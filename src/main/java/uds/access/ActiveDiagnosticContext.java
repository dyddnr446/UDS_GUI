package uds.access;

import java.util.Objects;

/**
 * Single writer for profileId, catalogVersion, generation, and accessState (FR-27, NFR-19). All
 * other components read {@link #snapshot()} only.
 */
public final class ActiveDiagnosticContext {

  private String profileId;
  private String catalogVersion;
  private long generation;
  private DiagnosticAccessState accessState = DiagnosticAccessState.DEFAULT_LOCKED;

  public synchronized AccessSnapshot snapshot() {
    return new AccessSnapshot(profileId, catalogVersion, generation, accessState);
  }

  /** Profile change: bump generation and lock (FR-27). */
  public synchronized AccessSnapshot selectProfile(String profileId, String catalogVersion) {
    Objects.requireNonNull(profileId, "profileId");
    this.profileId = profileId;
    this.catalogVersion = catalogVersion;
    this.accessState = DiagnosticAccessState.DEFAULT_LOCKED;
    this.generation++;
    return snapshot();
  }

  /** Full reset: DEFAULT_LOCKED and generation++ (FR-27, NFR-21 runtime). */
  public synchronized AccessSnapshot reset() {
    this.accessState = DiagnosticAccessState.DEFAULT_LOCKED;
    this.generation++;
    return snapshot();
  }

  /**
   * Applies a validated state transition if {@code expectedGeneration} still matches. Returns false
   * when stale (caller must discard the response).
   */
  public synchronized boolean tryApplyState(long expectedGeneration, DiagnosticAccessState newState) {
    Objects.requireNonNull(newState, "newState");
    if (this.generation != expectedGeneration) {
      return false;
    }
    this.accessState = newState;
    return true;
  }

  /**
   * Primes state for CSV-driven fixture flows without an exchange (still the sole writer). Used by
   * Facade when a flow step declares an initial_state.
   */
  public synchronized void primeState(DiagnosticAccessState state) {
    this.accessState = Objects.requireNonNull(state, "state");
  }
}
