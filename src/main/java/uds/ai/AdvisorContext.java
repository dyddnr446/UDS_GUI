package uds.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Sanitized AI input facts with evidence IDs (FR-09). Never holds secrets. */
public record AdvisorContext(String symptomText, String profileId, List<FactRow> facts) {

  public AdvisorContext {
    Objects.requireNonNull(symptomText, "symptomText");
    Objects.requireNonNull(profileId, "profileId");
    facts = List.copyOf(facts == null ? List.of() : facts);
  }

  public Set<String> availableEvidenceIds() {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (FactRow f : facts) {
      ids.add(f.evidenceId());
    }
    return Set.copyOf(ids);
  }

  public boolean isEmpty() {
    return symptomText.isBlank() && facts.isEmpty();
  }

  public record FactRow(String evidenceId, String kind, Map<String, String> fields) {
    public FactRow {
      Objects.requireNonNull(evidenceId, "evidenceId");
      Objects.requireNonNull(kind, "kind");
      fields = Map.copyOf(fields == null ? Map.of() : fields);
    }
  }
}
