package uds.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uds.access.DiagnosticAccessFacade;
import uds.access.DiagnosticAccessGuard;
import uds.access.SecurityAccessService;
import uds.access.SessionControlService;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;
import uds.analysis.SafeExchangeRecorder;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.exchange.ExchangeCoordinator;
import uds.exchange.ExchangePolicy;
import uds.exchange.RecordingDiagnosticExchangePort;
import uds.fixture.FixtureRunConfig;
import uds.fixture.SystemClock;
import uds.fixture.FileDiagnosticExchangePort;
import uds.fixture.FixtureRepository;
import uds.fixture.ReadScenario;
import uds.fixture.StatefulFixtureEcuSimulator;
import uds.read.FindingDetailCode;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;

class ReadScenariosParameterizedTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");
  private static final String SESSION = "read-step4a";

  @TempDir Path tempDir;

  private UdsCatalog catalog;
  private FixtureRepository repository;
  private FileDiagnosticExchangePort filePort;
  private ActiveDiagnosticContext context;
  private DiagnosticAccessFacade facade;
  private ReadDataService readService;
  private SafeExchangeRecorder recorder;
  private Path sessionDir;

  @BeforeEach
  void setUp() throws Exception {
    catalog = CatalogLoader.load(CATALOG);
    repository = FixtureRepository.load(FIXTURE);
    StatefulFixtureEcuSimulator simulator =
        new StatefulFixtureEcuSimulator(
            repository, uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD, new SystemClock());
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
    readService = new ReadDataService(context, guard, catalog, coordinator, SESSION);
  }

  static Stream<ReadScenario> readScenarios() throws Exception {
    return FixtureRepository.load(FIXTURE).readScenarios().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("readScenarios")
  void csvRow_matchesExpectedValueAndStatus(ReadScenario scenario) {
    facade.selectProfile(scenario.controllerProfile(), catalog.catalogVersion());
    facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
    filePort.setFixtureRunConfig(new FixtureRunConfig(scenario.scenarioId()));

    ReadDataResult result = readService.read(scenario.did());

    assertEquals(
        ReadStatus.valueOf(scenario.expectedStatus()),
        result.status(),
        scenario.scenarioId() + " status");
    String expectedValue =
        scenario.expectedValue() == null ? "" : scenario.expectedValue().trim();
    if (!expectedValue.isEmpty()) {
      assertEquals(expectedValue, result.displayValue(), scenario.scenarioId() + " value");
    }
  }

  @Test
  void uc04_staticVinFrame_rangeNotApplicable() {
    String hex = "62 F1 90 57 58 59 31 32 33 34 35 36 37 38 39 41 42 43";
    ReadDataResult result = readService.parseFrame(hex, "0xF190");
    assertEquals("WXY123456789ABC", result.displayValue());
    assertEquals(ReadStatus.NORMAL, result.status());
    assertEquals(FindingDetailCode.RANGE_NOT_APPLICABLE, result.detailCode());
  }

  @Test
  void locked_accessDenied_noPort_noJsonl() throws Exception {
    facade.selectProfile("FARDRIVER_ND72530", catalog.catalogVersion());
    // DEFAULT_LOCKED
    readService.resetPortCallCount();
    filePort.setFixtureRunConfig(new FixtureRunConfig("RD-ND-001"));

    ReadDataResult result = readService.read("0x1001");

    assertEquals(ReadStatus.ACCESS_DENIED, result.status());
    assertEquals(0, readService.portCallCount());
    assertFalse(Files.exists(recorder.sessionDir().resolve("requests.jsonl")));
    assertFalse(Files.exists(recorder.sessionDir().resolve("responses.jsonl")));
  }

  @Test
  void reportTable_allReadRows() {
    List<String> lines = new ArrayList<>();
    lines.add(
        String.format(
            "%-14s %-12s %-28s %-28s %s",
            "scenario", "statusExp", "valueExp", "valueAct", "statusAct"));
    for (ReadScenario scenario : repository.readScenarios()) {
      facade.selectProfile(scenario.controllerProfile(), catalog.catalogVersion());
      facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
      filePort.setFixtureRunConfig(new FixtureRunConfig(scenario.scenarioId()));
      ReadDataResult result = readService.read(scenario.did());
      String expVal = scenario.expectedValue() == null ? "" : scenario.expectedValue();
      lines.add(
          String.format(
              "%-14s %-12s %-28s %-28s %s",
              scenario.scenarioId(),
              scenario.expectedStatus(),
              expVal,
              result.displayValue(),
              result.status()));
      assertEquals(ReadStatus.valueOf(scenario.expectedStatus()), result.status());
    }
    assertEquals(20, repository.readScenarios().size());
    System.out.println(String.join(System.lineSeparator(), lines));
  }
}
