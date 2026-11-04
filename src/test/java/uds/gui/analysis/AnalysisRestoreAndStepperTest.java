package uds.gui.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.state.DiagnosticAccessState;
import uds.gui.app.AppSession;
import uds.gui.shell.MainViewModel;
import uds.read.finding.Finding;
import uds.read.finding.FindingDetailCode;
import uds.read.finding.ReadDataResult;
import uds.read.finding.ReadStatus;

/** UDS-UX-05: restore security banner + stepper completion. */
class AnalysisRestoreAndStepperTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final Path PASSWORD = ROOT.resolve("config").resolve("demo_password.txt");

  @TempDir Path tempDir;
  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(CATALOG, SAMPLES, PASSWORD, tempDir.resolve("out"), "ux05-restore");
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

  @Test
  void stepperFlags_updateOnDemoActions() {
    main.markStep1LogLoaded();
    assertTrue(main.step1DoneProperty().get());
    main.markStep2Unlocked();
    assertTrue(main.step2DoneProperty().get());
    main.markStep3AiOrFinding();
    main.markStep4WriteDone();
    main.markStep5SessionDone();
    assertTrue(main.step3DoneProperty().get());
    assertTrue(main.step4DoneProperty().get());
    assertTrue(main.step5DoneProperty().get());
    main.goDemoStep(3);
    assertEquals(MainViewModel.TAB_AI, main.requestedTabIndexProperty().get());
  }
}
