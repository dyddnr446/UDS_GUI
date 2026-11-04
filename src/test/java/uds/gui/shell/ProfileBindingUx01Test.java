package uds.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.gui.app.AppSession;

/** UDS-UX-01: profile change refreshes Read/Write DID·scenario lists without manual Refresh. */
class ProfileBindingUx01Test {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final Path PASSWORD = ROOT.resolve("config").resolve("demo_password.txt");

  @TempDir Path tempDir;

  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(CATALOG, SAMPLES, PASSWORD, tempDir.resolve("outputs"), "ux01-binding");
    main = new MainViewModel(session, SAMPLES);
  }

  @Test
  void selectProfile_updatesReadAndWriteListsImmediately() {
    main.selectProfile("FLIPSKY_75100_VESC");
    List<String> flipskyRead = new ArrayList<>(main.read().runtimeDidIds());
    List<String> flipskyWrite = new ArrayList<>(main.write().runtimeDidIds());
    assertEquals(
        List.of("0x1001", "0x1008", "0x1201", "0x1205"), flipskyRead);
    assertFalse(flipskyRead.isEmpty());
    assertFalse(main.read().scenarioIds().isEmpty());
    assertEquals(main.read().runtimeDidIds().get(0), main.read().selectedDid());
    assertEquals(main.read().scenarioIds().get(0), main.read().selectedScenarioId());
    assertFalse(flipskyWrite.isEmpty());
    assertEquals(main.write().runtimeDidIds().get(0), main.write().selectedDid());

    main.selectProfile("FARDRIVER_ND72530");
    List<String> ndRead = new ArrayList<>(main.read().runtimeDidIds());
    assertEquals(List.of("0x1001", "0x1002", "0x1007", "0x1101"), ndRead);
    // Previous profile-only DIDs must not linger
    assertFalse(ndRead.contains("0x1201"));
    assertFalse(ndRead.contains("0x1205"));
    assertFalse(main.write().runtimeDidIds().containsAll(flipskyWrite)
        && flipskyWrite.equals(new ArrayList<>(main.write().runtimeDidIds())));
    assertEquals(main.read().runtimeDidIds().get(0), main.read().selectedDid());
    assertTrue(main.read().scenarioIds().stream().allMatch(id -> !id.isBlank()));
  }

  @Test
  void blankProfile_clearsLists() {
    main.selectProfile("FLIPSKY_75100_VESC");
    assertFalse(main.read().runtimeDidIds().isEmpty());
    main.selectProfile("  ");
    assertTrue(main.read().runtimeDidIds().isEmpty());
    assertTrue(main.read().scenarioIds().isEmpty());
    assertTrue(main.write().runtimeDidIds().isEmpty());
  }
}
