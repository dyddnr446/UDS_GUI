package uds.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uds.access.AccessOutcome;
import uds.access.DiagnosticAccessState;
import uds.ai.AiProvider;
import uds.ai.ExternalAiClient;
import uds.analysis.AccessEvidence;
import uds.fixture.FixtureRepository;
import uds.gui.AccessViewModel;
import uds.gui.DiagnosticSessionChoice;
import uds.gui.AiAdvisorController;
import uds.gui.AnalysisSessionViewModel;
import uds.gui.app.AppSession;
import uds.gui.app.UiTaskRunner;
import uds.gui.InterpreterController;
import uds.gui.ReadDataViewModel;
import uds.gui.WriteDataViewModel;
import uds.read.ReadDataResult;
import uds.write.WriteResult;

/** Shell composition — wires feature ViewModels; UI progressive disclosure (NFR-05). */
public final class MainViewModel {

  /** Tab order after Access absorbed into Control Bar. */
  public static final int TAB_INTERPRETER = 0;

  public static final int TAB_READ = 1;
  public static final int TAB_WRITE = 2;
  public static final int TAB_DTC = 3;
  public static final int TAB_AI = 4;
  public static final int TAB_ANALYSIS = 5;

  /** Backend connection id for TEST=TRUE offline/log mode (UI: Offline Simulation). */
  public static final String INTERFACE_VIRTUAL = "VIRTUAL";

  /** Backend connection id for TEST=FALSE live path (UI: CAN Bus). */
  public static final String INTERFACE_CAN = "CAN";

  private final AppSession session;
  private final boolean testMode;
  private final AccessViewModel access;
  private final ReadDataViewModel read;
  private final WriteDataViewModel write;
  private final DtcViewModel dtc;
  private final InterpreterController interpreter;
  private final AnalysisSessionViewModel analysis;
  private final UiTaskRunner uiTasks;
  private final AiAdvisorController ai;

  private final StringProperty statusInterface =
      new SimpleStringProperty("Interface: VIRTUAL");
  private final StringProperty statusProfile = new SimpleStringProperty("Profile: (none)");
  private final StringProperty statusSession = new SimpleStringProperty("Session: DEFAULT");
  private final StringProperty statusSecurity = new SimpleStringProperty("Security: LOCKED");
  private final StringProperty statusRedaction =
      new SimpleStringProperty("0x27 Redaction: ON (Learning OFF)");
  private final IntegerProperty requestedTabIndex = new SimpleIntegerProperty(-1);

  private final BooleanProperty readTabEnabled = new SimpleBooleanProperty(false);
  private final BooleanProperty writeTabEnabled = new SimpleBooleanProperty(false);
  private final BooleanProperty dtcTabEnabled = new SimpleBooleanProperty(false);
  private final BooleanProperty aiTabEnabled = new SimpleBooleanProperty(false);
  private final StringProperty transportMessage = new SimpleStringProperty("");
  /** In-memory Seed/Key password reused for later Extended Apply (never logged). */
  private String rememberedUnlockPassword = "";

  public MainViewModel(AppSession session, Path samplesDir) {
    this(session, samplesDir, new ExternalAiClient(), new UiTaskRunner(), true);
  }

  public MainViewModel(
      AppSession session, Path samplesDir, AiProvider aiProvider, UiTaskRunner uiTasks) {
    this(session, samplesDir, aiProvider, uiTasks, true);
  }

  public MainViewModel(
      AppSession session,
      Path samplesDir,
      AiProvider aiProvider,
      UiTaskRunner uiTasks,
      boolean testMode) {
    this.session = Objects.requireNonNull(session, "session");
    this.testMode = testMode;
    this.uiTasks = Objects.requireNonNull(uiTasks, "uiTasks");
    FixtureRepository fixtures = session.fixtures();
    this.access = new AccessViewModel(session.accessFacade(), session.catalog(), fixtures);
    this.read =
        new ReadDataViewModel(
            session.accessFacade(),
            session.capabilityPolicy(),
            session.readDataService(),
            session.catalog(),
            fixtures);
    this.write =
        new WriteDataViewModel(
            session.accessFacade(),
            session.capabilityPolicy(),
            session.writeDataService(),
            session.catalog(),
            fixtures);
    this.dtc = new DtcViewModel(session.dtcService());
    this.interpreter = new InterpreterController(session.interpreter());
    this.analysis = new AnalysisSessionViewModel(session);
    this.ai =
        new AiAdvisorController(
            session.catalog(),
            analysis,
            read,
            Objects.requireNonNull(aiProvider, "aiProvider"),
            uiTasks);
    refreshDiagnosticStatus();
  }

  /** Wire keep-alive → Interpreter refresh (call once from JavaFX application thread). */
  public void bindKeepAliveInterpreterRefresh() {
    session
        .testerPresentKeepAlive()
        .setAfterTick(
            () -> javafx.application.Platform.runLater(this::refreshInterpreterLive));
  }

  public boolean testMode() {
    return testMode;
  }

  public String connectionId() {
    return testMode ? INTERFACE_VIRTUAL : INTERFACE_CAN;
  }

  public StringProperty transportMessageProperty() {
    return transportMessage;
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

  public DtcViewModel dtc() {
    return dtc;
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

  public StringProperty statusInterfaceProperty() {
    return statusInterface;
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

  public BooleanProperty readTabEnabledProperty() {
    return readTabEnabled;
  }

  public BooleanProperty writeTabEnabledProperty() {
    return writeTabEnabled;
  }

  public BooleanProperty dtcTabEnabledProperty() {
    return dtcTabEnabled;
  }

  public BooleanProperty aiTabEnabledProperty() {
    return aiTabEnabled;
  }

  /** True when Extended Apply needs a password popup (no remembered value yet). */
  public boolean needsUnlockPasswordPrompt() {
    return rememberedUnlockPassword == null || rememberedUnlockPassword.isBlank();
  }

  public String rememberedUnlockPassword() {
    return rememberedUnlockPassword == null ? "" : rememberedUnlockPassword;
  }

  /** Status bar text for Learning / 0x27 redaction policy. */
  public void refreshLearningStatus() {
    if (LearningMode.isEnabled()) {
      statusRedaction.set("0x27 Learning: ON (local HEX; AI still masked)");
    } else {
      statusRedaction.set("0x27 Redaction: ON (Learning OFF)");
    }
  }

  /** Read-only snapshot refresh from Access facade (no Context writes). */
  public void refreshDiagnosticStatus() {
    if (testMode) {
      statusInterface.set("Interface: VIRTUAL");
      transportMessage.set("");
    } else {
      statusInterface.set("Interface: CAN");
      transportMessage.set(
          "CAN socket transport not implemented — set TEST=TRUE for log/fixture mode");
    }
    String profile = access.selectedProfileId();
    boolean profileSelected = profile != null && !profile.isBlank();
    statusProfile.set("Profile: " + (profileSelected ? profile : "(none)"));
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
    refreshLearningStatus();

    boolean unlocked = state == DiagnosticAccessState.EXTENDED_UNLOCKED;
    boolean extended =
        state == DiagnosticAccessState.EXTENDED_LOCKED
            || state == DiagnosticAccessState.EXTENDED_UNLOCKED;
    // Re-evaluate Excel/Guard-based DID lists for the current access state.
    refreshAccessibleCapabilities();
    boolean hasReadable = !read.accessibleDidIds().isEmpty();
    boolean hasWritable = !write.accessibleDidIds().isEmpty();
    readTabEnabled.set(testMode && profileSelected && (hasReadable || extended));
    writeTabEnabled.set(testMode && profileSelected && (hasWritable || unlocked));
    dtcTabEnabled.set(testMode && profileSelected && unlocked);
    aiTabEnabled.set(testMode && unlocked);
  }

  /**
   * Profile change: Access resets to DEFAULT_LOCKED; Read/Write Observable DID lists update
   * immediately (UDS-UX-01).
   */
  public void selectProfile(String profileId) {
    access.selectProfile(profileId);
    read.onProfileChanged(profileId);
    write.onProfileChanged(profileId);
    analysis.bindProfile(profileId);
    ai.onProfileChanged(profileId);
    refreshDiagnosticStatus();
    refreshAccessibleCapabilities();
  }

  public AccessOutcome enterExtended() {
    requireTestTransport("Enter Extended");
    AccessOutcome out = access.enterExtended();
    mirrorAccessEvidence(out, "EXTENDED_SESSION");
    refreshDiagnosticStatus();
    refreshAccessibleCapabilities();
    refreshInterpreterLive();
    return out;
  }

  /** Reload Interpreter from this session's {@code live_session.log}. */
  public void refreshInterpreterLive() {
    interpreter.loadLiveSession(session.recorder().liveSessionLogPath());
  }

  /** Clear live CSV log and refresh Interpreter table. */
  public void clearInterpreterLive() {
    session.recorder().clearLiveSessionLog();
    refreshInterpreterLive();
  }

  /** Read selected DID and append Finding to Analysis Session store. */
  public ReadDataResult readSelectedAndRecord() {
    ReadDataResult result = read.readSelected();
    analysis.store().appendRead(result);
    analysis.refreshLiveSummary();
    refreshInterpreterLive();
    return result;
  }

  /** Write selected DID/value and append result to Analysis Session store. */
  public WriteResult writeSelectedAndRecord() {
    WriteResult result = write.writeSelected();
    analysis.store().appendWrite(result);
    analysis.refreshLiveSummary();
    refreshInterpreterLive();
    return result;
  }

  private void mirrorAccessEvidence(AccessOutcome out, String operation) {
    if (out == null || out.evidenceId() == null || out.evidenceId().isBlank()) {
      return;
    }
    analysis
        .store()
        .appendAccess(
            new AccessEvidence(
                out.evidenceId(),
                session.analysisSessionId(),
                operation,
                out.responseKind() == null || out.responseKind().isBlank()
                    ? "UNKNOWN"
                    : out.responseKind(),
                out.errorCode(),
                out.resultingState().name(),
                true));
    analysis.refreshLiveSummary();
  }

  public List<DiagnosticSessionChoice> sessionsForSelectedProfile() {
    return access.sessionsForProfile(access.selectedProfileId());
  }

  public List<DiagnosticSessionChoice> sessionsForProfile(String profileId) {
    return access.sessionsForProfile(profileId);
  }

  /**
   * Session Control Apply — catalog session key ({@code DEFAULT}/{@code EXTENDED}/…). Default
   * resets; Extended without password only enters 0x10 (tests). UI should call {@link
   * #applyExtendedWithPassword(String)} so Security Unlock is included.
   */
  public AccessOutcome applySessionControl(DiagnosticSessionChoice choice) {
    requireTestTransport("Session Control");
    if (choice == null) {
      return AccessOutcome.of(
          false, access.accessState(), "SESSION_NOT_SELECTED", null, "NEGATIVE", null);
    }
    if (choice.isExtended()) {
      if (!needsUnlockPasswordPrompt()) {
        return applyExtendedWithPassword(rememberedUnlockPassword);
      }
      return enterExtended();
    }
    if (choice.isDefault()) {
      AccessOutcome out = access.enterDefault();
      mirrorAccessEvidence(out, "DEFAULT_SESSION");
      refreshDiagnosticStatus();
      refreshAccessibleCapabilities();
      refreshInterpreterLive();
      return out;
    }
    return AccessOutcome.of(
        false,
        access.accessState(),
        "SESSION_NOT_SUPPORTED",
        null,
        "NEGATIVE",
        choice.displayName()
            + " ("
            + choice.subFunctionHex()
            + ") is listed in catalog but not wired in this educational simulator");
  }

  /**
   * Extended Session (0x10 03) + Security Unlock (0x27) in one step. Remembers password in memory
   * after a successful unlock for later Extended Apply.
   *
   * <p>Educational Apply is atomic: if unlock fails, roll back to {@code DEFAULT_LOCKED} so the UI
   * does not stay in Extended after a wrong password.
   */
  public AccessOutcome applyExtendedWithPassword(String password) {
    requireTestTransport("Session Control");
    if (password == null || password.isBlank()) {
      return AccessOutcome.of(
          false, access.accessState(), "EMPTY_PASSWORD", null, "NEGATIVE", null);
    }
    AccessOutcome sessionOut = enterExtended();
    if (sessionOut.errorCode() != null && !sessionOut.errorCode().isBlank()) {
      return sessionOut;
    }
    if (sessionOut.resultingState() != DiagnosticAccessState.EXTENDED_LOCKED
        && sessionOut.resultingState() != DiagnosticAccessState.EXTENDED_UNLOCKED) {
      return sessionOut;
    }
    access.setPassword(password);
    AccessOutcome unlocked = unlock();
    boolean ok =
        unlocked.resultingState() == DiagnosticAccessState.EXTENDED_UNLOCKED
            && (unlocked.errorCode() == null || unlocked.errorCode().isBlank());
    if (ok) {
      rememberedUnlockPassword = password;
      return unlocked;
    }
    // Unlock failed after 0x10 already succeeded — 10 01 back to Default (logged).
    AccessOutcome back = access.enterDefault();
    mirrorAccessEvidence(back, "DEFAULT_SESSION");
    refreshDiagnosticStatus();
    refreshAccessibleCapabilities();
    refreshInterpreterLive();
    String err =
        unlocked.errorCode() == null || unlocked.errorCode().isBlank()
            ? "INVALID_KEY"
            : unlocked.errorCode();
    return AccessOutcome.of(
        false,
        DiagnosticAccessState.DEFAULT_LOCKED,
        err,
        unlocked.evidenceId(),
        unlocked.responseKind() == null ? "NEGATIVE" : unlocked.responseKind(),
        unlocked.responseHex());
  }

  public AccessOutcome unlock() {
    requireTestTransport("Unlock");
    // AccessViewModel.unlock() clears the field in finally — capture first.
    String pwdForCache = access.password();
    AccessOutcome out = access.unlock();
    mirrorAccessEvidence(out, "SECURITY_ACCESS");
    refreshDiagnosticStatus();
    refreshAccessibleCapabilities();
    refreshInterpreterLive();
    if (access.accessState() == DiagnosticAccessState.EXTENDED_UNLOCKED) {
      read.clearAccessDenied();
      write.clearAccessDenied();
      if (pwdForCache != null && !pwdForCache.isBlank()) {
        rememberedUnlockPassword = pwdForCache;
      }
    }
    return out;
  }

  private void requireTestTransport(String action) {
    if (!testMode) {
      throw new IllegalStateException(
          action
              + " requires TEST=TRUE (offline fixture). CAN socket transport is not implemented yet.");
    }
  }

  private void refreshAccessibleCapabilities() {
    read.refreshAccessibleDids();
    write.refreshAccessibleDids();
  }

  /** Test helper: Virtual ECU preset A — voltage abnormal Read. */
  public void applyDemoPresetA() {
    selectProfile("FLIPSKY_75100_VESC");
    session.accessFacade().setFixtureRunConfig("RD-VESC-001");
    read.selectScenario("RD-VESC-001");
    read.selectDid("0x1001");
    ai.setSymptomText("급정지");
    ai.refreshSuggestions();
    refreshDiagnosticStatus();
  }

  /** Test helper: Virtual ECU preset B — locked deny DID selection. */
  public void applyDemoPresetB() {
    selectProfile("FLIPSKY_75100_VESC");
    if (!read.runtimeDidIds().isEmpty()) {
      read.selectDid(read.runtimeDidIds().get(0));
    }
    refreshDiagnosticStatus();
  }

  /** Test helper: Virtual ECU preset C — Write happy path setup. */
  public void applyDemoPresetC() {
    selectProfile("FLIPSKY_75100_VESC");
    session.accessFacade().setFixtureRunConfig("WD-VESC-001");
    write.selectScenario("WD-VESC-001");
    write.selectDid("0x2002");
    write.setInputValue("12000");
    refreshDiagnosticStatus();
  }
}
