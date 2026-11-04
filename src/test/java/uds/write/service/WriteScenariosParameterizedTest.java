package uds.write.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uds.access.facade.DiagnosticAccessFacade;
import uds.access.guard.DiagnosticAccessGuard;
import uds.access.service.SecurityAccessService;
import uds.access.service.SessionControlService;
import uds.access.state.ActiveDiagnosticContext;
import uds.access.state.DiagnosticAccessState;
import uds.analysis.record.SafeExchangeRecorder;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.exchange.policy.ExchangeCoordinator;
import uds.exchange.policy.ExchangePolicy;
import uds.exchange.recording.RecordingDiagnosticExchangePort;
import uds.fixture.config.FixtureRunConfig;
import uds.fixture.config.SystemClock;
import uds.fixture.port.FileDiagnosticExchangePort;
import uds.fixture.repo.FixtureRepository;
import uds.fixture.repo.WriteScenario;
import uds.fixture.sim.StatefulFixtureEcuSimulator;
import uds.gui.write.WriteDataViewModel;
import uds.write.parse.WriteResult;
import uds.write.parse.WriteStatus;

class WriteScenariosParameterizedTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CONFIG = ROOT.resolve("config");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final String SESSION = "write-step5";

  @TempDir Path tempDir;

  private UdsCatalog catalog;
  private FixtureRepository repository;
  private FileDiagnosticExchangePort filePort;
  private ActiveDiagnosticContext context;
  private DiagnosticAccessFacade facade;
  private WriteDataService writeService;
  private WriteDataViewModel writeVm;
  private SafeExchangeRecorder recorder;
  private Path sessionDir;

  @BeforeEach
  void setUp() throws Exception {
    catalog = CatalogLoader.load(CATALOG);
    repository =
        new FixtureRepository(
            SAMPLES.resolve("access_scenarios.csv"),
            SAMPLES.resolve("read_data_scenarios.csv"),
            SAMPLES.resolve("write_data_scenarios.csv"));
    StatefulFixtureEcuSimulator simulator =
        new StatefulFixtureEcuSimulator(
            repository, CONFIG.resolve("demo_password.txt"), new SystemClock());
    filePort = new FileDiagnosticExchangePort(simulator);
    sessionDir = tempDir.resolve("outputs").resolve(SESSION);
    recorder = new SafeExchangeRecorder(sessionDir, SESSION);
    RecordingDiagnosticExchangePort recording =
        new RecordingDiagnosticExchangePort(filePort, recorder);
    ExchangeCoordinator coordinator = new ExchangeCoordinator(recording, new ExchangePolicy());
    context = new ActiveDiagnosticContext();
    DiagnosticAccessGuard guard = new DiagnosticAccessGuard(context, catalog);
    facade =
        new DiagnosticAccessFacade(
            context,
            new SessionControlService(context, coordinator, recorder, SESSION),
            new SecurityAccessService(context, coordinator, recorder, SESSION),
            filePort,
            guard);
    writeService = new WriteDataService(context, guard, catalog, coordinator, SESSION);
    writeVm = new WriteDataViewModel(facade, writeService, catalog, repository);
  }

  static Stream<WriteScenario> writeScenarios() {
    FixtureRepository repo =
        new FixtureRepository(
            SAMPLES.resolve("access_scenarios.csv"),
            SAMPLES.resolve("read_data_scenarios.csv"),
            SAMPLES.resolve("write_data_scenarios.csv"));
    return repo.writeScenarios().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("writeScenarios")
  void csvRow_matchesRequestHexAndStatus(WriteScenario scenario) {
    facade.selectProfile(scenario.controllerProfile(), catalog.catalogVersion());
    facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
    filePort.setFixtureRunConfig(new FixtureRunConfig(scenario.scenarioId()));
    writeService.resetPortCallCount();

    WriteResult result = writeService.write(scenario.did(), scenario.inputValue());

    assertEquals(
        WriteStatus.valueOf(scenario.expectedStatus()),
        result.status(),
        scenario.scenarioId() + " status");
    String expectedReq =
        scenario.requestHex() == null ? "" : scenario.requestHex().trim().toUpperCase(Locale.ROOT);
    if (!expectedReq.isEmpty()) {
      assertEquals(
          expectedReq,
          result.requestHex().trim().toUpperCase(Locale.ROOT),
          scenario.scenarioId() + " request_hex");
    }
    if ("WD-ERR-004".equals(scenario.scenarioId()) || "WD-ERR-005".equals(scenario.scenarioId())) {
      assertEquals(0, writeService.portCallCount());
    }
  }

  @Test
  void wdErr004005_noPort_noJsonl() throws Exception {
    facade.selectProfile("FARDRIVER_ND72530", catalog.catalogVersion());
    facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
    writeService.resetPortCallCount();
    filePort.setFixtureRunConfig(new FixtureRunConfig("WD-ERR-004"));
    WriteResult range = writeService.write("0x2001", "2");
    assertEquals(WriteStatus.VALIDATION_ERROR_RANGE, range.status());
    assertEquals(0, writeService.portCallCount());
    assertFalse(Files.exists(sessionDir.resolve("requests.jsonl")));

    facade.selectProfile("FLIPSKY_75100_VESC", catalog.catalogVersion());
    facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
    writeService.resetPortCallCount();
    filePort.setFixtureRunConfig(new FixtureRunConfig("WD-ERR-005"));
    WriteResult type = writeService.write("0x2002", "not-a-number");
    assertEquals(WriteStatus.VALIDATION_ERROR_TYPE, type.status());
    assertEquals(0, writeService.portCallCount());
    assertFalse(Files.exists(sessionDir.resolve("responses.jsonl")));
  }

  @Test
  void lastAccepted_preservedAfterRejected() {
    writeVm.onProfileChanged("FARDRIVER_ND72530");
    facade.selectProfile("FARDRIVER_ND72530", catalog.catalogVersion());
    facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);

    WriteResult accepted = writeVm.write("WD-ND-001", "0x2001", "1");
    assertEquals(WriteStatus.ACCEPTED, accepted.status());
    assertNotNull(writeVm.lastAccepted());
    assertEquals(WriteStatus.ACCEPTED, writeVm.lastAccepted().status());
    assertEquals("1", writeVm.lastAccepted().inputValue());

    WriteResult rejected = writeVm.write("WD-ERR-001", "0x2002", "3000");
    assertEquals(WriteStatus.REJECTED, rejected.status());
    assertEquals(WriteStatus.ACCEPTED, writeVm.lastAccepted().status());
    assertEquals("0x2001", writeVm.lastAccepted().did());
    assertEquals("1", writeVm.lastAccepted().inputValue());
  }

  @Test
  void reportTable_allTwelveRows() {
    List<String> lines = new ArrayList<>();
    lines.add(
        String.format(
            "%-12s %-24s %-24s %-12s %s",
            "scenario", "reqExp", "reqAct", "statusExp", "statusAct"));
    for (WriteScenario scenario : repository.writeScenarios()) {
      facade.selectProfile(scenario.controllerProfile(), catalog.catalogVersion());
      facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
      filePort.setFixtureRunConfig(new FixtureRunConfig(scenario.scenarioId()));
      WriteResult result = writeService.write(scenario.did(), scenario.inputValue());
      String reqExp = scenario.requestHex() == null ? "" : scenario.requestHex();
      lines.add(
          String.format(
              "%-12s %-24s %-24s %-12s %s",
              scenario.scenarioId(),
              reqExp,
              result.requestHex(),
              scenario.expectedStatus(),
              result.status()));
      assertEquals(WriteStatus.valueOf(scenario.expectedStatus()), result.status());
    }
    assertEquals(12, repository.writeScenarios().size());
    System.out.println(String.join(System.lineSeparator(), lines));
  }
}
