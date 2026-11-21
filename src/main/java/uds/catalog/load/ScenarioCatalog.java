package uds.catalog.load;

import java.util.List;
import java.util.Objects;
import uds.catalog.model.SymptomDidMap;

/** App-facing symptom mapping slice of the catalog (fixtures live under fixtures). */
public final class ScenarioCatalog {

  private final List<SymptomDidMap> symptomDidMaps;

  public ScenarioCatalog(List<SymptomDidMap> symptomDidMaps) {
    this.symptomDidMaps = List.copyOf(Objects.requireNonNull(symptomDidMaps, "symptomDidMaps"));
  }

  public List<SymptomDidMap> symptomDidMaps() {
    return symptomDidMaps;
  }
}
