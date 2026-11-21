package uds.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import uds.interpreter.UdsInterpreter;
import uds.interpreter.DecodeKind;
import uds.interpreter.DecodeResult;
import uds.io.Frame;
import uds.io.LineError;
import uds.io.LogReadResult;
import uds.io.LogReader;

/** Interpreter tab — load log into table rows with filter predicates (FR-08, P1-4). */
public final class InterpreterController {

  /**
   * Interpreter log level / view filter.
   *
   * <ul>
   *   <li>{@link #NORMAL} — hide TesterPresent keep-alive ({@code 0x3E}/{@code 0x7E})
   *   <li>{@link #VERBOSE} — every decoded frame
   *   <li>{@link #ERRORS_ONLY} — NRC / LineError only
   * </ul>
   */
  public enum FilterMode {
    NORMAL,
    VERBOSE,
    ERRORS_ONLY
  }

  private final UdsInterpreter interpreter;
  private final List<DecodeResult> decodeResults = new ArrayList<>();
  private final List<LineError> lineErrors = new ArrayList<>();
  private final ObservableList<InterpreterRow> allRows = FXCollections.observableArrayList();
  private final FilteredList<InterpreterRow> filteredRows = new FilteredList<>(allRows, r -> true);
  private FilterMode filterMode = FilterMode.NORMAL;
  private final IntegerProperty okFrameCount = new SimpleIntegerProperty(0);
  private final IntegerProperty errorLineCount = new SimpleIntegerProperty(0);
  private final IntegerProperty redactedCount = new SimpleIntegerProperty(0);
  private final StringProperty summaryChip = new SimpleStringProperty("");

  public InterpreterController(UdsInterpreter interpreter) {
    this.interpreter = Objects.requireNonNull(interpreter, "interpreter");
    filteredRows.setPredicate(predicateFor(filterMode));
  }

  public List<DecodeResult> decodeResults() {
    return List.copyOf(decodeResults);
  }

  public List<LineError> lineErrors() {
    return List.copyOf(lineErrors);
  }

  public ObservableList<InterpreterRow> allRows() {
    return allRows;
  }

  public FilteredList<InterpreterRow> filteredRows() {
    return filteredRows;
  }

  public FilterMode filterMode() {
    return filterMode;
  }

  public void setFilterMode(FilterMode mode) {
    this.filterMode = mode == null ? FilterMode.NORMAL : mode;
    filteredRows.setPredicate(predicateFor(this.filterMode));
  }

  public IntegerProperty okFrameCountProperty() {
    return okFrameCount;
  }

  public IntegerProperty errorLineCountProperty() {
    return errorLineCount;
  }

  public IntegerProperty redactedCountProperty() {
    return redactedCount;
  }

  public StringProperty summaryChipProperty() {
    return summaryChip;
  }

  public String summaryChip() {
    return summaryChip.get();
  }

  /** Display lines under current filter (headless / existing tests). */
  public List<String> filteredDisplayLines() {
    List<String> lines = new ArrayList<>();
    Predicate<InterpreterRow> pred = predicateFor(filterMode);
    for (InterpreterRow row : allRows) {
      if (pred.test(row)) {
        lines.add(row.displayLine());
      }
    }
    return lines;
  }

  /**
   * Reload this analysis session's live CSV log ({@code live_session.log}). Missing/empty file →
   * empty table with a friendly summary.
   */
  public void loadLiveSession(Path liveLogPath) {
    Objects.requireNonNull(liveLogPath, "liveLogPath");
    if (!Files.isRegularFile(liveLogPath)) {
      decodeResults.clear();
      lineErrors.clear();
      allRows.clear();
      okFrameCount.set(0);
      errorLineCount.set(0);
      redactedCount.set(0);
      summaryChip.set("이번 세션 실거래 로그 없음 — Session/Read/Write 실행 후 여기에 표시됩니다");
      return;
    }
    loadLog(liveLogPath);
    if (decodeResults.isEmpty() && lineErrors.isEmpty()) {
      summaryChip.set(
          "이번 세션 실거래: 0프레임 — Access/Read/Write를 실행하면 요청·응답이 쌓입니다");
    } else {
      summaryChip.set("이번 세션 | " + summaryChip.get());
    }
  }

  public void loadLog(Path path) {
    decodeResults.clear();
    lineErrors.clear();
    allRows.clear();
    try {
      LogReadResult read = LogReader.read(path);
      lineErrors.addAll(read.errors());
      List<InterpreterRow> built = new ArrayList<>();
      int redacted = 0;
      for (Frame frame : read.frames()) {
        DecodeResult parsed = interpreter.parse(frame);
        decodeResults.add(parsed);
        InterpreterRow row = InterpreterRow.ofDecode(parsed, frame.hexRaw());
        built.add(row);
        if (row.isRedacted()) {
          redacted++;
        }
      }
      // Newest frames first (file order is chronological ascending).
      Collections.reverse(built);
      for (LineError err : lineErrors) {
        built.add(InterpreterRow.ofError(err));
      }
      allRows.setAll(built);
      okFrameCount.set(decodeResults.size());
      int negatives = 0;
      for (DecodeResult parsed : decodeResults) {
        if (parsed.kind() == DecodeKind.NEGATIVE) {
          negatives++;
        }
      }
      errorLineCount.set(lineErrors.size() + negatives);
      redactedCount.set(redacted);
      summaryChip.set(
          "정상 프레임: "
              + decodeResults.size()
              + "개 | 오류(NRC/줄): "
              + (lineErrors.size() + negatives)
              + "개 | 0x27 REDACTED: "
              + redacted
              + "개");
      filteredRows.setPredicate(predicateFor(filterMode));
    } catch (Exception e) {
      throw new IllegalStateException("log load failed: " + path, e);
    }
  }

  /**
   * Export decoded frames as JSON (TEST mode log testing). Seed/key raw bytes stay redacted via
   * {@link DecodeResult#toJsonMap()}.
   */
  public Path exportDecodedJson(Path target) {
    Objects.requireNonNull(target, "target");
    try {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("okFrameCount", decodeResults.size());
      root.put("errorLineCount", lineErrors.size());
      List<Map<String, Object>> frames = new ArrayList<>();
      for (DecodeResult r : decodeResults) {
        frames.add(r.toJsonMap());
      }
      root.put("frames", frames);
      List<Map<String, Object>> errors = new ArrayList<>();
      for (LineError err : lineErrors) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("lineNo", err.lineNo());
        e.put("reason", err.reason());
        e.put("raw", err.raw());
        errors.add(e);
      }
      root.put("errors", errors);
      ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
      mapper.writeValue(target.toFile(), root);
      return target;
    } catch (Exception e) {
      throw new IllegalStateException("JSON export failed: " + target, e);
    }
  }

  static Predicate<InterpreterRow> predicateFor(FilterMode mode) {
    return switch (mode == null ? FilterMode.NORMAL : mode) {
      case NORMAL -> r -> !r.isTesterPresent();
      case VERBOSE -> r -> true;
      case ERRORS_ONLY -> InterpreterRow::isProblem;
    };
  }
}
