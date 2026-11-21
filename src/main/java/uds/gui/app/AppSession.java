package uds.gui.app;

import java.nio.file.Path;
import java.util.Objects;
import uds.access.DiagnosticAccessFacade;
import uds.access.DiagnosticAccessGuard;
import uds.access.SessionCapabilityPolicy;
import uds.access.SecurityAccessService;
import uds.access.SessionControlService;
import uds.access.TesterPresentKeepAlive;
import uds.access.ActiveDiagnosticContext;
import uds.analysis.SafeExchangeRecorder;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.exchange.ExchangeCoordinator;
import uds.exchange.ExchangePolicy;
import uds.exchange.RecordingDiagnosticExchangePort;
import uds.fixture.SystemClock;
import uds.fixture.FileDiagnosticExchangePort;
import uds.fixture.FixtureRepository;
import uds.fixture.StatefulFixtureEcuSimulator;
import uds.interpreter.UdsInterpreter;
import uds.read.DtcService;
import uds.read.ReadDataService;
import uds.write.WriteDataService;

/** Analysis-session-scoped wiring: single File/Recording/Simulator instances. */
public final class AppSession {

  private final UdsCatalog catalog;
  private final FixtureRepository fixtures;
  private final ActiveDiagnosticContext context;
  private final FileDiagnosticExchangePort filePort;
  private final SafeExchangeRecorder recorder;
  private final DiagnosticAccessFacade accessFacade;
  private final SessionCapabilityPolicy capabilityPolicy;
  private final ReadDataService readDataService;
  private final WriteDataService writeDataService;
  private final DtcService dtcService;
  private final UdsInterpreter interpreter;
  private final TesterPresentKeepAlive testerPresentKeepAlive;
  private final Path sessionDir;
  private final String analysisSessionId;

  private AppSession(
      UdsCatalog catalog,
      FixtureRepository fixtures,
      ActiveDiagnosticContext context,
      FileDiagnosticExchangePort filePort,
      SafeExchangeRecorder recorder,
      DiagnosticAccessFacade accessFacade,
      SessionCapabilityPolicy capabilityPolicy,
      ReadDataService readDataService,
      WriteDataService writeDataService,
      DtcService dtcService,
      UdsInterpreter interpreter,
      TesterPresentKeepAlive testerPresentKeepAlive,
      Path sessionDir,
      String analysisSessionId) {
    this.catalog = catalog;
    this.fixtures = fixtures;
    this.context = context;
    this.filePort = filePort;
    this.recorder = recorder;
    this.accessFacade = accessFacade;
    this.capabilityPolicy = capabilityPolicy;
    this.readDataService = readDataService;
    this.writeDataService = writeDataService;
    this.dtcService = dtcService;
    this.interpreter = interpreter;
    this.testerPresentKeepAlive = testerPresentKeepAlive;
    this.sessionDir = sessionDir;
    this.analysisSessionId = analysisSessionId;
  }

  /** Open from a catalog directory containing DID/DTC + SID masters. */
  public static AppSession open(
      Path catalogDir, String demoPassword, Path outputsRoot, String analysisSessionId)
      throws Exception {
    Path dir = catalogDir.toAbsolutePath().normalize();
    Path fixture =
        dir.getParent().resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");
    return open(
        dir.resolve(CatalogLoader.DEFAULT_DID_DTC_FILE),
        dir.resolve(CatalogLoader.DEFAULT_SID_FILE),
        fixture,
        demoPassword,
        outputsRoot,
        analysisSessionId);
  }

  public static AppSession open(
      Path didDtcXlsx,
      Path sidXlsx,
      Path fixtureXlsx,
      String demoPassword,
      Path outputsRoot,
      String analysisSessionId)
      throws Exception {
    Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    Objects.requireNonNull(demoPassword, "demoPassword");
    Objects.requireNonNull(fixtureXlsx, "fixtureXlsx");
    Objects.requireNonNull(didDtcXlsx, "didDtcXlsx");
    Objects.requireNonNull(sidXlsx, "sidXlsx");
    UdsCatalog catalog = CatalogLoader.load(didDtcXlsx, sidXlsx);
    FixtureRepository repository = FixtureRepository.load(fixtureXlsx);
    StatefulFixtureEcuSimulator simulator =
        new StatefulFixtureEcuSimulator(repository, demoPassword, new SystemClock());
    FileDiagnosticExchangePort filePort = new FileDiagnosticExchangePort(simulator);
    // User logs: output/live_session.log ; machine JSONL: output/.session/
    Path outputs = outputsRoot.toAbsolutePath().normalize();
    Path sessionDir = outputs.resolve(SafeExchangeRecorder.SESSION_SUBDIR);
    SafeExchangeRecorder recorder = new SafeExchangeRecorder(outputs, analysisSessionId);
    RecordingDiagnosticExchangePort recording =
        new RecordingDiagnosticExchangePort(filePort, recorder);
    ExchangeCoordinator coordinator = new ExchangeCoordinator(recording, new ExchangePolicy());
    ActiveDiagnosticContext context = new ActiveDiagnosticContext();
    TesterPresentKeepAlive keepAlive =
        new TesterPresentKeepAlive(context, coordinator, analysisSessionId);
    DiagnosticAccessGuard guard = new DiagnosticAccessGuard(context, catalog);
    SessionCapabilityPolicy capabilityPolicy = new SessionCapabilityPolicy(guard);
    DiagnosticAccessFacade facade =
        new DiagnosticAccessFacade(
            context,
            new SessionControlService(
                context, coordinator, recorder, analysisSessionId, keepAlive),
            new SecurityAccessService(context, coordinator, recorder, analysisSessionId),
            filePort,
            guard,
            keepAlive);
    ReadDataService readDataService =
        new ReadDataService(context, guard, catalog, coordinator, analysisSessionId);
    WriteDataService writeDataService =
        new WriteDataService(context, guard, catalog, coordinator, analysisSessionId);
    DtcService dtcService =
        new DtcService(context, guard, coordinator, catalog, analysisSessionId);
    for (var e : simulator.dtcNameHints().entrySet()) {
      dtcService.putNameHint(e.getKey(), e.getValue());
    }
    UdsInterpreter interpreter = new UdsInterpreter(catalog);
    return new AppSession(
        catalog,
        repository,
        context,
        filePort,
        recorder,
        facade,
        capabilityPolicy,
        readDataService,
        writeDataService,
        dtcService,
        interpreter,
        keepAlive,
        sessionDir,
        analysisSessionId);
  }

  /** Convenience: catalog path + password + outputs from {@link AppConfig}. */
  public static AppSession open(AppConfig config, Path outputsRoot, String analysisSessionId)
      throws Exception {
    Objects.requireNonNull(config, "config");
    config.requireCatalogExists();
    config.requireFixtureExists();
    return open(
        config.didDtcCatalogPath(),
        config.sidCatalogPath(),
        config.fixturePath(),
        config.demoPassword(),
        outputsRoot,
        analysisSessionId);
  }

  public UdsCatalog catalog() {
    return catalog;
  }

  public FixtureRepository fixtures() {
    return fixtures;
  }

  public ActiveDiagnosticContext context() {
    return context;
  }

  public FileDiagnosticExchangePort filePort() {
    return filePort;
  }

  /** Offline demo: restore seed DTCs after Clear. */
  public void resetFixtureDtc() {
    filePort.resetFixtureDtc();
  }

  public SafeExchangeRecorder recorder() {
    return recorder;
  }

  public DiagnosticAccessFacade accessFacade() {
    return accessFacade;
  }

  public SessionCapabilityPolicy capabilityPolicy() {
    return capabilityPolicy;
  }

  public ReadDataService readDataService() {
    return readDataService;
  }

  public WriteDataService writeDataService() {
    return writeDataService;
  }

  public DtcService dtcService() {
    return dtcService;
  }

  public UdsInterpreter interpreter() {
    return interpreter;
  }

  public TesterPresentKeepAlive testerPresentKeepAlive() {
    return testerPresentKeepAlive;
  }

  public Path sessionDir() {
    return sessionDir;
  }

  public String analysisSessionId() {
    return analysisSessionId;
  }
}
