package uds.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppConfig;
import uds.gui.app.AppSession;
import uds.gui.MainViewModel;

/** Excel OemExtensions session/security columns drive Guard allow/deny. */
class DiagnosticAccessGuardTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");

  @TempDir Path tempDir;
  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(CATALOG, AppConfig.DEFAULT_DEMO_PASSWORD, tempDir.resolve("out"), "guard-excel");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void flipsky_extendedLocked_allowsNoneSecurityDidsOnly() {
    main.selectProfile("FLIPSKY_75100_VESC");
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.enterExtended();
    assertEquals(DiagnosticAccessState.EXTENDED_LOCKED, main.access().accessState());

    List<String> readable = new ArrayList<>(main.read().accessibleDidIds());
    assertTrue(readable.contains("0x1008"), "duty EXTENDED+NONE → readable without unlock");
    assertTrue(readable.contains("0x1205"), "controllerId EXTENDED+NONE");
    assertTrue(!readable.contains("0x1001"), "voltage still SECURITY_ACCESS");
  }

  @Test
  void flipsky_unlocked_allowsSecurityAccessDids() {
    main.selectProfile("FLIPSKY_75100_VESC");
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.access().setPassword(AppConfig.DEFAULT_DEMO_PASSWORD);
    main.enterExtended();
    main.unlock();
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());

    List<String> readable = new ArrayList<>(main.read().accessibleDidIds());
    assertTrue(readable.contains("0x1001"));
    assertTrue(readable.contains("0x1008"));
    assertTrue(readable.contains("0x1205"));
  }

  @Test
  void nd_extendedLocked_allowsFaultCodeWithoutUnlock() {
    main.selectProfile("FARDRIVER_ND72530");
    main.access().selectFlow("ACCESS-ND-SUCCESS");
    main.enterExtended();
    assertEquals(DiagnosticAccessState.EXTENDED_LOCKED, main.access().accessState());
    List<String> readable = new ArrayList<>(main.read().accessibleDidIds());
    assertTrue(readable.contains("0x1007"), "fault EXTENDED+NONE");
    assertTrue(!readable.contains("0x1001"));
  }

  @Test
  void liveSessionLog_growsAfterExtendedAndRead() throws Exception {
    main.selectProfile("FLIPSKY_75100_VESC");
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.enterExtended();
    Path live = main.session().recorder().liveSessionLogPath();
    assertTrue(java.nio.file.Files.isRegularFile(live));
    long afterSession = java.nio.file.Files.size(live);

    main.access().setPassword(AppConfig.DEFAULT_DEMO_PASSWORD);
    main.unlock();
    main.read().selectDid("0x1008");
    main.readSelectedAndRecord();
    main.refreshInterpreterLive();

    assertTrue(java.nio.file.Files.size(live) > afterSession);
    assertTrue(main.interpreter().okFrameCountProperty().get() >= 2);

    main.clearInterpreterLive();
    assertTrue(main.interpreter().okFrameCountProperty().get() == 0);
  }
}
