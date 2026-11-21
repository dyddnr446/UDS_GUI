package uds.exchange;

import java.util.Objects;

/** Immutable diagnostic exchange response. */
public record ExchangeResponse(
    String responseId,
    String requestId,
    String kind,
    String hex,
    String fixtureId,
    boolean payloadRedacted) {

  public ExchangeResponse {
    Objects.requireNonNull(responseId, "responseId");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(hex, "hex");
  }

  public ExchangeResponse withHexAndRedacted(String newHex, boolean redacted) {
    return new ExchangeResponse(responseId, requestId, kind, newHex, fixtureId, redacted);
  }
}
