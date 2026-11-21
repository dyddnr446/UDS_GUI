package uds.exchange;

import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;
/** Current pass-through boundary for future timeout / 0x78 / retry policy. */
public final class ExchangePolicy {

  public ExchangeRequest apply(ExchangeRequest request) {
    return request;
  }

  public ExchangeResponse applyResponse(ExchangeResponse response) {
    return response;
  }
}
