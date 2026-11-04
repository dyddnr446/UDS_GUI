package uds.access.facade;

import java.util.Objects;
import uds.fixture.port.FileDiagnosticExchangePort;
import uds.fixture.config.FixtureRunConfig;
import uds.access.guard.DiagnosticAccessGuard;
import uds.access.guard.GuardDecision;
import uds.access.service.AccessOutcome;
import uds.access.service.SecurityAccessService;
import uds.access.service.SessionControlService;
import uds.access.state.AccessSnapshot;
import uds.access.state.ActiveDiagnosticContext;
import uds.access.state.DiagnosticAccessState;

/**
 * GUI-facing access orchestration only — does not re-implement UDS protocol (UC-06).
 */
public final class DiagnosticAccessFacade {

  private final ActiveDiagnosticContext context;
  private final SessionControlService sessionControl;
  private final SecurityAccessService securityAccess;
  private final FileDiagnosticExchangePort filePort;
  private final DiagnosticAccessGuard guard;

  public DiagnosticAccessFacade(
      ActiveDiagnosticContext context,
      SessionControlService sessionControl,
      SecurityAccessService securityAccess,
      FileDiagnosticExchangePort filePort,
      DiagnosticAccessGuard guard) {
    this.context = Objects.requireNonNull(context, "context");
    this.sessionControl = Objects.requireNonNull(sessionControl, "sessionControl");
    this.securityAccess = Objects.requireNonNull(securityAccess, "securityAccess");
    this.filePort = Objects.requireNonNull(filePort, "filePort");
    this.guard = Objects.requireNonNull(guard, "guard");
  }

  public AccessSnapshot selectProfile(String profileId, String catalogVersion) {
    return context.selectProfile(profileId, catalogVersion);
  }

  public AccessSnapshot reset() {
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
