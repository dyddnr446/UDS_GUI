package uds.access.service;

import uds.access.state.DiagnosticAccessState;
/** Result of a SessionControl / SecurityAccess attempt. */
public record AccessOutcome(
    boolean applied,
    boolean staleDiscarded,
    DiagnosticAccessState resultingState,
    String errorCode,
    String evidenceId,
    String responseKind,
    String responseHex) {

  public static AccessOutcome stale(DiagnosticAccessState current) {
    return new AccessOutcome(false, true, current, "STALE_GENERATION", null, null, null);
  }

  public static AccessOutcome of(
      boolean applied,
      DiagnosticAccessState state,
      String errorCode,
      String evidenceId,
      String responseKind,
      String responseHex) {
    return new AccessOutcome(applied, false, state, errorCode, evidenceId, responseKind, responseHex);
  }
}
