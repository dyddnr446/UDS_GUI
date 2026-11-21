package uds.catalog.model;

/** Human/AI guide text for a DTC status bit (guide catalog). */
public record DtcStatusBitGuideMetadata(int bit, String meaningKo, String checkPointKo) {

  public DtcStatusBitGuideMetadata {
    meaningKo = nullToEmpty(meaningKo);
    checkPointKo = nullToEmpty(checkPointKo);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
