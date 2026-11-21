package uds.catalog.model;

/** Runtime DTC status bit identity (system catalog). */
public record DtcStatusBitDefinition(int bit, String mask, String nameEn) {

  public DtcStatusBitDefinition {
    mask = nullToEmpty(mask);
    nameEn = nullToEmpty(nameEn);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
