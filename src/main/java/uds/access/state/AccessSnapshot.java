package uds.access.state;

import java.util.Objects;

/** Immutable view of ActiveDiagnosticContext. */
public record AccessSnapshot(
    String profileId, String catalogVersion, long generation, DiagnosticAccessState accessState) {

  public AccessSnapshot {
    Objects.requireNonNull(accessState, "accessState");
  }

  public boolean profileSelected() {
    return profileId != null && !profileId.isBlank();
  }
}
