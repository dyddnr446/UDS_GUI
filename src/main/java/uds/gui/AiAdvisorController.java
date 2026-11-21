package uds.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uds.ai.AiAdviceResult;
import uds.ai.AiAdvisor;
import uds.ai.AiChatCompleter;
import uds.ai.AiError;
import uds.ai.AiPromptConfig;
import uds.ai.AiProvider;
import uds.ai.DidSelectService;
import uds.ai.DidSelectService.SelectionResult;
import uds.ai.SymptomDidSuggestionService;
import uds.ai.SymptomDidSuggestionService.CandidateDid;
import uds.ai.SymptomDidSuggestionService.SymptomSuggestion;
import uds.analysis.AnalysisSessionStore;
import uds.catalog.load.UdsCatalog;
import uds.gui.AnalysisSessionViewModel;
import uds.gui.app.UiTaskRunner;
import uds.gui.ReadDataViewModel;
import uds.read.Finding;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;

/**
 * AI Advisor tab controller — symptom → DID suggestion → optional ReadData → async AI with fact/AI
 * zone separation (FR-08/12/17, NFR-14/17). UDS-UX-03/04 extensions.
 */
public final class AiAdvisorController {

  private final UdsCatalog catalog;
  private final AnalysisSessionViewModel analysis;
  private final ReadDataViewModel read;
  private final SymptomDidSuggestionService suggestionService;
  private final AiAdvisor advisor;
  private final DidSelectService didSelect;
  private final UiTaskRunner runner;

  private String profileId = "";
  private String symptomText = "";
  private SymptomSuggestion suggestion =
      new SymptomSuggestion(null, List.of(), List.of(), List.of());
  private String factZone = "";
  private String aiZone = "";
  private List<String> displayedEvidenceIds = List.of();
  private List<String> lastSelectedDids = List.of();
  private final BooleanProperty running = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("");
  private final StringProperty errorBanner = new SimpleStringProperty("");
  private final StringProperty highlightedEvidenceId = new SimpleStringProperty("");
  private AiError lastError;
  private String accessDeniedNote = "";

  public AiAdvisorController(
      UdsCatalog catalog,
      AnalysisSessionViewModel analysis,
      ReadDataViewModel read,
      AiProvider provider,
      UiTaskRunner runner) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.analysis = Objects.requireNonNull(analysis, "analysis");
    this.read = Objects.requireNonNull(read, "read");
    this.suggestionService = new SymptomDidSuggestionService();
    this.advisor = new AiAdvisor(Objects.requireNonNull(provider, "provider"));
    this.runner = Objects.requireNonNull(runner, "runner");
    AiPromptConfig prompts =
        AiPromptConfig.loadNear(java.nio.file.Path.of(System.getProperty("user.dir", ".")));
    AiChatCompleter completer =
        provider instanceof AiChatCompleter chat
            ? chat
            : (sys, user) ->
                new AiChatCompleter.CompletionResult.Err(
                    new AiError(AiError.KEY_MISSING, "DID select completer unavailable"));
    this.didSelect = new DidSelectService(completer, prompts);
  }

  public BooleanProperty runningProperty() {
    return running;
  }

  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  public StringProperty errorBannerProperty() {
    return errorBanner;
  }

  public StringProperty highlightedEvidenceIdProperty() {
    return highlightedEvidenceId;
  }

  public void onProfileChanged(String profileId) {
    this.profileId = profileId == null ? "" : profileId;
    analysis.bindProfile(this.profileId);
    refreshSuggestions();
  }

  public void setSymptomText(String symptomText) {
    this.symptomText = symptomText == null ? "" : symptomText;
  }

  public String symptomText() {
    return symptomText;
  }

  public void refreshSuggestions() {
    suggestion = suggestionService.suggest(catalog, profileId, symptomText);
    if (suggestion.guidance() != null) {
      setStatus(suggestion.guidance());
    } else {
      setStatus(
          "candidates="
              + suggestion.candidates().size()
              + " runtime="
              + suggestion.runtimeExecutableDids()
              + " staticRef="
              + suggestion.staticLogReferenceDids());
    }
    rebuildFactZone();
  }

  /**
   * Runs ReadData for runtime-executable suggested DIDs (current Read scenario) and appends facts to
   * the Analysis Session store.
   */
  public List<ReadDataResult> runSuggestedReads() {
    return runSuggestedReads(suggestion.runtimeExecutableDids());
  }

  /** Runs ReadData only for the given DID list (already filtered to runtime-executable). */
  public List<ReadDataResult> runSuggestedReads(List<String> dids) {
    List<ReadDataResult> out = new ArrayList<>();
    accessDeniedNote = "";
    List<String> targets = dids == null ? List.of() : dids;
    if (targets.isEmpty()) {
      setStatus("실행 가능 DID 없음 (runtime_enabled 필터)");
      rebuildFactZone();
      return out;
    }
    int denied = 0;
    int failed = 0;
    for (String did : targets) {
      // DID별로 기본 fixture를 다시 resolve (이전 Read 시나리오 재사용 금지).
      read.selectScenario(null);
      read.selectDid(did);
      ReadDataResult result = read.readSelected();
      analysis.store().appendRead(result);
      out.add(result);
      if (result.status() == ReadStatus.ACCESS_DENIED) {
        denied++;
      } else if (result.status() != ReadStatus.NORMAL && result.status() != ReadStatus.ABNORMAL) {
        failed++;
      }
    }
    if (!out.isEmpty() && denied == out.size()) {
      accessDeniedNote = "모든 runtime DID Read가 ACCESS_DENIED — Extended/Unlock 필요";
    } else if (failed > 0) {
      accessDeniedNote =
          "일부 DID Read 실패 (" + failed + "건) — fixture/응답 불일치 또는 NRC. Analysis/JSONL 확인";
    }
    rebuildFactZone();
    setStatus("ReadData appended: " + out.size() + (failed > 0 ? " (fail=" + failed + ")" : ""));
    return out;
  }

  /**
   * UDS-UX-04: Suggest → AI DID select → selected Read → AI advice.
   *
   * <p>DID selection runs in background; Read + advice continue on the UI callback thread.
   * Empty/failed selection degrades to all runtime-executable DIDs.
   */
  public void runOneClickDiagnosis() {
    if (running.get()) {
      setStatus("AI already running");
      return;
    }
    refreshSuggestions();
    List<String> runtime = List.copyOf(suggestion.runtimeExecutableDids());
    List<CandidateDid> candidates = List.copyOf(suggestion.candidates());
    String symptom = symptomText;
    String profile = profileId;

    running.set(true);
    setStatus("AI selecting DIDs…");
    errorBanner.set("");
    aiZone = "";
    lastError = null;

    runner.runAsync(
        () -> didSelect.select(symptom, profile, candidates, runtime),
        selection -> onDidSelectOk(selection, symptom, profile),
        this::onAdviceError,
        this::onAdviceCancelled);
  }

  public List<String> lastSelectedDids() {
    return lastSelectedDids;
  }

  private void onDidSelectOk(SelectionResult selection, String symptom, String profile) {
    lastSelectedDids = List.copyOf(selection.selectedDids());
    String selectNote =
        selection.degraded()
            ? (selection.degradeMessage() == null ? "select degraded" : selection.degradeMessage())
            : ("AI selected DIDs: " + selection.selectedDids());
    setStatus(selectNote);
    List<ReadDataResult> reads = runSuggestedReads(selection.selectedDids());
    setStatus(selectNote + " · reads=" + reads.size() + " · AI advising…");
    AnalysisSessionStore store = analysis.store();
    runner.runAsync(
        () -> advisor.advise(symptom, profile, store),
        this::onAdviceOk,
        this::onAdviceError,
        this::onAdviceCancelled);
  }

  /** Starts AI advice asynchronously; returns immediately (NFR-17). */
  public void requestAdvice() {
    if (running.get()) {
      setStatus("AI already running");
      return;
    }
    rebuildFactZone();
    AnalysisSessionStore store = analysis.store();
    String symptom = symptomText;
    String profile = profileId;
    running.set(true);
    setStatus("AI running…");
    errorBanner.set("");
    aiZone = "";
    lastError = null;

    runner.runAsync(
        () -> advisor.advise(symptom, profile, store),
        this::onAdviceOk,
        this::onAdviceError,
        this::onAdviceCancelled);
  }

  public void cancel() {
    runner.cancelCurrent();
    running.set(false);
    lastError = new AiError(AiError.CANCELLED, "AI request cancelled");
    errorBanner.set("AiError CANCELLED: AI request cancelled");
    setStatus("AI cancelled");
    // Deterministic facts stay in store (NFR-12/DOD-07).
    rebuildFactZone();
  }

  public void selectEvidence(String evidenceId) {
    highlightedEvidenceId.set(evidenceId == null ? "" : evidenceId);
  }

  public boolean isRunning() {
    return running.get() || runner.isRunning();
  }

  public String factZone() {
    return factZone;
  }

  public String aiZone() {
    return aiZone;
  }

  public List<String> displayedEvidenceIds() {
    return displayedEvidenceIds;
  }

  public String statusMessage() {
    return statusMessage.get();
  }

  public String errorBanner() {
    return errorBanner.get();
  }

  public String highlightedEvidenceId() {
    return highlightedEvidenceId.get();
  }

  public AiError lastError() {
    return lastError;
  }

  public List<CandidateDid> candidates() {
    return suggestion.candidates();
  }

  public List<String> runtimeExecutableDids() {
    return suggestion.runtimeExecutableDids();
  }

  public List<String> staticLogReferenceDids() {
    return suggestion.staticLogReferenceDids();
  }

  /** Fact-zone rows for ListView — human labels; full evidenceId kept via {@link #shortEvidenceLabel}. */
  public List<String> factEvidenceLines() {
    List<String> lines = new ArrayList<>();
    for (AnalysisSessionStore.FindingEntry e : analysis.store().findings()) {
      Finding f = e.finding();
      lines.add(
          shortEvidenceLabel(e.evidenceId())
              + "  ·  "
              + f.did()
              + "  "
              + nullToDash(f.displayValue())
              + "  ·  "
              + f.status());
    }
    for (AnalysisSessionStore.WriteEntry e : analysis.store().writeResults()) {
      lines.add(
          shortEvidenceLabel(e.evidenceId())
              + "  ·  "
              + e.result().did()
              + "  ·  "
              + e.result().status());
    }
    for (var e : analysis.store().accessEvidences()) {
      lines.add(
          shortEvidenceLabel(e.evidenceId())
              + "  ·  "
              + e.operation()
              + "  ·  "
              + e.resultingState());
    }
    return lines;
  }

  /** Chip / list label for an evidenceId (`FINDING:6-uuid` → `Finding #6`). */
  public static String shortEvidenceLabel(String evidenceId) {
    if (evidenceId == null || evidenceId.isBlank()) {
      return "";
    }
    int colon = evidenceId.indexOf(':');
    if (colon < 0) {
      return evidenceId;
    }
    String type = evidenceId.substring(0, colon).trim();
    String rest = evidenceId.substring(colon + 1).trim();
    String num = rest;
    int dash = rest.indexOf('-');
    if (dash > 0) {
      num = rest.substring(0, dash);
    }
    String pretty =
        switch (type.toUpperCase(Locale.ROOT)) {
          case "FINDING" -> "Finding";
          case "READ" -> "Read";
          case "WRITE" -> "Write";
          case "ACCESS" -> "Access";
          case "LOG" -> "Log";
          default -> type;
        };
    return pretty + " #" + num;
  }

  private static String nullToDash(String s) {
    return s == null || s.isBlank() ? "—" : s;
  }

  public List<Finding> findings() {
    return analysis.store().findings().stream().map(AnalysisSessionStore.FindingEntry::finding).toList();
  }

  private void onAdviceOk(AiAdvisor.AdviseResult result) {
    running.set(false);
    if (result instanceof AiAdvisor.AdviseResult.Ok ok) {
      AiAdviceResult advice = ok.advice();
      analysis.store().setAiAdvice(advice);
      displayedEvidenceIds = preferFindingEvidence(advice.evidenceIds());
      aiZone = formatAiZone(advice);
      setStatus("AI complete");
      lastError = null;
      errorBanner.set("");
    } else if (result instanceof AiAdvisor.AdviseResult.Err err) {
      lastError = err.error();
      aiZone =
          "■ 요약\nAI 응답을 가져오지 못했습니다.\n· "
              + err.error().code()
              + " — "
              + err.error().message()
              + "\n";
      setStatus("AI error: " + err.error().code());
      errorBanner.set("AiError " + err.error().code() + ": " + err.error().message());
      displayedEvidenceIds = List.of();
    }
    rebuildFactZone();
  }

  private void onAdviceError(Throwable error) {
    running.set(false);
    if (error instanceof TimeoutException) {
      lastError = new AiError(AiError.TIMEOUT, error.getMessage() == null ? "timeout" : error.getMessage());
    } else {
      lastError =
          new AiError(
              AiError.NETWORK, error.getMessage() == null ? error.toString() : error.getMessage());
    }
    aiZone =
        "■ 요약\nAI 응답을 가져오지 못했습니다.\n· "
            + lastError.code()
            + " — "
            + lastError.message()
            + "\n";
    setStatus("AI error: " + lastError.code());
    errorBanner.set("AiError " + lastError.code() + ": " + lastError.message());
    rebuildFactZone();
  }

  private void onAdviceCancelled() {
    running.set(false);
    if (lastError == null || !AiError.CANCELLED.equals(lastError.code())) {
      lastError = new AiError(AiError.CANCELLED, "AI request cancelled");
      errorBanner.set("AiError CANCELLED: AI request cancelled");
    }
    setStatus("AI cancelled");
    rebuildFactZone();
  }

  private void setStatus(String msg) {
    statusMessage.set(msg == null ? "" : msg);
  }

  private void rebuildFactZone() {
    AnalysisSessionStore store = analysis.store();
    StringBuilder sb = new StringBuilder();
    sb.append("■ 사실 구역\n");
    sb.append("프로필: ").append(profileId.isBlank() ? "(없음)" : profileId).append('\n');
    sb.append("증상: ").append(symptomText.isBlank() ? "(미입력)" : symptomText).append('\n');
    if (!suggestion.candidates().isEmpty()) {
      sb.append("\nDID 후보\n");
      for (CandidateDid c : suggestion.candidates()) {
        boolean runtime = suggestion.runtimeExecutableDids().contains(c.didId());
        boolean staticRef = suggestion.staticLogReferenceDids().contains(c.didId());
        String kind = runtime ? "실행" : (staticRef ? "참고" : "기타");
        sb.append("· [")
            .append(kind)
            .append("] ")
            .append(c.didId());
        if (c.reasonKo() != null && !c.reasonKo().isBlank()) {
          sb.append(" — ").append(c.reasonKo());
        }
        sb.append('\n');
      }
    }
    if (!accessDeniedNote.isBlank()) {
      sb.append('\n').append(accessDeniedNote).append('\n');
    }
    if (!store.findings().isEmpty() || !store.writeResults().isEmpty() || !store.accessEvidences().isEmpty()) {
      sb.append("\n측정/이벤트\n");
    }
    for (AnalysisSessionStore.FindingEntry e : store.findings()) {
      Finding f = e.finding();
      sb.append("· ")
          .append(shortEvidenceLabel(e.evidenceId()))
          .append("  ")
          .append(f.did())
          .append("  ")
          .append(nullToDash(f.displayValue()))
          .append("  ")
          .append(f.status())
          .append('\n');
    }
    for (AnalysisSessionStore.WriteEntry e : store.writeResults()) {
      sb.append("· ")
          .append(shortEvidenceLabel(e.evidenceId()))
          .append("  ")
          .append(e.result().did())
          .append("  ")
          .append(e.result().status())
          .append('\n');
    }
    for (var e : store.accessEvidences()) {
      sb.append("· ")
          .append(shortEvidenceLabel(e.evidenceId()))
          .append("  ")
          .append(e.operation())
          .append("  ")
          .append(e.resultingState())
          .append('\n');
    }
    factZone = sb.toString();
  }

  private static String formatAiZone(AiAdviceResult advice) {
    StringBuilder sb = new StringBuilder();
    sb.append("■ 요약\n");
    sb.append(advice.summary() == null ? "" : advice.summary().trim());
    sb.append("\n");
    if (!advice.checklist().isEmpty()) {
      sb.append("\n■ 점검 항목\n");
      int n = 1;
      for (AiAdviceResult.ChecklistItem item : advice.checklist()) {
        sb.append(n++).append(". ").append(item.text() == null ? "" : item.text().trim());
        sb.append('\n');
        List<String> refs = shortenEvidenceList(item.evidenceIds());
        if (!refs.isEmpty()) {
          sb.append("   근거: ").append(String.join(", ", refs)).append('\n');
        }
      }
    }
    if (!advice.warnings().isEmpty()) {
      sb.append("\n■ 참고\n");
      for (String w : advice.warnings()) {
        if (w != null && !w.isBlank()) {
          sb.append("· ").append(w.trim()).append('\n');
        }
      }
    }
    return sb.toString().trim() + "\n";
  }

  /** Chips: FINDING 우선, 최대 8개. */
  private static List<String> preferFindingEvidence(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> findings = new LinkedHashSet<>();
    LinkedHashSet<String> others = new LinkedHashSet<>();
    for (String id : ids) {
      if (id == null || id.isBlank()) {
        continue;
      }
      if (id.toUpperCase(Locale.ROOT).startsWith("FINDING:")) {
        findings.add(id);
      } else {
        others.add(id);
      }
    }
    List<String> out = new ArrayList<>(findings);
    for (String id : others) {
      if (out.size() >= 8) {
        break;
      }
      out.add(id);
    }
    if (out.size() > 8) {
      return List.copyOf(out.subList(0, 8));
    }
    return List.copyOf(out);
  }

  private static List<String> shortenEvidenceList(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    // Finding 우선 → 그다음 기타 (가독성)
    for (String id : ids) {
      if (id != null && id.toUpperCase(Locale.ROOT).startsWith("FINDING:")) {
        out.add(shortEvidenceLabel(id));
      }
      if (out.size() >= 2) {
        break;
      }
    }
    if (out.isEmpty()) {
      for (String id : ids) {
        String label = shortEvidenceLabel(id);
        if (!label.isBlank()) {
          out.add(label);
        }
        if (out.size() >= 2) {
          break;
        }
      }
    }
    return List.copyOf(out);
  }
}
