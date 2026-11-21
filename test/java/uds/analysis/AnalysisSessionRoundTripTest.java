package uds.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;
import uds.analysis.AccessEvidence;
import uds.analysis.AnalysisReportWriter;
import uds.analysis.AnalysisReportWriter.RestoreResult;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.gui.app.AppSession;
import uds.gui.MainViewModel;
import uds.read.Finding;
import uds.read.FindingDetailCode;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;
import uds.read.ReadDataService;
import uds.write.WriteResult;
import uds.write.WriteStatus;

class AnalysisSessionRoundTripTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("test").resolve("fixtures");
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");

  @TempDir Path tempDir;

  private UdsCatalog catalog;
  private AppSession appSession;
  private MainViewModel main;
  private AnalysisReportWriter writer;

  @BeforeEach
  void setUp() throws Exception {
    catalog = CatalogLoader.load(CATALOG);
    appSession =
        AppSession.open(CATALOG, uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD, tempDir.resolve("outputs"), "analysis-step6");
    main = new MainViewModel(appSession, SAMPLES);
    writer = new AnalysisReportWriter();
  }

  @Test
  void roundTrip_preservesEvidenceIds_andLocksOnRestore() throws Exception {
    AnalysisSessionStore store =
        new AnalysisSessionStore(appSession.analysisSessionId(), catalog.catalogVersion());
    store.setProfileId("FLIPSKY_75100_VESC");

    ReadDataResult read =
        new ReadDataResult(
            "req-1",
            "rsp-1",
            "0x1001",
            "22 10 01",
            "62 10 01 01 F4",
            "50.0 V",
            ReadStatus.ABNORMAL,
            FindingDetailCode.NONE,
            null,
            new Finding("0x1001", "50.0 V", ReadStatus.ABNORMAL, FindingDetailCode.NONE, "RANGE"));
    String readId = store.appendRead(read);
    String writeId =
        store.appendWrite(
            new WriteResult(
                "req-w",
                "rsp-w",
                "0x2002",
                "12000",
                "00 00 2E E0",
                "2E 20 02 00 00 2E E0",
                "6E 20 02",
                WriteStatus.ACCEPTED,
                "ACCEPTED"));
    String accessId = store.ids().access();
    store.appendAccess(
        new AccessEvidence(
            accessId,
            store.analysisSessionId(),
            "SEND_KEY",
            "POSITIVE",
            null,
            "EXTENDED_UNLOCKED",
            true));

    assertTrue(readId.startsWith("READ:"));
    assertTrue(store.findings().get(0).evidenceId().startsWith("FINDING:"));
    assertTrue(writeId.startsWith("WRITE:"));
    assertTrue(accessId.startsWith("ACCESS:"));

    Path sessionDir = appSession.sessionDir();
    writer.save(store, sessionDir);

    // Simulate unlocked runtime then restore
    ActiveDiagnosticContext context = appSession.context();
    context.selectProfile("FLIPSKY_75100_VESC", catalog.catalogVersion());
    context.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);

    RestoreResult restored =
        writer.restore(sessionDir, catalog.catalogVersion(), context);
    assertNotNull(restored.store());
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, context.snapshot().accessState());

    assertEquals(1, restored.store().readResults().size());
    assertEquals(readId, restored.store().readResults().get(0).evidenceId());
    assertEquals(writeId, restored.store().writeResults().get(0).evidenceId());
    assertEquals(accessId, restored.store().accessEvidences().get(0).evidenceId());
    assertEquals("EXTENDED_UNLOCKED", restored.store().accessEvidences().get(0).resultingState());

    // Restored lock → read denied
    ReadDataService readService = appSession.readDataService();
    readService.resetPortCallCount();
    ReadDataResult denied = readService.read("0x1001");
    assertEquals(ReadStatus.ACCESS_DENIED, denied.status());
    assertEquals(0, readService.portCallCount());
  }

  @Test
  void catalogVersionMismatch_warnsButDoesNotCrash() throws Exception {
    AnalysisSessionStore store =
        new AnalysisSessionStore(appSession.analysisSessionId(), catalog.catalogVersion());
    store.setProfileId("FARDRIVER_ND72530");
    store.appendRead(
        new ReadDataResult(
            null,
            null,
            "0x1001",
            null,
            null,
            "72.5 V",
            ReadStatus.NORMAL,
            FindingDetailCode.NONE,
            null,
            new Finding("0x1001", "72.5 V", ReadStatus.NORMAL, FindingDetailCode.NONE, "RANGE")));
    Path sessionDir = appSession.sessionDir();
    writer.save(store, sessionDir);

    Path meta = sessionDir.resolve("analysis-session.json");
    String text = Files.readString(meta, StandardCharsets.UTF_8);
    text = text.replace(catalog.catalogVersion(), "deadbeef".repeat(8));
    Files.writeString(meta, text, StandardCharsets.UTF_8);

    RestoreResult restored =
        writer.restore(sessionDir, catalog.catalogVersion(), appSession.context());
    assertNotNull(restored.store());
    assertTrue(
        restored.warnings().stream().anyMatch(w -> w.contains("catalogVersion mismatch")),
        restored.warnings().toString());
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, appSession.context().snapshot().accessState());
  }

  @Test
  void outputs_containNoSensitiveSecrets() throws Exception {
    main.selectProfile("FLIPSKY_75100_VESC");
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.access().runAccessSuccessFlow("uds-demo-2026");
    main.read().read("RD-VESC-001", "0x1001");

    AnalysisSessionStore store = main.analysis().store();
    store.setProfileId("FLIPSKY_75100_VESC");
    store.appendAccess(
        new AccessEvidence(
            store.ids().access(),
            store.analysisSessionId(),
            "SEND_KEY",
            "POSITIVE",
            null,
            "EXTENDED_UNLOCKED",
            true));
    main.analysis().save();

    Path sessionDir = main.session().sessionDir();
    StringBuilder all = new StringBuilder();
    try (Stream<Path> paths = Files.walk(sessionDir)) {
      for (Path p : paths.filter(Files::isRegularFile).toList()) {
        all.append(Files.readString(p, StandardCharsets.UTF_8));
      }
    }
    String compact = all.toString().replace(" ", "").toUpperCase(Locale.ROOT);
    assertFalse(compact.contains("AABBCCDD"));
    assertFalse(compact.contains("D3AC3F47"));
    assertFalse(all.toString().contains("uds-demo-2026"));
  }
}
