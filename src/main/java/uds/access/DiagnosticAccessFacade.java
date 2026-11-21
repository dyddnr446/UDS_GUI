package uds.access;

import java.util.Objects;
import uds.fixture.FileDiagnosticExchangePort;
import uds.fixture.FixtureRunConfig;
import uds.access.DiagnosticAccessGuard;
import uds.access.GuardDecision;
import uds.access.AccessOutcome;
import uds.access.SecurityAccessService;
import uds.access.SessionControlService;
import uds.access.TesterPresentKeepAlive;
import uds.access.AccessSnapshot;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;

/**
 * GUI-facing access orchestration only — does not re-implement UDS protocol (UC-06).
 */
public final class DiagnosticAccessFacade {

  private final ActiveDiagnosticContext context;
  private final SessionControlService sessionControl;
  private final SecurityAccessService securityAccess;
  private final FileDiagnosticExchangePort filePort;
  private final DiagnosticAccessGuard guard;
  private final TesterPresentKeepAlive keepAlive;

  public DiagnosticAccessFacade(
      ActiveDiagnosticContext context,
      SessionControlService sessionControl,
      SecurityAccessService securityAccess,
      FileDiagnosticExchangePort filePort,
      DiagnosticAccessGuard guard) {
    this(context, sessionControl, securityAccess, filePort, guard, null);
  }

  public DiagnosticAccessFacade(
      ActiveDiagnosticContext context,
      SessionControlService sessionControl,
      SecurityAccessService securityAccess,
      FileDiagnosticExchangePort filePort,
      DiagnosticAccessGuard guard,
      TesterPresentKeepAlive keepAlive) {
    this.context = Objects.requireNonNull(context, "context");
    this.sessionControl = Objects.requireNonNull(sessionControl, "sessionControl");
    this.securityAccess = Objects.requireNonNull(securityAccess, "securityAccess");
    this.filePort = Objects.requireNonNull(filePort, "filePort");
    this.guard = Objects.requireNonNull(guard, "guard");
    this.keepAlive = keepAlive;
  }

  public AccessSnapshot selectProfile(String profileId, String catalogVersion) {
    if (keepAlive != null) {
      keepAlive.stop();
    }
    return context.selectProfile(profileId, catalogVersion);
  }

  public AccessSnapshot reset() {
    if (keepAlive != null) {
      keepAlive.stop();
    }
    return context.reset();
  }

  public AccessSnapshot snapshot() {
    return context.snapshot();
  }

  /** Scenario/flow selection for File Port only. */
  public void setFixtureRunConfig(String scenarioOrFlowId) {
    filePort.setFixtureRunConfig(new FixtureRunConfig(scenarioOrFlowId));
  }

  /** Primes CSV initial_state before a flow step. */
  public void primeState(DiagnosticAccessState state) {
    context.primeState(state);
  }

  public AccessOutcome enterExtended() {
    return sessionControl.enterExtended();
  }

  public AccessOutcome enterDefault() {
    return sessionControl.enterDefault();
  }

  public AccessOutcome requestSeed() {
    return securityAccess.requestSeed();
  }

  public AccessOutcome unlock(char[] password) {
    return securityAccess.sendKey(password);
  }

  public GuardDecision checkRead(String didId) {
    return guard.check(didId, DiagnosticAccessGuard.Operation.READ);
  }

  public GuardDecision checkWrite(String didId) {
    return guard.check(didId, DiagnosticAccessGuard.Operation.WRITE);
  }
}
