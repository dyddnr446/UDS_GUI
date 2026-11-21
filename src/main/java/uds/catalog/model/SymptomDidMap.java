package uds.catalog.model;

/** Runtime symptom→DID mapping keys (DID/DTC catalog). */
public record SymptomDidMap(
    String symptomKey, String keywordsKo, int didId, int priority, String reasonKo) {

  public SymptomDidMap {
    symptomKey = nullToEmpty(symptomKey);
    keywordsKo = nullToEmpty(keywordsKo);
    reasonKo = nullToEmpty(reasonKo);
  }

  public String didIdHex() {
    return DidDefinition.formatDidHex(didId);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
