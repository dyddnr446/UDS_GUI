package uds.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import uds.ai.AiError;
import uds.ai.AiPromptConfig;
import uds.gui.app.DotEnv;

/**
 * LangChain4j {@link OpenAiChatModel} provider against an OpenAI-compatible endpoint.
 * Default target is <strong>Google Gemini</strong> ({@code generativelanguage.googleapis.com}).
 * API key from {@code UDS_AI_API_KEY} only (FR-18, NFR-15/18).
 */
public final class ExternalAiClient implements AiProvider, AiChatCompleter {

  public static final String ENV_API_KEY = "UDS_AI_API_KEY";
  public static final String ENV_BASE_URL = "UDS_AI_BASE_URL";
  public static final String ENV_MODEL = "UDS_AI_MODEL";

  /** Gemini OpenAI-compatible base URL (trailing slash OK). */
  public static final String DEFAULT_BASE_URL =
      "https://generativelanguage.googleapis.com/v1beta/openai/";

  public static final String DEFAULT_MODEL = "gemini-2.5-flash";
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

  private final ChatModel chatModel;
  private final ObjectMapper mapper;
  private final String apiKey;
  private final String systemPrompt;

  public ExternalAiClient() {
    this(
        envValue(ENV_API_KEY),
        envOr(ENV_BASE_URL, DEFAULT_BASE_URL),
        envOr(ENV_MODEL, DEFAULT_MODEL),
        DEFAULT_TIMEOUT,
        AiPromptConfig.loadNear(Path.of(System.getProperty("user.dir", "."))).systemPrompt());
  }

  /** Env-style constructor used by tests (missing key / fake endpoint). */
  public ExternalAiClient(String apiKey, String baseUrl, String model) {
    this(apiKey, baseUrl, model, DEFAULT_TIMEOUT, AiPromptConfig.DEFAULT_SYSTEM);
  }

  public ExternalAiClient(String apiKey, String baseUrl, String model, Duration timeout) {
    this(apiKey, baseUrl, model, timeout, AiPromptConfig.DEFAULT_SYSTEM);
  }

  public ExternalAiClient(
      String apiKey, String baseUrl, String model, Duration timeout, String systemPrompt) {
    this.mapper = new ObjectMapper();
    this.apiKey = apiKey;
    this.systemPrompt =
        systemPrompt == null || systemPrompt.isBlank()
            ? AiPromptConfig.DEFAULT_SYSTEM
            : systemPrompt;
    Duration t = Objects.requireNonNullElse(timeout, DEFAULT_TIMEOUT);
    if (apiKey == null || apiKey.isBlank()) {
      this.chatModel = null;
    } else {
      this.chatModel =
          OpenAiChatModel.builder()
              .apiKey(apiKey)
              .baseUrl(trimTrailingSlash(baseUrl))
              .modelName(model == null || model.isBlank() ? DEFAULT_MODEL : model)
              .temperature(0.0)
              .timeout(t)
              .maxRetries(1)
              .responseFormat(ResponseFormat.JSON)
              .logRequests(false)
              .logResponses(false)
              .build();
    }
  }

  /** Inject a pre-built {@link ChatModel} (unit tests). */
  public ExternalAiClient(ChatModel chatModel, String apiKey, ObjectMapper mapper) {
    this.chatModel = chatModel;
    this.apiKey = apiKey;
    this.mapper = Objects.requireNonNullElseGet(mapper, ObjectMapper::new);
    this.systemPrompt = AiPromptConfig.DEFAULT_SYSTEM;
  }

  /**
   * Backward-compatible test overload; HttpClient is unused (LangChain4j owns HTTP).
   *
   * @deprecated prefer {@link #ExternalAiClient(String, String, String)}
   */
  @Deprecated
  public ExternalAiClient(
      java.net.http.HttpClient ignoredHttpClient,
      ObjectMapper mapper,
      Duration timeout,
      String apiKey,
      String baseUrl,
      String model) {
    this(apiKey, baseUrl, model, timeout);
  }

  @Override
  public ProviderResult analyze(String prompt) {
    CompletionResult raw = complete(systemPrompt, prompt);
    if (raw instanceof CompletionResult.Err err) {
      return new ProviderFailure(err.error());
    }
    return parseAdviceJson(((CompletionResult.Ok) raw).content());
  }

  @Override
  public CompletionResult complete(String systemPromptOverride, String userPrompt) {
    if (apiKey == null || apiKey.isBlank() || chatModel == null) {
      return new CompletionResult.Err(
          new AiError(AiError.KEY_MISSING, "UDS_AI_API_KEY is not set"));
    }
    String sys =
        systemPromptOverride == null || systemPromptOverride.isBlank()
            ? systemPrompt
            : systemPromptOverride;
    try {
      ChatResponse response =
          chatModel.chat(SystemMessage.from(sys), UserMessage.from(userPrompt));
      String content =
          response == null || response.aiMessage() == null ? null : response.aiMessage().text();
      if (content == null || content.isBlank()) {
        return new CompletionResult.Err(
            new AiError(AiError.INVALID_RESPONSE, "empty AI content"));
      }
      return new CompletionResult.Ok(content);
    } catch (Exception e) {
      ProviderResult mapped = mapException(e);
      if (mapped instanceof ProviderFailure failure) {
        return new CompletionResult.Err(failure.error());
      }
      return new CompletionResult.Err(
          new AiError(AiError.NETWORK, "AI request failed: " + safeMessage(e)));
    }
  }

  private ProviderResult parseAdviceJson(String content) {
    try {
      JsonNode advice = mapper.readTree(content);
      String summary = advice.path("summary").asText("");
      List<ChecklistDraft> items = new ArrayList<>();
      for (JsonNode n : advice.path("checklist")) {
        List<String> ids = new ArrayList<>();
        n.path("evidenceIds").forEach(x -> ids.add(x.asText()));
        items.add(new ChecklistDraft(n.path("text").asText(""), List.copyOf(ids)));
      }
      return new ProviderSuccess(summary, List.copyOf(items));
    } catch (Exception e) {
      return new ProviderFailure(
          new AiError(AiError.INVALID_RESPONSE, "AI JSON parse failed: " + safeMessage(e)));
    }
  }

  private static ProviderResult mapException(Exception e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof TimeoutException
          || cause.getClass().getName().contains("Timeout")
          || (cause.getMessage() != null
              && cause.getMessage().toLowerCase().contains("timed out"))) {
        return new ProviderFailure(new AiError(AiError.TIMEOUT, "AI request exceeded 15s"));
      }
      if (cause instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        return new ProviderFailure(new AiError(AiError.CANCELLED, "AI request interrupted"));
      }
      cause = cause.getCause();
    }
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
      return new ProviderFailure(new AiError(AiError.CANCELLED, "AI request interrupted"));
    }
    return new ProviderFailure(
        new AiError(AiError.NETWORK, "AI request failed: " + safeMessage(e)));
  }

  private static String envValue(String name) {
    return DotEnv.get(name);
  }

  private static String envOr(String name, String defaultValue) {
    return DotEnv.getOrDefault(name, defaultValue);
  }

  private static String trimTrailingSlash(String url) {
    if (url == null || url.isBlank()) {
      return DEFAULT_BASE_URL.endsWith("/")
          ? DEFAULT_BASE_URL.substring(0, DEFAULT_BASE_URL.length() - 1)
          : DEFAULT_BASE_URL;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String safeMessage(Exception e) {
    String m = e.getMessage();
    if (m == null) {
      return e.getClass().getSimpleName();
    }
    return m.replaceAll("(?i)Bearer\\s+\\S+", "Bearer <REDACTED>");
  }
}
