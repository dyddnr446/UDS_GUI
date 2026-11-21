package uds.catalog.model;

/** Human/AI guide text for a DID (guide catalog). */
public record DidGuideMetadata(
    String didId,
    String speedBasis,
    String sourceRef,
    String example,
    String whenToUseKo,
    String whyUseKo,
    String notes) {

  public DidGuideMetadata {
    didId = nullToEmpty(didId);
    speedBasis = nullToEmpty(speedBasis);
    sourceRef = nullToEmpty(sourceRef);
    example = nullToEmpty(example);
    whenToUseKo = nullToEmpty(whenToUseKo);
    whyUseKo = nullToEmpty(whyUseKo);
    notes = nullToEmpty(notes);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
