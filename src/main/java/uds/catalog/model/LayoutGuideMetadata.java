package uds.catalog.model;

/** Human/AI guide text for a request/response layout (guide catalog). */
public record LayoutGuideMetadata(
    String kind, String sidOrMarker, String exampleHex, String whenYouNeedThisKo, String notes) {

  public LayoutGuideMetadata {
    kind = nullToEmpty(kind);
    sidOrMarker = nullToEmpty(sidOrMarker);
    exampleHex = nullToEmpty(exampleHex);
    whenYouNeedThisKo = nullToEmpty(whenYouNeedThisKo);
    notes = nullToEmpty(notes);
  }

  public String joinKey() {
    return kind + "|" + sidOrMarker;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
