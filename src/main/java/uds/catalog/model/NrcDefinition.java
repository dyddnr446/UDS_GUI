package uds.catalog.model;

/** Runtime NRC identity (system catalog). */
public record NrcDefinition(String code, String name) {

  public NrcDefinition {
    code = nullToEmpty(code);
    name = nullToEmpty(name);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
