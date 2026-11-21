package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppSession;
import uds.read.ReadStatus;
import uds.write.WriteStatus;

/** UDS-UX-02: status chips + ACCESS_DENIED flags. */
class StatusBarAndAccessDeniedTest {

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
        AppSession.open(CATALOG, uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD, tempDir.resolve("out"), "ux02-status");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void statusChips_syncFromDefaultToUnlocked() {
    main.selectProfile("FLIPSKY_75100_VESC");
    assertTrue(main.statusProfileProperty().get().contains("FLIPSKY"));
    assertEquals("Session: DEFAULT", main.statusSessionProperty().get());
    assertEquals("Security: LOCKED", main.statusSecurityProperty().get());
    assertEquals("0x27 Redaction: ON", main.statusRedactionProperty().get());
    assertTrue(main.needsUnlockPasswordPrompt());

    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    var unlocked = main.applyExtendedWithPassword(demoPassword);
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, unlocked.resultingState());
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());
    assertEquals("Session: EXTENDED", main.statusSessionProperty().get());
    assertEquals("Security: UNLOCKED", main.statusSecurityProperty().get());
    assertFalse(main.needsUnlockPasswordPrompt());
    assertEquals(demoPassword, main.rememberedUnlockPassword());
  }

  @Test
  void applyExtended_wrongPassword_rollsBackToDefault() {
    main.selectProfile("FARDRIVER_ND72530");
    main.access().selectFlow("ACCESS-ND-SUCCESS");
    var failed = main.applyExtendedWithPassword("definitely-wrong-password");
    assertEquals("INVALID_KEY", failed.errorCode());
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, failed.resultingState());
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.access().accessState());
    assertEquals("Session: DEFAULT", main.statusSessionProperty().get());
    assertEquals("Security: LOCKED", main.statusSecurityProperty().get());
    assertTrue(main.needsUnlockPasswordPrompt());
  }

  @Test
  void applyDefault_logsSessionControl10_01() throws Exception {
    main.selectProfile("FARDRIVER_ND72530");
    main.access().selectFlow("ACCESS-ND-SUCCESS");
    assertEquals(
        DiagnosticAccessState.EXTENDED_UNLOCKED,
        main.applyExtendedWithPassword(demoPassword).resultingState());

    var choice =
        main.sessionsForSelectedProfile().stream()
            .filter(uds.gui.DiagnosticSessionChoice::isDefault)
            .findFirst()
            .orElseThrow();
    var back = main.applySessionControl(choice);
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, back.resultingState());
    assertEquals("Session: DEFAULT", main.statusSessionProperty().get());

    String live =
        Files.readString(
            main.session().recorder().liveSessionLogPath(), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(live.contains("10 01"), "Default Apply must log 10 01 request");
    assertTrue(live.contains("50 01") || live.contains("5001"), "Default Apply must log 50 01 response");
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
