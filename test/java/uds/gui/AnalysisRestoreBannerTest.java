package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppSession;
import uds.gui.MainViewModel;
import uds.read.Finding;
import uds.read.FindingDetailCode;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;

/** Analysis restore keeps security locked and shows banner (no stepper). */
class AnalysisRestoreBannerTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");

  @TempDir Path tempDir;
  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(
            CATALOG,
            uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD,
            tempDir.resolve("out"),
            "ux05-restore");
    main = new MainViewModel(session, SAMPLES);
    main.selectProfile("FLIPSKY_75100_VESC");
  }

  @Test
  void restore_showsBannerAndStaysLocked() {
    main.analysis()
        .store()
        .appendRead(
            new ReadDataResult(
                "r1",
                "s1",
                "0x1001",
                "22 10 01",
                "62 10 01 01 F4",
                "50.0 V",
                ReadStatus.ABNORMAL,
                FindingDetailCode.NONE,
                null,
                new Finding(
                    "0x1001",
                    "50.0 V",
                    ReadStatus.ABNORMAL,
                    FindingDetailCode.NONE,
                    "RANGE")));
    main.analysis().save();
    var result = main.analysis().restoreFrom(main.session().sessionDir());
    assertTrue(result.store() != null || !main.analysis().messages().isEmpty());
    assertTrue(main.analysis().securityBannerVisible());
    assertTrue(
        main.analysis().securityBannerTextProperty().get().contains("Unlock 권한은 복원되지 않습니다"));
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.analysis().accessState());
    assertTrue(main.analysis().restoreSummary().contains("Findings="));
  }
}
