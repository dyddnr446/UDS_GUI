package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppSession;

/** SessionCapabilityPolicy: Excel session/security columns gate accessible DIDs. */
class SessionAccessibleDidsTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");

  @TempDir Path tempDir;
  private MainViewModel main;
  private String demoPassword;

  @BeforeEach
  void setUp() throws Exception {
    demoPassword = uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD;
    AppSession session =
        AppSession.open(
            CATALOG, uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD, tempDir.resolve("out"), "ux-accessible");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void accessibleDids_followExcelSessionAndSecurity() {
    main.selectProfile("FLIPSKY_75100_VESC");
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.access().accessState());
    assertTrue(main.read().accessibleDidIds().isEmpty());
    assertTrue(main.write().accessibleDidIds().isEmpty());
    assertEquals(
        List.of("0x1001", "0x1002", "0x1008", "0x1201", "0x1205"),
        new ArrayList<>(main.read().runtimeDidIds()));

    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.enterExtended();
    // EXTENDED + NONE security DIDs become readable before Unlock
    List<String> afterExtended = new ArrayList<>(main.read().accessibleDidIds());
    assertTrue(afterExtended.contains("0x1008"));
    assertTrue(afterExtended.contains("0x1205"));
    assertFalse(afterExtended.contains("0x1001"));

    main.access().setPassword(demoPassword);
    main.unlock();
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());
    assertEquals(
        List.of("0x1001", "0x1002", "0x1008", "0x1201", "0x1205"),
        new ArrayList<>(main.read().accessibleDidIds()));
    // Keep prior Extended-only selection if still allowed (e.g. 0x1008).
    assertTrue(main.read().accessibleDidIds().contains(main.read().selectedDid()));
  }
}
