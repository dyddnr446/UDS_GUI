package uds.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppSession;
import uds.gui.InterpreterController;
import uds.gui.InterpreterRow;
import uds.gui.MainViewModel;

/** TesterPresent keep-alive + Normal log-level filter. */
class TesterPresentKeepAliveTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");

  @TempDir Path tempDir;
  private AppSession session;
  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    session = AppSession.open(CATALOG, "uds-demo-2026", tempDir.resolve("out"), "tp-ka");
    main = new MainViewModel(session, SAMPLES);
  }

  @AfterEach
  void tearDown() {
    session.testerPresentKeepAlive().close();
  }

  @Test
  void sessionTiming_fromFixtureHex_isoP2Star() {
    SessionTiming t =
        SessionTiming.fromPositiveSessionHex("50 03 00 32 01 F4").orElseThrow();
    assertEquals(50, t.p2ServerMaxRaw());
    assertEquals(500, t.p2StarServerMaxRaw());
    assertEquals(5000L, t.p2StarPhysicalMs());
    assertEquals(2500L, t.keepAliveIntervalMs());
  }

  @Test
  void enterExtended_startsKeepAlive_andSendOnce_logs3E() throws Exception {
    main.selectProfile("FARDRIVER_ND72530");
    main.access().selectFlow("ACCESS-ND-SUCCESS");
    AccessOutcome out = main.enterExtended();
    assertEquals(DiagnosticAccessState.EXTENDED_LOCKED, out.resultingState());
    assertTrue(session.testerPresentKeepAlive().isRunning());
    assertEquals(2500L, session.testerPresentKeepAlive().timing().keepAliveIntervalMs());

    session.testerPresentKeepAlive().sendOnce();
    Path live = session.recorder().liveSessionLogPath();
    List<String> lines = Files.readAllLines(live, StandardCharsets.UTF_8);
    assertTrue(
        lines.stream().anyMatch(l -> l.contains("3E 80")),
        "live log should contain TesterPresent REQ");

    InterpreterController interp = main.interpreter();
    interp.loadLiveSession(live);
    long testerInAll =
        interp.allRows().stream().filter(InterpreterRow::isTesterPresent).count();
    assertTrue(testerInAll >= 1);

    interp.setFilterMode(InterpreterController.FilterMode.NORMAL);
    assertEquals(
        0,
        interp.filteredRows().stream().filter(InterpreterRow::isTesterPresent).count(),
        "Normal hides TesterPresent");

    interp.setFilterMode(InterpreterController.FilterMode.VERBOSE);
    assertTrue(
        interp.filteredRows().stream().anyMatch(InterpreterRow::isTesterPresent),
        "Verbose shows TesterPresent");

    main.selectProfile("FARDRIVER_ND72530");
    assertFalse(session.testerPresentKeepAlive().isRunning());
  }
}
