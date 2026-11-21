package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.fixture.FileDiagnosticExchangePort;
import uds.gui.app.AppSession;
import uds.gui.InterpreterController;

/** P1-3 / P1-4: preset keeps Port identity; interpreter filters. */
class DemoPresetAndInterpreterFilterTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");
  private static final Path DEMO_LOG = SAMPLES.resolve("demo_session.log");

  @TempDir Path tempDir;
  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(CATALOG, uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD, tempDir.resolve("out"), "ux06-preset");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void applyPreset_doesNotReplaceFilePortInstance() {
    FileDiagnosticExchangePort before = main.session().filePort();
    main.applyDemoPresetA();
    assertSame(before, main.session().filePort());
    main.applyDemoPresetB();
    assertSame(before, main.session().filePort());
    main.applyDemoPresetC();
    assertSame(before, main.session().filePort());
    assertEquals("FLIPSKY_75100_VESC", main.access().selectedProfileId());
  }

  @Test
  void interpreterFilters_matchCounts() {
    InterpreterController interp = main.interpreter();
    interp.loadLog(DEMO_LOG);
    int normal = interp.filteredDisplayLines().size();
    interp.setFilterMode(InterpreterController.FilterMode.VERBOSE);
    int verbose = interp.filteredDisplayLines().size();
    interp.setFilterMode(InterpreterController.FilterMode.ERRORS_ONLY);
    int errors = interp.filteredDisplayLines().size();
    assertEquals(interp.errorLineCountProperty().get(), errors);
    assertTrue(errors >= 2, "demo log includes NRC negative frames");
    assertTrue(verbose >= normal, "Verbose includes TesterPresent hidden by Normal");
    assertTrue(verbose >= errors);
    assertTrue(interp.redactedCountProperty().get() >= 1, "demo log has redacted 0x27 frames");
    assertTrue(interp.summaryChip().contains("정상 프레임"));
    assertTrue(interp.summaryChip().contains("오류"));
  }
}
