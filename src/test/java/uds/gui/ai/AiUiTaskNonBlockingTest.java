package uds.gui.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.ai.provider.AiProvider;
import uds.gui.app.AppSession;
import uds.gui.app.UiTaskRunner;
import uds.gui.shell.MainViewModel;
import uds.read.finding.Finding;
import uds.read.finding.FindingDetailCode;
import uds.read.finding.ReadDataResult;
import uds.read.finding.ReadStatus;

class AiUiTaskNonBlockingTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final Path PASSWORD = ROOT.resolve("config").resolve("demo_password.txt");

  @TempDir Path tempDir;

  private UiTaskRunner runner;
  private MainViewModel main;
  private CountDownLatch workEntered;
  private AtomicBoolean workFinished;

  @BeforeEach
  void setUp() throws Exception {
    workEntered = new CountDownLatch(1);
    workFinished = new AtomicBoolean(false);
    runner = new UiTaskRunner(15);
    AiProvider slow =
        prompt -> {
          workEntered.countDown();
          try {
            Thread.sleep(5_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AiProvider.ProviderFailure(
                new uds.ai.advisor.AiError(uds.ai.advisor.AiError.CANCELLED, "interrupted"));
          } finally {
            workFinished.set(true);
          }
          String findingId =
              main.analysis().store().findings().isEmpty()
                  ? "FINDING:missing"
                  : main.analysis().store().findings().get(0).evidenceId();
          return new AiProvider.ProviderSuccess(
              "delayed advice",
              List.of(new AiProvider.ChecklistDraft("check power", List.of(findingId))));
        };
    AppSession session =
        AppSession.open(CATALOG, SAMPLES, PASSWORD, tempDir.resolve("outputs"), "ai-ui-7b");
    main = new MainViewModel(session, SAMPLES, slow, runner);
    main.selectProfile("FLIPSKY_75100_VESC");
    seedFinding();
  }

  @AfterEach
  void tearDown() {
    if (runner != null) {
      runner.close();
    }
  }

  @Test
  void delayedMock_doesNotBlockCallerThread() throws Exception {
    AiAdvisorController ai = main.ai();
    ai.setSymptomText("급정지");
    ai.refreshSuggestions();

    long started = System.nanoTime();
    ai.requestAdvice();
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

    assertTrue(elapsedMs < 1_000, "caller blocked for " + elapsedMs + "ms");
    assertTrue(ai.isRunning() || workEntered.getCount() == 0);
    assertTrue(workEntered.await(2, TimeUnit.SECONDS), "mock work never started");
    assertFalse(workFinished.get(), "5s mock finished too early — caller may have blocked");
  }

  @Test
  void cancel_preservesFindings() throws Exception {
    AiAdvisorController ai = main.ai();
    assertEquals(1, ai.findings().size());
    Finding before = ai.findings().get(0);
    String factBefore = ai.factZone();
    assertTrue(factBefore.contains("Finding"), factBefore);

    ai.setSymptomText("급정지");
    ai.refreshSuggestions();
    ai.requestAdvice();
    assertTrue(ai.runningProperty().get() || ai.isRunning());
    assertTrue(workEntered.await(2, TimeUnit.SECONDS));

    ai.cancel();

    assertEquals(1, ai.findings().size());
    assertEquals(before, ai.findings().get(0));
    assertTrue(ai.factZone().contains("Finding"), ai.factZone());
    assertTrue(
        ai.statusMessage().toLowerCase().contains("cancel"),
        ai.statusMessage());
    assertFalse(ai.runningProperty().get());
    org.junit.jupiter.api.Assertions.assertNotNull(ai.lastError());
    assertEquals(uds.ai.advisor.AiError.CANCELLED, ai.lastError().code());
    assertTrue(ai.errorBanner().contains("CANCELLED"), ai.errorBanner());
  }

  @Test
  void udsGuiApplication_hasNoPauseTransition() throws Exception {
    Path src =
        ROOT.resolve("src")
            .resolve("main")
            .resolve("java")
            .resolve("uds")
            .resolve("gui")
            .resolve("app")
            .resolve("UdsGuiApplication.java");
    String text = java.nio.file.Files.readString(src);
    assertFalse(text.contains("PauseTransition"), "PauseTransition must be removed (UDS-UX-03)");
  }

  private void seedFinding() {
    main.analysis()
        .store()
        .appendRead(
            new ReadDataResult(
                "r1",
                "s1",
                "0x1001",
                "22 10 01",
                "62 10 01 01 F4",
                "50.0 V",
                ReadStatus.ABNORMAL,
                FindingDetailCode.NONE,
                null,
                new Finding(
                    "0x1001",
                    "50.0 V",
                    ReadStatus.ABNORMAL,
                    FindingDetailCode.NONE,
                    "RANGE")));
    main.ai().refreshSuggestions();
  }
}
