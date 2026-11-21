package uds.catalog.model;

/** Human/AI guide text for a sub-function (guide catalog). */
public record SubFunctionGuideMetadata(
    String parentSid,
    String subFunction,
    String responseNeededPractice,
    String paramsSummary,
    String whenToUseKo,
    String whyUseKo,
    String notes) {

  public SubFunctionGuideMetadata {
    parentSid = nullToEmpty(parentSid);
    subFunction = nullToEmpty(subFunction);
    responseNeededPractice = nullToEmpty(responseNeededPractice);
    paramsSummary = nullToEmpty(paramsSummary);
    whenToUseKo = nullToEmpty(whenToUseKo);
    whyUseKo = nullToEmpty(whyUseKo);
    notes = nullToEmpty(notes);
  }

  public String joinKey() {
    return parentSid + "|" + subFunction;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
