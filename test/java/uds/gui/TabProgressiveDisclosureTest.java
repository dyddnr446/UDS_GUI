package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppSession;

/** Progressive disclosure: Read needs EXTENDED; Write/AI need UNLOCKED. */
class TabProgressiveDisclosureTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");

  @TempDir Path tempDir;
  private MainViewModel main;
  private String demoPassword;

  @BeforeEach
  void setUp() throws Exception {
    demoPassword = uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD;
    AppSession session =
        AppSession.open(
            CATALOG, demoPassword, tempDir.resolve("out"), "ux-tab-gate");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void tabs_gateBySessionSecurityState() {
    assertFalse(main.readTabEnabledProperty().get());
    assertFalse(main.writeTabEnabledProperty().get());
    assertFalse(main.aiTabEnabledProperty().get());
    assertEquals("Interface: VIRTUAL", main.statusInterfaceProperty().get());

    main.selectProfile("FLIPSKY_75100_VESC");
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.access().accessState());
    assertFalse(main.readTabEnabledProperty().get());
    assertFalse(main.writeTabEnabledProperty().get());

    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.enterExtended();
    assertEquals(DiagnosticAccessState.EXTENDED_LOCKED, main.access().accessState());
    assertTrue(main.readTabEnabledProperty().get());
    assertFalse(main.writeTabEnabledProperty().get());
    assertFalse(main.aiTabEnabledProperty().get());

    main.access().setPassword(demoPassword);
    main.unlock();
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());
    assertTrue(main.readTabEnabledProperty().get());
    assertTrue(main.writeTabEnabledProperty().get());
    assertTrue(main.aiTabEnabledProperty().get());
  }

  @Test
  void tabConstants_matchProductionOrder() {
    assertEquals(0, MainViewModel.TAB_INTERPRETER);
    assertEquals(1, MainViewModel.TAB_READ);
    assertEquals(2, MainViewModel.TAB_WRITE);
    assertEquals(3, MainViewModel.TAB_AI);
    assertEquals(4, MainViewModel.TAB_ANALYSIS);
  }
}
