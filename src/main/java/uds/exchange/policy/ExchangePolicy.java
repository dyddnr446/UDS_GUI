package uds.exchange.policy;

import uds.exchange.api.ExchangeRequest;
import uds.exchange.api.ExchangeResponse;
/** Current pass-through boundary for future timeout / 0x78 / retry policy. */
public final class ExchangePolicy {

  public ExchangeRequest apply(ExchangeRequest request) {
    return request;
  }

  public ExchangeResponse applyResponse(ExchangeResponse response) {
    return response;
  }
}
