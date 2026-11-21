package uds.io;

import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;

/**
 * Future live CAN / ISO-TP transport. Wired when {@code TEST=false}; not implemented yet.
 *
 * <p>Swap into the exchange chain in place of {@code FileDiagnosticExchangePort} once the socket
 * layer exists. Do not call from GUI feature code by concrete type.
 */
public final class CanSocketExchangePort implements DiagnosticExchangePort {

  @Override
  public ExchangeResponse exchange(ExchangeRequest request) {
    throw new UnsupportedOperationException(
        "CAN socket transport is not implemented yet (set TEST=TRUE for log/fixture offline mode)");
  }
}
