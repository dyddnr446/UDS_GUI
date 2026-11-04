package uds.gui.interpreter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uds.interpreter.decode.SidDecoder;
import uds.interpreter.decode.UdsInterpreter;
import uds.interpreter.model.DecodeResult;
import uds.io.Frame;
import uds.io.LineError;
import uds.io.LogReadResult;
import uds.io.LogReader;

/** Interpreter tab — load log and show DecodeResult / LineError (FR-08). P1-4 filters. */
public final class InterpreterController {

  public enum FilterMode {
    ALL,
    ERRORS_ONLY,
    REDACTED_ONLY
  }

  private final UdsInterpreter interpreter;
  private final List<DecodeResult> decodeResults = new ArrayList<>();
  private final List<LineError> lineErrors = new ArrayList<>();
  private FilterMode filterMode = FilterMode.ALL;
  private final IntegerProperty okFrameCount = new SimpleIntegerProperty(0);
  private final IntegerProperty errorLineCount = new SimpleIntegerProperty(0);
  private final IntegerProperty redactedCount = new SimpleIntegerProperty(0);
  private final StringProperty summaryChip = new SimpleStringProperty("");

  public InterpreterController(UdsInterpreter interpreter) {
    this.interpreter = Objects.requireNonNull(interpreter, "interpreter");
  }

  public List<DecodeResult> decodeResults() {
    return List.copyOf(decodeResults);
  }

  public List<LineError> lineErrors() {
    return List.copyOf(lineErrors);
  }

  public FilterMode filterMode() {
    return filterMode;
  }

  public void setFilterMode(FilterMode mode) {
    this.filterMode = mode == null ? FilterMode.ALL : mode;
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

  /** Display rows under current filter (testable without JavaFX Toolkit). */
  public List<String> filteredDisplayLines() {
    List<String> lines = new ArrayList<>();
    switch (filterMode) {
      case ERRORS_ONLY -> {
        for (LineError err : lineErrors) {
          lines.add("ERR " + err);
        }
      }
      case REDACTED_ONLY -> {
        for (DecodeResult r : decodeResults) {
          if (isRedacted(r)) {
            lines.add(String.valueOf(r));
          }
        }
      }
      case ALL -> {
        for (DecodeResult r : decodeResults) {
          lines.add(String.valueOf(r));
        }
        for (LineError err : lineErrors) {
          lines.add("ERR " + err);
        }
      }
    }
    return lines;
  }

  public void loadLog(Path path) {
    decodeResults.clear();
    lineErrors.clear();
    try {
      LogReadResult read = LogReader.read(path);
      lineErrors.addAll(read.errors());
      for (Frame frame : read.frames()) {
        decodeResults.add(interpreter.parse(frame));
      }
      int redacted = 0;
      for (DecodeResult r : decodeResults) {
        if (isRedacted(r)) {
          redacted++;
        }
      }
      okFrameCount.set(decodeResults.size());
      errorLineCount.set(lineErrors.size());
      redactedCount.set(redacted);
      summaryChip.set(
          "정상 프레임: "
              + decodeResults.size()
              + "개 | 격리된 오류 줄: "
              + lineErrors.size()
              + "개 | 0x27 REDACTED: "
              + redacted
              + "개");
    } catch (Exception e) {
      throw new IllegalStateException("log load failed: " + path, e);
    }
  }

  private static boolean isRedacted(DecodeResult r) {
    String s = String.valueOf(r).toUpperCase(Locale.ROOT);
    return s.contains(SidDecoder.REDACTED.toUpperCase(Locale.ROOT)) || s.contains("REDACTED");
  }
}
