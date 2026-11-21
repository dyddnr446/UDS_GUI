package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.DiagnosticAccessState;
import uds.gui.app.AppSession;
import uds.gui.MainViewModel;

/** DID 전환 시 fixture 시나리오가 따라가지 않는 sticky-bug 회귀 방지. */
class ReadScenarioResolveTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");

  @TempDir Path tempDir;
  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(
            CATALOG,
            uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD,
            tempDir.resolve("out"),
            "read-scenario-resolve");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void switchingDid_resolvesMatchingScenarioPerDid() {
    main.selectProfile("FARDRIVER_ND72530");
    main.access().selectFlow("ACCESS-ND-SUCCESS");
    main.access().setPassword(uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD);
    main.enterExtended();
    main.unlock();
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());

    main.read().selectDid("0x1001");
    var r1 = main.read().readSelected();
    assertNotNull(main.read().selectedScenarioId());
    String s1 = main.read().selectedScenarioId();
    assertEquals("RD-ND-001", s1);
    assertEquals(uds.read.ReadStatus.NORMAL, r1.status());

    main.read().selectDid("0x1004");
    var r2 = main.read().readSelected();
    String s2 = main.read().selectedScenarioId();
    assertEquals("RD-ND-008", s2);
    assertNotEquals(s1, s2);
    assertEquals(uds.read.ReadStatus.ABNORMAL, r2.status());
  }
}
