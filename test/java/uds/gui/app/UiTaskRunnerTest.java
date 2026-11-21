package uds.gui.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UDS-UX-03: runningProperty toggles around async work. */
class UiTaskRunnerTest {

  private UiTaskRunner runner;

  @BeforeEach
  void setUp() {
    runner = new UiTaskRunner(15);
  }

  @AfterEach
  void tearDown() {
    runner.close();
  }

  @Test
  void runAsync_setsRunningTrueThenFalseOnComplete() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(1);
    AtomicBoolean sawRunning = new AtomicBoolean(false);

    runner.runAsync(
        () -> {
          sawRunning.set(runner.runningProperty().get());
          started.countDown();
          Thread.sleep(50);
          return "ok";
        },
        v -> done.countDown(),
        e -> done.countDown(),
        done::countDown);

    assertTrue(started.await(2, TimeUnit.SECONDS));
    assertTrue(sawRunning.get() || runner.runningProperty().get());
    assertTrue(done.await(3, TimeUnit.SECONDS));
    assertFalse(runner.runningProperty().get());
  }

  @Test
  void cancelCurrent_clearsRunning() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch cancelled = new CountDownLatch(1);

    runner.runAsync(
        () -> {
          entered.countDown();
          Thread.sleep(5_000);
          return "late";
        },
        v -> {},
        e -> {},
        cancelled::countDown);

    assertTrue(entered.await(2, TimeUnit.SECONDS));
    assertTrue(runner.runningProperty().get());
    runner.cancelCurrent();
    assertTrue(cancelled.await(2, TimeUnit.SECONDS));
    assertFalse(runner.runningProperty().get());
  }
}
