package uds.catalog.model;

/** Human/AI guide text for a UDS service (guide catalog). */
public record ServiceGuideMetadata(
    String sidHex,
    String purposeSummaryKo,
    String whenToUseKo,
    String whyUseKo,
    String typicalFlowKo,
    String sessionNote,
    String securityNote,
    String notes,
    String exampleReqHex,
    String exampleResHex) {

  public ServiceGuideMetadata {
    sidHex = nullToEmpty(sidHex);
    purposeSummaryKo = nullToEmpty(purposeSummaryKo);
    whenToUseKo = nullToEmpty(whenToUseKo);
    whyUseKo = nullToEmpty(whyUseKo);
    typicalFlowKo = nullToEmpty(typicalFlowKo);
    sessionNote = nullToEmpty(sessionNote);
    securityNote = nullToEmpty(securityNote);
    notes = nullToEmpty(notes);
    exampleReqHex = nullToEmpty(exampleReqHex);
    exampleResHex = nullToEmpty(exampleResHex);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
