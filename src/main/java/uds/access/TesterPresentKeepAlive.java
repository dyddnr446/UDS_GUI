package uds.access;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import uds.access.AccessSnapshot;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;
import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeRequest;

/**
 * Sends periodic TesterPresent ({@code 3E 80}, suppress) while Extended session is active. Interval
 * follows {@link SessionTiming#keepAliveIntervalMs()}.
 */
public final class TesterPresentKeepAlive implements AutoCloseable {

  public static final String REQUEST_HEX = "3E 80";

  private final ActiveDiagnosticContext context;
  private final DiagnosticExchangePort port;
  private final String analysisSessionId;
  private final AtomicReference<SessionTiming> timing =
      new AtomicReference<>(SessionTiming.DEFAULT);
  private final ScheduledExecutorService executor;
  private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
  private final AtomicReference<Runnable> afterTick = new AtomicReference<>();

  public TesterPresentKeepAlive(
      ActiveDiagnosticContext context,
      DiagnosticExchangePort port,
      String analysisSessionId) {
    this.context = Objects.requireNonNull(context, "context");
    this.port = Objects.requireNonNull(port, "port");
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "uds-tester-present");
              t.setDaemon(true);
              return t;
            });
  }

  public void setTiming(SessionTiming timing) {
    this.timing.set(timing == null ? SessionTiming.DEFAULT : timing);
  }

  public SessionTiming timing() {
    return timing.get();
  }

  /** Optional UI refresh hook (e.g. reload Interpreter live log). */
  public void setAfterTick(Runnable afterTick) {
    this.afterTick.set(afterTick);
  }

  public synchronized void start() {
    stopScheduleOnly();
    DiagnosticAccessState state = context.snapshot().accessState();
    if (state != DiagnosticAccessState.EXTENDED_LOCKED
        && state != DiagnosticAccessState.EXTENDED_UNLOCKED) {
      return;
    }
    long interval = timing.get().keepAliveIntervalMs();
    ScheduledFuture<?> f =
        executor.scheduleAtFixedRate(this::safeTick, interval, interval, TimeUnit.MILLISECONDS);
    future.set(f);
  }

  public synchronized void start(SessionTiming sessionTiming) {
    setTiming(sessionTiming);
    start();
  }

  public synchronized void stop() {
    stopScheduleOnly();
  }

  public boolean isRunning() {
    ScheduledFuture<?> f = future.get();
    return f != null && !f.isCancelled() && !f.isDone();
  }

  /** One-shot send for tests / immediate keep-alive. */
  public void sendOnce() {
    tick();
  }

  private void safeTick() {
    try {
      tick();
    } catch (RuntimeException ignored) {
      // Keep scheduler alive; fixture/profile races should not kill the thread.
    }
  }

  private void tick() {
    AccessSnapshot snap = context.snapshot();
    DiagnosticAccessState state = snap.accessState();
    if (state != DiagnosticAccessState.EXTENDED_LOCKED
        && state != DiagnosticAccessState.EXTENDED_UNLOCKED) {
      stop();
      return;
    }
    if (!snap.profileSelected()) {
      stop();
      return;
    }
    ExchangeRequest request =
        new ExchangeRequest(
            analysisSessionId,
            "req-" + UUID.randomUUID(),
            snap.profileId(),
            "0x3E",
            null,
            REQUEST_HEX,
            snap.generation());
    port.exchange(request);
    Runnable hook = afterTick.get();
    if (hook != null) {
      hook.run();
    }
  }

  private void stopScheduleOnly() {
    ScheduledFuture<?> f = future.getAndSet(null);
    if (f != null) {
      f.cancel(false);
    }
  }

  @Override
  public void close() {
    stop();
    executor.shutdownNow();
  }
}
