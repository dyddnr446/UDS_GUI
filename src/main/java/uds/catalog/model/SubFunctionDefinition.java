package uds.catalog.model;

/** Runtime sub-function identity (system catalog). */
public record SubFunctionDefinition(
    String parentSid, String subFunction, String name, boolean suppressBitApplicable) {

  public SubFunctionDefinition {
    parentSid = nullToEmpty(parentSid);
    subFunction = nullToEmpty(subFunction);
    name = nullToEmpty(name);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
