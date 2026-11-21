package uds.ai;

import java.util.List;
import uds.ai.AiError;

/** Port for external AI completion (FR-12, FR-18). */
public interface AiProvider {

  ProviderResult analyze(String prompt);

  record ChecklistDraft(String text, List<String> evidenceIds) {}

  record ProviderSuccess(String summary, List<ChecklistDraft> checklist) implements ProviderResult {}

  record ProviderFailure(AiError error) implements ProviderResult {}

  sealed interface ProviderResult permits ProviderSuccess, ProviderFailure {}
}
