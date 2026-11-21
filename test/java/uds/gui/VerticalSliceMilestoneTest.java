package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppSession;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;

class VerticalSliceMilestoneTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");

  @TempDir Path tempDir;

  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(CATALOG, uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD, tempDir.resolve("outputs"), "milestone-4b");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void verticalSlice_flipskyUnlockReadThenNdReset() throws Exception {
    // 1) Catalog loaded → FLIPSKY select
    assertTrue(main.access().profileIds().contains("FLIPSKY_75100_VESC"));
    main.selectProfile("FLIPSKY_75100_VESC");
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.access().accessState());

    // 2) Read DID list
    assertEquals(
        List.of("0x1001", "0x1002", "0x1008", "0x1201", "0x1205"),
            main.read().runtimeDidIds());

    // 3) Read before auth → ACCESS_DENIED, JSONL 0
    main.read().selectScenario("RD-VESC-001");
    main.read().selectDid("0x1001");
    ReadDataResult denied = main.read().readSelected();
    assertEquals(ReadStatus.ACCESS_DENIED, denied.status());
    Path sessionDir = main.session().sessionDir();
    assertFalse(Files.exists(sessionDir.resolve("requests.jsonl")));
    assertFalse(Files.exists(sessionDir.resolve("responses.jsonl")));

    // 4) ACCESS-VESC-SUCCESS → EXTENDED_UNLOCKED, password cleared
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.access().setPassword("uds-demo-2026");
    assertEquals("uds-demo-2026", main.access().password());
    main.access().runAccessSuccessFlow("uds-demo-2026");
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());
    assertEquals("", main.access().password());

    // 5) RD-VESC-001 → 50.0 V / ABNORMAL
    ReadDataResult read = main.read().read("RD-VESC-001", "0x1001");
    assertEquals("50.0 V", read.displayValue());
    assertEquals(ReadStatus.ABNORMAL, read.status());

    // 6) Switch to ND → DEFAULT_LOCKED + DID list replaced
    main.selectProfile("FARDRIVER_ND72530");
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.access().accessState());
    assertEquals(
        List.of("0x1001", "0x1002", "0x1004", "0x1005", "0x1007", "0x1101"),
            main.read().runtimeDidIds());
  }
}
