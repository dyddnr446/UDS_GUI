package uds.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.fixture.port.FileDiagnosticExchangePort;
import uds.gui.app.AppSession;
import uds.gui.interpreter.InterpreterController;

/** P1-3 / P1-4: preset keeps Port identity; interpreter filters. */
class DemoPresetAndInterpreterFilterTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final Path PASSWORD = ROOT.resolve("config").resolve("demo_password.txt");
  private static final Path DEMO_LOG = SAMPLES.resolve("demo_session.log");

  @TempDir Path tempDir;
  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(CATALOG, SAMPLES, PASSWORD, tempDir.resolve("out"), "ux06-preset");
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
    int all = interp.filteredDisplayLines().size();
    interp.setFilterMode(InterpreterController.FilterMode.ERRORS_ONLY);
    int errors = interp.filteredDisplayLines().size();
    interp.setFilterMode(InterpreterController.FilterMode.REDACTED_ONLY);
    int redacted = interp.filteredDisplayLines().size();
    assertEquals(interp.errorLineCountProperty().get(), errors);
    assertEquals(interp.redactedCountProperty().get(), redacted);
    assertTrue(all >= errors);
    assertTrue(all >= redacted);
    assertTrue(interp.summaryChip().contains("정상 프레임"));
  }
}
