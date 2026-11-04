package uds.gui.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uds.ai.advisor.AiAdviceResult;
import uds.ai.advisor.AiAdvisor;
import uds.ai.advisor.AiError;
import uds.ai.advisor.SymptomDidSuggestionService;
import uds.ai.advisor.SymptomDidSuggestionService.CandidateDid;
import uds.ai.advisor.SymptomDidSuggestionService.SymptomSuggestion;
import uds.ai.provider.AiProvider;
import uds.analysis.session.AnalysisSessionStore;
import uds.catalog.load.UdsCatalog;
import uds.gui.analysis.AnalysisSessionViewModel;
import uds.gui.app.UiTaskRunner;
import uds.gui.read.ReadDataViewModel;
import uds.read.finding.Finding;
import uds.read.finding.ReadDataResult;
import uds.read.finding.ReadStatus;

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
  private final UiTaskRunner runner;

  private String profileId = "";
  private String symptomText = "";
  private SymptomSuggestion suggestion =
      new SymptomSuggestion(null, List.of(), List.of(), List.of());
  private String factZone = "";
  private String aiZone = "";
  private List<String> displayedEvidenceIds = List.of();
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
    List<ReadDataResult> out = new ArrayList<>();
    accessDeniedNote = "";
    if (suggestion.runtimeExecutableDids().isEmpty()) {
      setStatus("실행 가능 DID 없음 (runtime_enabled 필터)");
      rebuildFactZone();
      return out;
    }
    String scenario = read.selectedScenarioId();
    int denied = 0;
    for (String did : suggestion.runtimeExecutableDids()) {
      ReadDataResult result;
      if (scenario != null && !scenario.isBlank()) {
        result = read.read(scenario, did);
      } else {
        read.selectDid(did);
        result = read.readSelected();
      }
      analysis.store().appendRead(result);
      out.add(result);
      if (result.status() == ReadStatus.ACCESS_DENIED) {
        denied++;
      }
    }
    if (!out.isEmpty() && denied == out.size()) {
      accessDeniedNote = "모든 runtime DID Read가 ACCESS_DENIED — Extended/Unlock 필요";
    }
    rebuildFactZone();
    setStatus("ReadData appended: " + out.size());
    return out;
  }

  /** UDS-UX-04: Suggest → runtime Read → AI advice in one action. */
  public void runOneClickDiagnosis() {
    refreshSuggestions();
    runSuggestedReads();
    requestAdvice();
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

  /** Fact-zone rows keyed by evidenceId for ListView binding (UDS-UX-04). */
  public List<String> factEvidenceLines() {
    List<String> lines = new ArrayList<>();
    for (AnalysisSessionStore.FindingEntry e : analysis.store().findings()) {
      Finding f = e.finding();
      lines.add(
          e.evidenceId()
              + " Finding "
              + f.did()
              + ' '
              + f.displayValue()
              + ' '
              + f.status());
    }
    for (AnalysisSessionStore.WriteEntry e : analysis.store().writeResults()) {
      lines.add(
          e.evidenceId()
              + " Write "
              + e.result().did()
              + ' '
              + e.result().status());
    }
    for (var e : analysis.store().accessEvidences()) {
      lines.add(
          e.evidenceId()
              + " Access "
              + e.operation()
              + ' '
              + e.resultingState());
    }
    return lines;
  }

  public List<Finding> findings() {
    return analysis.store().findings().stream().map(AnalysisSessionStore.FindingEntry::finding).toList();
  }

  private void onAdviceOk(AiAdvisor.AdviseResult result) {
    running.set(false);
    if (result instanceof AiAdvisor.AdviseResult.Ok ok) {
      AiAdviceResult advice = ok.advice();
      analysis.store().setAiAdvice(advice);
      displayedEvidenceIds = advice.evidenceIds();
      aiZone = formatAiZone(advice);
      setStatus("AI complete");
      lastError = null;
      errorBanner.set("");
    } else if (result instanceof AiAdvisor.AdviseResult.Err err) {
      lastError = err.error();
      aiZone = "[AI 설명]\n(unavailable: " + err.error().code() + ") " + err.error().message();
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
    aiZone = "[AI 설명]\n(unavailable: " + lastError.code() + ") " + lastError.message();
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
    sb.append("사실 구역\n");
    sb.append("profile=").append(profileId).append('\n');
    sb.append("symptom=").append(symptomText).append('\n');
    if (!suggestion.candidates().isEmpty()) {
      sb.append("DID 후보:\n");
      for (CandidateDid c : suggestion.candidates()) {
        boolean runtime = suggestion.runtimeExecutableDids().contains(c.didId());
        boolean staticRef = suggestion.staticLogReferenceDids().contains(c.didId());
        String kind = runtime ? "[runtime]" : (staticRef ? "[static-ref]" : "[other]");
        sb.append("  ")
            .append(kind)
            .append(' ')
            .append(c.didId())
            .append(" p=")
            .append(c.priority())
            .append(' ')
            .append(c.reasonKo())
            .append('\n');
      }
    }
    if (!suggestion.runtimeExecutableDids().isEmpty()) {
      sb.append("실행 DID (runtime): ").append(suggestion.runtimeExecutableDids()).append('\n');
    }
    if (!suggestion.staticLogReferenceDids().isEmpty()) {
      sb.append("정적 로그 참고 (static): ").append(suggestion.staticLogReferenceDids()).append('\n');
    }
    if (!accessDeniedNote.isBlank()) {
      sb.append(accessDeniedNote).append('\n');
    }
    for (AnalysisSessionStore.FindingEntry e : store.findings()) {
      Finding f = e.finding();
      sb.append(e.evidenceId())
          .append(" Finding ")
          .append(f.did())
          .append(' ')
          .append(f.displayValue())
          .append(' ')
          .append(f.status())
          .append('\n');
    }
    for (AnalysisSessionStore.WriteEntry e : store.writeResults()) {
      sb.append(e.evidenceId())
          .append(" Write ")
          .append(e.result().did())
          .append(' ')
          .append(e.result().status())
          .append('\n');
    }
    for (var e : store.accessEvidences()) {
      sb.append(e.evidenceId())
          .append(" Access ")
          .append(e.operation())
          .append(' ')
          .append(e.resultingState())
          .append('\n');
    }
    factZone = sb.toString();
  }

  private static String formatAiZone(AiAdviceResult advice) {
    StringBuilder sb = new StringBuilder();
    sb.append("[AI 설명]\n");
    sb.append(advice.summary()).append('\n');
    if (!advice.checklist().isEmpty()) {
      sb.append("checklist:\n");
      for (AiAdviceResult.ChecklistItem item : advice.checklist()) {
        StringJoiner ids = new StringJoiner(", ");
        item.evidenceIds().forEach(ids::add);
        sb.append("  - ").append(item.text()).append(" [").append(ids).append("]\n");
      }
    }
    if (!advice.evidenceIds().isEmpty()) {
      sb.append("evidenceIds=").append(advice.evidenceIds()).append('\n');
    }
    for (String w : advice.warnings()) {
      sb.append("warning: ").append(w).append('\n');
    }
    return sb.toString();
  }
}
