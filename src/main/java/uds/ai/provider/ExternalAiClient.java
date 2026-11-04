package uds.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uds.ai.advisor.AiError;

/**
 * OpenAI-compatible chat client. API key from env {@code UDS_AI_API_KEY} only (FR-18, NFR-15/18).
 */
public final class ExternalAiClient implements AiProvider {

  public static final String ENV_API_KEY = "UDS_AI_API_KEY";
  public static final String ENV_BASE_URL = "UDS_AI_BASE_URL";
  public static final String ENV_MODEL = "UDS_AI_MODEL";
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

  private final HttpClient httpClient;
  private final ObjectMapper mapper;
  private final Duration timeout;
  private final String apiKey;
  private final String baseUrl;
  private final String model;

  public ExternalAiClient() {
    this(
        HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build(),
        new ObjectMapper(),
        DEFAULT_TIMEOUT,
        envValue(ENV_API_KEY),
        envOr(ENV_BASE_URL, "https://api.openai.com/v1"),
        envOr(ENV_MODEL, "gpt-4o-mini"));
  }

  /** Visible for tests (missing-key / fake endpoint without reading process env). */
  public ExternalAiClient(
      HttpClient httpClient,
      ObjectMapper mapper,
      Duration timeout,
      String apiKey,
      String baseUrl,
      String model) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.apiKey = apiKey;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.model = model;
  }

  @Override
  public ProviderResult analyze(String prompt) {
    if (apiKey == null || apiKey.isBlank()) {
      return new ProviderFailure(new AiError(AiError.KEY_MISSING, "UDS_AI_API_KEY is not set"));
    }
    try {
      ObjectNode body = mapper.createObjectNode();
      body.put("model", model);
      body.put("temperature", 0);
      ObjectNode format = body.putObject("response_format");
      format.put("type", "json_object");
      ArrayNode messages = body.putArray("messages");
      ObjectNode system = messages.addObject();
      system.put("role", "system");
      system.put("content", "Return only valid JSON for UDS advice.");
      ObjectNode user = messages.addObject();
      user.put("role", "user");
      user.put("content", prompt);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/chat/completions"))
              .timeout(timeout)
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return new ProviderFailure(
            new AiError(AiError.NETWORK, "AI HTTP status " + response.statusCode()));
      }
      return parseCompletion(response.body());
    } catch (java.net.http.HttpTimeoutException e) {
      return new ProviderFailure(new AiError(AiError.TIMEOUT, "AI request exceeded 15s"));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new ProviderFailure(new AiError(AiError.CANCELLED, "AI request interrupted"));
    } catch (Exception e) {
      return new ProviderFailure(
          new AiError(AiError.NETWORK, "AI request failed: " + safeMessage(e)));
    }
  }

  private ProviderResult parseCompletion(String body) {
    try {
      JsonNode root = mapper.readTree(body);
      String content =
          root.path("choices").path(0).path("message").path("content").asText(null);
      if (content == null || content.isBlank()) {
        return new ProviderFailure(new AiError(AiError.INVALID_RESPONSE, "empty AI content"));
      }
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

  /**
   * OS environment first, then {@code uds.env.*} system property from local {@code .env}
   * (loaded by GUI startup). Never logs the value.
   */
  private static String envValue(String name) {
    String fromOs = System.getenv(name);
    if (fromOs != null && !fromOs.isBlank()) {
      return fromOs.trim();
    }
    String fromProp = System.getProperty("uds.env." + name);
    if (fromProp != null && !fromProp.isBlank()) {
      return fromProp.trim();
    }
    return null;
  }

  private static String envOr(String name, String defaultValue) {
    String v = envValue(name);
    return v == null || v.isBlank() ? defaultValue : v;
  }

  private static String trimTrailingSlash(String url) {
    if (url == null || url.isBlank()) {
      return "https://api.openai.com/v1";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String safeMessage(Exception e) {
    String m = e.getMessage();
    if (m == null) {
      return e.getClass().getSimpleName();
    }
    // Never echo API key material if a library embeds headers.
    return m.replaceAll("(?i)Bearer\\s+\\S+", "Bearer <REDACTED>");
  }
}
