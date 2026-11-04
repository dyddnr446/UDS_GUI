package uds.gui.app;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Runs long work (external AI) off the caller/UI thread with cancel + 15s timeout (NFR-15/17,
 * DOD-08). UDS-UX-03: observable running/status for declarative UI binding.
 */
public final class UiTaskRunner implements AutoCloseable {

  public static final long DEFAULT_TIMEOUT_SECONDS = 15L;

  private final ExecutorService executor;
  private final long timeoutSeconds;
  private final AtomicReference<Future<?>> inFlight = new AtomicReference<>();
  private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
  private final BooleanProperty running = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("");

  public UiTaskRunner() {
    this(DEFAULT_TIMEOUT_SECONDS);
  }

  public UiTaskRunner(long timeoutSeconds) {
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be > 0");
    }
    this.timeoutSeconds = timeoutSeconds;
    this.executor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "uds-ui-task");
              t.setDaemon(true);
              return t;
            });
  }

  /** Package-visible for tests that inject a shared executor. */
  UiTaskRunner(long timeoutSeconds, ExecutorService executor) {
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be > 0");
    }
    this.timeoutSeconds = timeoutSeconds;
    this.executor = Objects.requireNonNull(executor, "executor");
  }

  public BooleanProperty runningProperty() {
    return running;
  }

  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  public boolean isRunning() {
    return running.get() || (inFlight.get() != null && !inFlight.get().isDone());
  }

  /**
   * Submits work asynchronously. Returns immediately (caller never blocks on {@code work}).
   * Completion callbacks are marshalled to the JavaFX thread when the toolkit is alive; otherwise
   * they run on the worker completion thread (unit tests).
   */
  public <T> void runAsync(
      Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError, Runnable onCancelled) {
    Objects.requireNonNull(work, "work");
    Objects.requireNonNull(onSuccess, "onSuccess");
    Objects.requireNonNull(onError, "onError");
    Objects.requireNonNull(onCancelled, "onCancelled");

    cancelCurrent();
    cancelRequested.set(false);
    setRunning(true, "Running…");

    CompletableFuture<T> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return work.call();
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            },
            executor);

    inFlight.set(future);

    future
        .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .whenComplete(
            (value, error) -> {
              inFlight.compareAndSet(future, null);
              if (cancelRequested.get() || future.isCancelled()) {
                setRunning(false, "Cancelled");
                deliver(onCancelled);
                return;
              }
              if (error != null) {
                Throwable root = unwrap(error);
                if (root instanceof CancellationException) {
                  setRunning(false, "Cancelled");
                  deliver(onCancelled);
                } else if (root instanceof TimeoutException) {
                  setRunning(false, "Timeout");
                  deliver(
                      () ->
                          onError.accept(
                              new TimeoutException(
                                  "UI task exceeded " + timeoutSeconds + "s")));
                } else {
                  setRunning(false, "Error");
                  deliver(() -> onError.accept(root));
                }
                return;
              }
              setRunning(false, "Complete");
              deliver(() -> onSuccess.accept(value));
            });
  }

  /** Requests cancel of the in-flight task. Deterministic store data is not cleared here. */
  public void cancelCurrent() {
    cancelRequested.set(true);
    Future<?> f = inFlight.getAndSet(null);
    if (f != null) {
      f.cancel(true);
    }
  }

  /** @deprecated use {@link #cancelCurrent()} */
  public void cancel() {
    cancelCurrent();
  }

  @Override
  public void close() {
    cancelCurrent();
    executor.shutdownNow();
    setRunning(false, "");
  }

  private void setRunning(boolean value, String message) {
    deliver(
        () -> {
          running.set(value);
          statusMessage.set(message == null ? "" : message);
        });
  }

  private static void deliver(Runnable action) {
    try {
      if (Platform.isFxApplicationThread()) {
        action.run();
      } else {
        Platform.runLater(action);
      }
    } catch (IllegalStateException toolkitNotReady) {
      action.run();
    }
  }

  private static Throwable unwrap(Throwable error) {
    Throwable t = error;
    while (t.getCause() != null
        && (t instanceof java.util.concurrent.CompletionException
            || t instanceof RuntimeException && t.getClass() == RuntimeException.class)) {
      t = t.getCause();
    }
    return t;
  }
}
