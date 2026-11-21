package uds.catalog.model;

/** Human/AI guide text for a symptom mapping (guide catalog). */
public record SymptomGuideMetadata(String symptomKey, String reasonKo, String notes) {

  public SymptomGuideMetadata {
    symptomKey = nullToEmpty(symptomKey);
    reasonKo = nullToEmpty(reasonKo);
    notes = nullToEmpty(notes);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
