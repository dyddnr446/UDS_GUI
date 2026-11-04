package uds.ai.advisor;

import java.util.Objects;

/** Non-throwing AI failure signal. Facts must remain untouched (NFR-12). */
public record AiError(String code, String message) {

  public static final String EMPTY_CONTEXT = "EMPTY_CONTEXT";
  public static final String KEY_MISSING = "KEY_MISSING";
  public static final String TIMEOUT = "TIMEOUT";
  public static final String NETWORK = "NETWORK";
  public static final String INVALID_RESPONSE = "INVALID_RESPONSE";
  public static final String NO_GROUNDED_ITEMS = "NO_GROUNDED_ITEMS";
  public static final String CANCELLED = "CANCELLED";

  public AiError {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
  }
}
