package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.gui.app.AppConfig;
import uds.gui.app.AppSession;
import uds.gui.MainViewModel;

class DiagnosticSessionChoiceTest {

  private static final Path CATALOG =
      Path.of("기준정보/uds_system_catalog.xlsx").toAbsolutePath().normalize();

  @TempDir Path tempDir;

  private MainViewModel main;

  @BeforeEach
  void setUp() throws Exception {
    AppSession session =
        AppSession.open(
            CATALOG, AppConfig.DEFAULT_DEMO_PASSWORD, tempDir.resolve("out"), "session-choice");
    main = new MainViewModel(session, tempDir);
  }

  @Test
  void sessionsFollowCatalogSupportedSessionsPerEcu() {
    main.selectProfile("FLIPSKY_75100_VESC");
    List<DiagnosticSessionChoice> vesc = main.sessionsForSelectedProfile();
    assertEquals(2, vesc.size());
    assertEquals("DEFAULT", vesc.get(0).key());
    assertEquals("EXTENDED", vesc.get(1).key());
    assertTrue(vesc.get(1).displayName().toLowerCase().contains("extended"));

    main.selectProfile("FARDRIVER_ND72530");
    List<DiagnosticSessionChoice> nd = main.sessionsForSelectedProfile();
    assertEquals(2, nd.size());
    assertTrue(nd.stream().anyMatch(DiagnosticSessionChoice::isDefault));
    assertTrue(nd.stream().anyMatch(DiagnosticSessionChoice::isExtended));
    assertFalse(nd.stream().anyMatch(s -> "PROGRAMMING".equals(s.key())));
  }
}
