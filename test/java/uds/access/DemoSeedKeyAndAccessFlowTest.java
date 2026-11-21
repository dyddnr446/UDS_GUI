package uds.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.analysis.SafeExchangeRecorder;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeCoordinator;
import uds.exchange.ExchangePolicy;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;
import uds.exchange.RecordingDiagnosticExchangePort;
import uds.fixture.AccessStep;
import uds.fixture.FileDiagnosticExchangePort;
import uds.fixture.FixtureRepository;
import uds.fixture.StatefulFixtureEcuSimulator;
import uds.fixture.SystemClock;
import uds.access.DemoSeedKeyAlgorithm;
import uds.access.DiagnosticAccessGuard;
import uds.access.GuardDecision;
import uds.access.AccessOutcome;
import uds.access.SecurityAccessService;
import uds.access.SessionControlService;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;

class DemoSeedKeyAndAccessFlowTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");
  private static final String SESSION = "access-step3";

  @TempDir Path tempDir;

  private FixtureRepository repository;
  private UdsCatalog catalog;
  private FileDiagnosticExchangePort filePort;
  private ActiveDiagnosticContext context;
  private DiagnosticAccessFacade facade;
  private SafeExchangeRecorder recorder;

  @BeforeEach
  void setUp() throws Exception {
    catalog = CatalogLoader.load(CATALOG);
    repository = FixtureRepository.load(FIXTURE);
    StatefulFixtureEcuSimulator simulator =
        new StatefulFixtureEcuSimulator(
            repository, uds.gui.app.AppConfig.DEFAULT_DEMO_PASSWORD, new SystemClock());
    filePort = new FileDiagnosticExchangePort(simulator);
    recorder = new SafeExchangeRecorder(tempDir.resolve("outputs").resolve(SESSION), SESSION);
    RecordingDiagnosticExchangePort recording =
        new RecordingDiagnosticExchangePort(filePort, recorder);
    ExchangeCoordinator coordinator = new ExchangeCoordinator(recording, new ExchangePolicy());
    context = new ActiveDiagnosticContext();
    SessionControlService session =
        new SessionControlService(context, coordinator, recorder, SESSION);
    SecurityAccessService security =
        new SecurityAccessService(context, coordinator, recorder, SESSION);
    DiagnosticAccessGuard guard = new DiagnosticAccessGuard(context, catalog);
    facade = new DiagnosticAccessFacade(context, session, security, filePort, guard);
  }

  @Test
  void demoVector_isD3Ac3F47() {
    byte[] key =
        DemoSeedKeyAlgorithm.computeKey(
            "uds-demo-2026", DemoSeedKeyAlgorithm.DEMO_SEED, "FARDRIVER_ND72530");
    assertEquals("D3AC3F47", HexFormat.of().formatHex(key).toUpperCase(Locale.ROOT));
  }

  @Test
  void accessVescSuccess_reachesUnlocked() {
    facade.selectProfile("FLIPSKY_75100_VESC", catalog.catalogVersion());
    facade.setFixtureRunConfig("ACCESS-VESC-SUCCESS");
    assertEquals(DiagnosticAccessState.EXTENDED_LOCKED, facade.enterExtended().resultingState());
    assertEquals(DiagnosticAccessState.EXTENDED_LOCKED, facade.requestSeed().resultingState());
    AccessOutcome unlocked = facade.unlock("uds-demo-2026".toCharArray());
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, unlocked.resultingState());
    assertEquals(null, unlocked.errorCode());
  }

  @Test
  void allAccessFlows_matchExpectedStateAndError() {
    Map<String, List<AccessStep>> byFlow =
        repository.accessSteps().stream()
            .collect(Collectors.groupingBy(AccessStep::flowId));
    assertEquals(8, byFlow.size());
    assertEquals(14, repository.accessSteps().size());

    List<String> flowIds = new ArrayList<>(byFlow.keySet());
    flowIds.sort(Comparator.naturalOrder());
    for (String flowId : flowIds) {
      List<AccessStep> steps = byFlow.get(flowId);
      steps.sort(Comparator.comparingInt(AccessStep::stepNo));
      String profile = steps.get(0).profileId();
      facade.selectProfile(profile, catalog.catalogVersion());
      facade.setFixtureRunConfig(flowId);

      for (AccessStep step : steps) {
        facade.primeState(DiagnosticAccessState.valueOf(step.initialState()));
        AccessOutcome outcome = runAction(step);
        assertEquals(
            DiagnosticAccessState.valueOf(step.expectedState()),
            outcome.resultingState(),
            flowId + " step " + step.stepNo() + " state");
        String expectedError =
            step.expectedError() == null || step.expectedError().isBlank()
                ? null
                : step.expectedError();
        assertEquals(
            expectedError, outcome.errorCode(), flowId + " step " + step.stepNo() + " error");
        assertFalse(outcome.staleDiscarded(), flowId + " should not be stale");
      }
    }
  }

  @Test
  void guard_deniesLockedUnsupportedAndRuntimeDisabled() {
    facade.selectProfile("FARDRIVER_ND72530", catalog.catalogVersion());
    assertEquals(GuardDecision.ACCESS_DENIED, facade.checkRead("0x1001"));

    facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
    assertEquals(GuardDecision.ALLOW, facade.checkRead("0x1001"));
    assertEquals(GuardDecision.ACCESS_DENIED, facade.checkRead("0x1003")); // runtime N
    assertEquals(GuardDecision.ACCESS_DENIED, facade.checkRead("0x9999")); // unsupported
  }

  @Test
  void reset_bumpsGeneration_andStaleResponseDiscarded() {
    facade.selectProfile("FARDRIVER_ND72530", catalog.catalogVersion());
    facade.setFixtureRunConfig("ACCESS-ND-SUCCESS");
    long genBefore = facade.snapshot().generation();

    DiagnosticExchangePort resetting =
        new DiagnosticExchangePort() {
          @Override
          public ExchangeResponse exchange(ExchangeRequest request) {
            ExchangeResponse rsp = filePort.exchange(request);
            context.reset();
            return rsp;
          }
        };
    SessionControlService staleSession =
        new SessionControlService(context, resetting, recorder, SESSION);
    AccessOutcome outcome = staleSession.enterExtended();
    assertTrue(outcome.staleDiscarded());
    assertFalse(outcome.applied());
    assertTrue(facade.snapshot().generation() > genBefore);
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, facade.snapshot().accessState());
  }

  private AccessOutcome runAction(AccessStep step) {
    return switch (step.action()) {
      case "EXTENDED_SESSION" -> facade.enterExtended();
      case "REQUEST_SEED" -> facade.requestSeed();
      case "SEND_KEY" -> {
        char[] password =
            "ACCESS-INVALID-KEY".equals(step.flowId())
                ? "wrong-password".toCharArray()
                : "uds-demo-2026".toCharArray();
        yield facade.unlock(password);
      }
      default -> throw new IllegalStateException("unknown action " + step.action());
    };
  }
}
