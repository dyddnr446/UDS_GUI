package uds.catalog.model;

/** Human/AI guide text for a controller profile (guide catalog). */
public record ControllerProfileGuideMetadata(
    String profileId, String speedBasis, String telemetryReference, String notes) {

  public ControllerProfileGuideMetadata {
    profileId = nullToEmpty(profileId);
    speedBasis = nullToEmpty(speedBasis);
    telemetryReference = nullToEmpty(telemetryReference);
    notes = nullToEmpty(notes);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
