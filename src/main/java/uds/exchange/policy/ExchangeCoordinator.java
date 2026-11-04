package uds.exchange.policy;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import uds.exchange.api.DiagnosticExchangePort;
import uds.exchange.api.ExchangeRequest;
import uds.exchange.api.ExchangeResponse;

/** Correlation / cancel adjustment only; delegates exchange to the configured port. */
public final class ExchangeCoordinator implements DiagnosticExchangePort {

  private final DiagnosticExchangePort port;
  private final ExchangePolicy policy;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  public ExchangeCoordinator(DiagnosticExchangePort port, ExchangePolicy policy) {
    this.port = Objects.requireNonNull(port, "port");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public ExchangeResponse exchange(ExchangeRequest request) {
    if (cancelled.get()) {
      throw new IllegalStateException("exchange cancelled");
    }
    ExchangeRequest applied = policy.apply(request);
    ExchangeResponse response = port.exchange(applied);
    return policy.applyResponse(response);
  }

  public void cancel() {
    cancelled.set(true);
  }

  public void resetCancel() {
    cancelled.set(false);
  }
}
