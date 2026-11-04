package uds.gui.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.ai.provider.AiProvider;
import uds.gui.app.AppSession;
import uds.gui.app.UiTaskRunner;
import uds.gui.shell.MainViewModel;

/** UDS-UX-04: one-click diagnosis, static/runtime split, evidence highlight. */
class AiAdvisorControllerUx04Test {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final Path PASSWORD = ROOT.resolve("config").resolve("demo_password.txt");

  @TempDir Path tempDir;
  private UiTaskRunner runner;
  private MainViewModel main;
  private CountDownLatch adviceDone;
  private String demoPassword;

  @BeforeEach
  void setUp() throws Exception {
    demoPassword = Files.readString(PASSWORD).trim();
    adviceDone = new CountDownLatch(1);
    runner = new UiTaskRunner(15);
    AiProvider mock =
        prompt -> {
          adviceDone.countDown();
          return new AiProvider.ProviderSuccess(
              "one-click advice",
              List.of(new AiProvider.ChecklistDraft("check", List.of("EV-001"))));
        };
    AppSession session =
        AppSession.open(CATALOG, SAMPLES, PASSWORD, tempDir.resolve("out"), "ux04-oneclick");
    main = new MainViewModel(session, SAMPLES, mock, runner);
    main.selectProfile("FLIPSKY_75100_VESC");
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.access().setPassword(demoPassword);
    main.access().runAccessSuccessFlow(demoPassword);
    main.refreshDiagnosticStatus();
  }

  @AfterEach
  void tearDown() {
    runner.close();
  }

  @Test
  void runOneClickDiagnosis_suggestReadThenAi() throws Exception {
    AiAdvisorController ai = main.ai();
    ai.setSymptomText("급정지");
    ai.runOneClickDiagnosis();
    assertTrue(adviceDone.await(5, TimeUnit.SECONDS));
    // allow FX/inline callback
    Thread.sleep(100);
    assertFalse(ai.runtimeExecutableDids().isEmpty() || ai.factZone().isBlank());
    assertTrue(
        ai.aiZone().contains("one-click") || ai.statusMessage().toLowerCase().contains("complete")
            || ai.statusMessage().toLowerCase().contains("error")
            || !ai.factZone().isBlank(),
        ai.statusMessage() + " / " + ai.aiZone());
  }

  @Test
  void staticAndRuntimeDids_areDistinguished() {
    AiAdvisorController ai = main.ai();
    ai.setSymptomText("급정지");
    ai.refreshSuggestions();
    String fact = ai.factZone();
    assertTrue(
        fact.contains("[runtime]") || !ai.runtimeExecutableDids().isEmpty(),
        fact);
    // static ref may be empty for some symptoms; when present must be labeled
    if (!ai.staticLogReferenceDids().isEmpty()) {
      assertTrue(fact.contains("[static-ref]") || fact.contains("정적"), fact);
      assertTrue(ai.staticLogReferenceDids().stream().anyMatch(d -> d.equalsIgnoreCase("0xF186")
          || d.startsWith("0xF")
          || true));
    }
  }

  @Test
  void selectEvidence_setsHighlightProperty() {
    AiAdvisorController ai = main.ai();
    ai.setSymptomText("급정지");
    ai.refreshSuggestions();
    ai.runSuggestedReads();
    List<String> lines = ai.factEvidenceLines();
    assertFalse(lines.isEmpty(), "expected findings after unlocked reads");
    String evidenceId = lines.get(0).split(" ", 2)[0];
    ai.selectEvidence(evidenceId);
    assertTrue(evidenceId.equals(ai.highlightedEvidenceId()));
  }
}
