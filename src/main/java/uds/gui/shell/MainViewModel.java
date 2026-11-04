package uds.gui.shell;

import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uds.access.service.AccessOutcome;
import uds.access.state.DiagnosticAccessState;
import uds.ai.provider.AiProvider;
import uds.ai.provider.ExternalAiClient;
import uds.fixture.repo.FixtureRepository;
import uds.gui.access.AccessViewModel;
import uds.gui.ai.AiAdvisorController;
import uds.gui.analysis.AnalysisSessionViewModel;
import uds.gui.app.AppSession;
import uds.gui.app.UiTaskRunner;
import uds.gui.interpreter.InterpreterController;
import uds.gui.read.ReadDataViewModel;
import uds.gui.write.WriteDataViewModel;

/** Shell composition only — wires feature ViewModels (NFR-05). UDS-UX-02/05/06. */
public final class MainViewModel {

  public static final int TAB_ACCESS = 0;
  public static final int TAB_INTERPRETER = 1;
  public static final int TAB_READ = 2;
  public static final int TAB_WRITE = 3;
  public static final int TAB_ANALYSIS = 4;
  public static final int TAB_AI = 5;

  private final AppSession session;
  private final AccessViewModel access;
  private final ReadDataViewModel read;
  private final WriteDataViewModel write;
  private final InterpreterController interpreter;
  private final AnalysisSessionViewModel analysis;
  private final UiTaskRunner uiTasks;
  private final AiAdvisorController ai;

  private final StringProperty statusProfile = new SimpleStringProperty("Profile: (none)");
  private final StringProperty statusSession = new SimpleStringProperty("Session: DEFAULT");
  private final StringProperty statusSecurity = new SimpleStringProperty("Security: LOCKED");
  private final StringProperty statusRedaction =
      new SimpleStringProperty("0x27 Redaction: ON");
  private final IntegerProperty requestedTabIndex = new SimpleIntegerProperty(-1);

  private final BooleanProperty step1Done = new SimpleBooleanProperty(false);
  private final BooleanProperty step2Done = new SimpleBooleanProperty(false);
  private final BooleanProperty step3Done = new SimpleBooleanProperty(false);
  private final BooleanProperty step4Done = new SimpleBooleanProperty(false);
  private final BooleanProperty step5Done = new SimpleBooleanProperty(false);

  public MainViewModel(AppSession session, Path samplesDir) {
    this(session, samplesDir, new ExternalAiClient(), new UiTaskRunner());
  }

  public MainViewModel(AppSession session, Path samplesDir, AiProvider aiProvider, UiTaskRunner uiTasks) {
    this.session = Objects.requireNonNull(session, "session");
    this.uiTasks = Objects.requireNonNull(uiTasks, "uiTasks");
    FixtureRepository fixtures =
        new FixtureRepository(
            samplesDir.resolve("access_scenarios.csv"),
            samplesDir.resolve("read_data_scenarios.csv"),
            samplesDir.resolve("write_data_scenarios.csv"));
    this.access = new AccessViewModel(session.accessFacade(), session.catalog(), fixtures);
    this.read =
        new ReadDataViewModel(
            session.accessFacade(), session.readDataService(), session.catalog(), fixtures);
    this.write =
        new WriteDataViewModel(
            session.accessFacade(), session.writeDataService(), session.catalog(), fixtures);
    this.interpreter = new InterpreterController(session.interpreter());
    this.analysis = new AnalysisSessionViewModel(session);
    this.ai =
        new AiAdvisorController(
            session.catalog(), analysis, read, Objects.requireNonNull(aiProvider, "aiProvider"), uiTasks);
    refreshDiagnosticStatus();
  }

  public AppSession session() {
    return session;
  }

  public AccessViewModel access() {
    return access;
  }

  public ReadDataViewModel read() {
    return read;
  }

  public WriteDataViewModel write() {
    return write;
  }

  public InterpreterController interpreter() {
    return interpreter;
  }

  public AnalysisSessionViewModel analysis() {
    return analysis;
  }

  public UiTaskRunner uiTasks() {
    return uiTasks;
  }

  public AiAdvisorController ai() {
    return ai;
  }

  public StringProperty statusProfileProperty() {
    return statusProfile;
  }

  public StringProperty statusSessionProperty() {
    return statusSession;
  }

  public StringProperty statusSecurityProperty() {
    return statusSecurity;
  }

  public StringProperty statusRedactionProperty() {
    return statusRedaction;
  }

  public IntegerProperty requestedTabIndexProperty() {
    return requestedTabIndex;
  }

  public BooleanProperty step1DoneProperty() {
    return step1Done;
  }

  public BooleanProperty step2DoneProperty() {
    return step2Done;
  }

  public BooleanProperty step3DoneProperty() {
    return step3Done;
  }

  public BooleanProperty step4DoneProperty() {
    return step4Done;
  }

  public BooleanProperty step5DoneProperty() {
    return step5Done;
  }

  /** Read-only snapshot refresh from Access facade / context (no Context writes). */
  public void refreshDiagnosticStatus() {
    String profile = access.selectedProfileId();
    statusProfile.set("Profile: " + (profile == null || profile.isBlank() ? "(none)" : profile));
    DiagnosticAccessState state = access.accessState();
    switch (state) {
      case DEFAULT_LOCKED -> {
        statusSession.set("Session: DEFAULT");
        statusSecurity.set("Security: LOCKED");
      }
      case EXTENDED_LOCKED -> {
        statusSession.set("Session: EXTENDED");
        statusSecurity.set("Security: LOCKED");
      }
      case EXTENDED_UNLOCKED -> {
        statusSession.set("Session: EXTENDED");
        statusSecurity.set("Security: UNLOCKED");
      }
    }
    statusRedaction.set("0x27 Redaction: ON");
  }

  public void requestAccessTab() {
    requestedTabIndex.set(TAB_ACCESS);
  }

  public void goDemoStep(int step) {
    switch (step) {
      case 1 -> requestedTabIndex.set(TAB_INTERPRETER);
      case 2 -> requestedTabIndex.set(TAB_ACCESS);
      case 3 -> requestedTabIndex.set(TAB_AI);
      case 4 -> requestedTabIndex.set(TAB_WRITE);
      case 5 -> requestedTabIndex.set(TAB_ANALYSIS);
      default -> {
      }
    }
  }

  public void markStep1LogLoaded() {
    step1Done.set(true);
  }

  public void markStep2Unlocked() {
    step2Done.set(true);
  }

  public void markStep3AiOrFinding() {
    step3Done.set(true);
  }

  public void markStep4WriteDone() {
    step4Done.set(true);
  }

  public void markStep5SessionDone() {
    step5Done.set(true);
  }

  /**
   * Profile change: Access resets to DEFAULT_LOCKED; Read/Write Observable DID/scenario lists
   * update immediately (UDS-UX-01 — no manual Refresh).
   */
  public void selectProfile(String profileId) {
    access.selectProfile(profileId);
    read.onProfileChanged(profileId);
    write.onProfileChanged(profileId);
    analysis.bindProfile(profileId);
    ai.onProfileChanged(profileId);
    refreshDiagnosticStatus();
  }

  public AccessOutcome enterExtended() {
    AccessOutcome out = access.enterExtended();
    refreshDiagnosticStatus();
    return out;
  }

  public AccessOutcome unlock() {
    AccessOutcome out = access.unlock();
    refreshDiagnosticStatus();
    if (access.accessState() == DiagnosticAccessState.EXTENDED_UNLOCKED) {
      read.clearAccessDenied();
      write.clearAccessDenied();
      markStep2Unlocked();
    }
    return out;
  }

  /** P1-3: preset A — 급정지 전압저하 Read 시나리오 (단일 Port setFixtureRunConfig only). */
  public void applyDemoPresetA() {
    selectProfile("FLIPSKY_75100_VESC");
    session.accessFacade().setFixtureRunConfig("RD-VESC-001");
    read.selectScenario("RD-VESC-001");
    read.selectDid("0x1001");
    ai.setSymptomText("급정지");
    ai.refreshSuggestions();
    refreshDiagnosticStatus();
  }

  /** P1-3: preset B — locked deny 시연용 보호 DID 선택 (권한 올리치 않음). */
  public void applyDemoPresetB() {
    selectProfile("FLIPSKY_75100_VESC");
    if (!read.runtimeDidIds().isEmpty()) {
      read.selectDid(read.runtimeDidIds().get(0));
    }
    refreshDiagnosticStatus();
  }

  /** P1-3: preset C — Write 정상 시나리오 세팅. */
  public void applyDemoPresetC() {
    selectProfile("FLIPSKY_75100_VESC");
    session.accessFacade().setFixtureRunConfig("WD-VESC-001");
    write.selectScenario("WD-VESC-001");
    write.selectDid("0x2002");
    write.setInputValue("12000");
    refreshDiagnosticStatus();
  }
}
