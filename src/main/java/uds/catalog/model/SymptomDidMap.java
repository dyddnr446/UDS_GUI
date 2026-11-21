package uds.catalog.model;

import uds.catalog.model.DidDefinition;

/** Runtime symptom→DID mapping keys (system catalog). */
public record SymptomDidMap(String symptomKey, String keywordsKo, int didId, int priority) {

  public SymptomDidMap {
    symptomKey = nullToEmpty(symptomKey);
    keywordsKo = nullToEmpty(keywordsKo);
  }

  public String didIdHex() {
    return DidDefinition.formatDidHex(didId);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
