package uds.exchange;

import java.util.Objects;

/**
 * Immutable diagnostic exchange request. Must not carry scenarioId (FR-14, FR-21) — scenario is
 * applied only via FileDiagnosticExchangePort.setFixtureRunConfig.
 */
public record ExchangeRequest(
    String analysisSessionId,
    String requestId,
    String profile,
    String service,
    String did,
    String hex,
    long generation) {

  public ExchangeRequest {
    Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(hex, "hex");
  }
}
