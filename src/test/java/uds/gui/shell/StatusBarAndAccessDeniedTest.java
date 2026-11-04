package uds.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.state.DiagnosticAccessState;
import uds.gui.app.AppSession;
import uds.read.finding.ReadStatus;
import uds.write.parse.WriteStatus;

/** UDS-UX-02: status chips + ACCESS_DENIED flags. */
class StatusBarAndAccessDeniedTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final Path PASSWORD = ROOT.resolve("config").resolve("demo_password.txt");

  @TempDir Path tempDir;
  private MainViewModel main;
  private String demoPassword;

  @BeforeEach
  void setUp() throws Exception {
    demoPassword = Files.readString(PASSWORD).trim();
    AppSession session =
        AppSession.open(CATALOG, SAMPLES, PASSWORD, tempDir.resolve("out"), "ux02-status");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void statusChips_syncFromDefaultToUnlocked() {
    main.selectProfile("FLIPSKY_75100_VESC");
    assertTrue(main.statusProfileProperty().get().contains("FLIPSKY"));
    assertEquals("Session: DEFAULT", main.statusSessionProperty().get());
    assertEquals("Security: LOCKED", main.statusSecurityProperty().get());
    assertEquals("0x27 Redaction: ON", main.statusRedactionProperty().get());

    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.enterExtended();
    assertEquals(DiagnosticAccessState.EXTENDED_LOCKED, main.access().accessState());
    assertEquals("Session: EXTENDED", main.statusSessionProperty().get());
    assertEquals("Security: LOCKED", main.statusSecurityProperty().get());

    main.access().setPassword(demoPassword);
    main.unlock();
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());
    assertEquals("Session: EXTENDED", main.statusSessionProperty().get());
    assertEquals("Security: UNLOCKED", main.statusSecurityProperty().get());
  }

  @Test
  void lockedReadWrite_setsAccessDeniedFlag() {
    main.selectProfile("FLIPSKY_75100_VESC");
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.access().accessState());
    var read = main.read().readSelected();
    assertEquals(ReadStatus.ACCESS_DENIED, read.status());
    assertTrue(main.read().accessDeniedProperty().get());
    assertTrue(main.read().accessDeniedMessageProperty().get().contains("Unlock"));

    main.write().setInputValue("12000");
    var write = main.write().writeSelected();
    assertEquals(WriteStatus.ACCESS_DENIED, write.status());
    assertTrue(main.write().accessDeniedProperty().get());
    assertFalse(main.read().accessDeniedMessageProperty().get().isBlank());
  }
}
