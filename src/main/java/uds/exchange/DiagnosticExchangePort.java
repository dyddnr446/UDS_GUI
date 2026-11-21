package uds.exchange;

/** Service-facing exchange port. Implementations must not be referenced by type outside wiring. */
public interface DiagnosticExchangePort {
  ExchangeResponse exchange(ExchangeRequest request);
}
