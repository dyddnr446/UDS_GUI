package uds.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import uds.ai.AdvisorContext;
import uds.ai.AdvisorContextSanitizer;
import uds.ai.AdvisorPromptBuilder;
import uds.ai.AiProvider;
import uds.analysis.AnalysisSessionStore;

/**
 * Orchestrates sanitize → prompt → provider → evidence validation (FR-09/12/17, NFR-12/14/16).
 * Never mutates deterministic Finding/WriteResult/AccessEvidence.
 */
public final class AiAdvisor {

  private final AdvisorContextSanitizer sanitizer;
  private final AdvisorPromptBuilder promptBuilder;
  private final AiProvider provider;

  public AiAdvisor(AiProvider provider) {
    this(new AdvisorContextSanitizer(), new AdvisorPromptBuilder(), provider);
  }

  public AiAdvisor(
      AdvisorContextSanitizer sanitizer, AdvisorPromptBuilder promptBuilder, AiProvider provider) {
    this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  public AdviseResult advise(String symptomText, String profileId, AnalysisSessionStore store) {
    Objects.requireNonNull(store, "store");
    AdvisorContext context = sanitizer.sanitize(symptomText, profileId, store);
    return advise(context);
  }

  public AdviseResult advise(AdvisorContext context) {
    Objects.requireNonNull(context, "context");
    if (context.isEmpty()) {
      return new AdviseResult.Err(new AiError(AiError.EMPTY_CONTEXT, "empty AdvisorContext"));
    }
    String prompt = promptBuilder.build(context);
    AiProvider.ProviderResult raw = provider.analyze(prompt);
    if (raw instanceof AiProvider.ProviderFailure failure) {
      return new AdviseResult.Err(failure.error());
    }
    AiProvider.ProviderSuccess success = (AiProvider.ProviderSuccess) raw;
    return validate(success, context.availableEvidenceIds());
  }

  private static AdviseResult validate(AiProvider.ProviderSuccess success, Set<String> allowed) {
    List<AiAdviceResult.ChecklistItem> kept = new ArrayList<>();
    List<String> dropped = new ArrayList<>();
    LinkedHashSet<String> evidenceUnion = new LinkedHashSet<>();
    for (AiProvider.ChecklistDraft draft : success.checklist()) {
      if (draft == null || draft.text() == null || draft.text().isBlank()) {
        dropped.add("(blank)");
        continue;
      }
      List<String> grounded = new ArrayList<>();
      if (draft.evidenceIds() != null) {
        for (String id : draft.evidenceIds()) {
          if (id != null && allowed.contains(id)) {
            grounded.add(id);
            evidenceUnion.add(id);
          }
        }
      }
      if (grounded.isEmpty()) {
        dropped.add(draft.text());
        continue;
      }
      kept.add(new AiAdviceResult.ChecklistItem(draft.text().trim(), grounded));
    }
    List<String> warnings = new ArrayList<>();
    if (!dropped.isEmpty()) {
      warnings.add("dropped ungrounded checklist items: " + dropped.size());
    }
    String summary = success.summary() == null ? "" : success.summary().trim();
    if (kept.isEmpty() && summary.isEmpty()) {
      return new AdviseResult.Err(
          new AiError(AiError.NO_GROUNDED_ITEMS, "no grounded AI checklist items"));
    }
    if (kept.isEmpty()) {
      warnings.add("checklist empty after evidence validation");
    }
    return new AdviseResult.Ok(
        new AiAdviceResult(summary, kept, List.copyOf(evidenceUnion), List.copyOf(warnings)));
  }

  public sealed interface AdviseResult {
    record Ok(AiAdviceResult advice) implements AdviseResult {}

    record Err(AiError error) implements AdviseResult {}
  }
}
