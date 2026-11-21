package uds.ai;

/**
 * Low-level chat completion (system + user → raw text). Used for DID selection JSON before advice.
 * Kept separate from {@link AiProvider} so advice mocks can stay single-method lambdas.
 */
@FunctionalInterface
public interface AiChatCompleter {

  CompletionResult complete(String systemPrompt, String userPrompt);

  sealed interface CompletionResult permits CompletionResult.Ok, CompletionResult.Err {
    record Ok(String content) implements CompletionResult {}

    record Err(AiError error) implements CompletionResult {}
  }
}
