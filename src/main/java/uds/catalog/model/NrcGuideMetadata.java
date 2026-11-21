package uds.catalog.model;

/** Human/AI guide text for an NRC (guide catalog). */
public record NrcGuideMetadata(
    String code,
    String meaningKo,
    String typicalServices,
    String whenAppearsKo,
    String whatToCheckKo,
    String notes) {

  public NrcGuideMetadata {
    code = nullToEmpty(code);
    meaningKo = nullToEmpty(meaningKo);
    typicalServices = nullToEmpty(typicalServices);
    whenAppearsKo = nullToEmpty(whenAppearsKo);
    whatToCheckKo = nullToEmpty(whatToCheckKo);
    notes = nullToEmpty(notes);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
