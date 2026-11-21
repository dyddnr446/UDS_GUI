package uds.ai;

import java.util.List;
import java.util.Objects;

/** Validated AI advice. Deterministic facts are never mutated (FR-17, NFR-14/16). */
public record AiAdviceResult(
    String summary, List<ChecklistItem> checklist, List<String> evidenceIds, List<String> warnings) {

  public AiAdviceResult {
    Objects.requireNonNull(summary, "summary");
    checklist = List.copyOf(checklist == null ? List.of() : checklist);
    evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
    warnings = List.copyOf(warnings == null ? List.of() : warnings);
  }

  /** Persistence-compatible constructor used by Analysis Session restore. */
  public AiAdviceResult(String summary, List<String> evidenceIds, List<String> warnings) {
    this(summary, List.of(), evidenceIds, warnings);
  }

  public record ChecklistItem(String text, List<String> evidenceIds) {
    public ChecklistItem {
      Objects.requireNonNull(text, "text");
      evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
    }
  }
}
