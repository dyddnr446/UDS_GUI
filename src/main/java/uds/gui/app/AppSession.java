package uds.gui.app;

import java.nio.file.Path;
import java.util.Objects;
import uds.access.facade.DiagnosticAccessFacade;
import uds.access.guard.DiagnosticAccessGuard;
import uds.access.service.SecurityAccessService;
import uds.access.service.SessionControlService;
import uds.access.state.ActiveDiagnosticContext;
import uds.analysis.record.SafeExchangeRecorder;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.exchange.policy.ExchangeCoordinator;
import uds.exchange.policy.ExchangePolicy;
import uds.exchange.recording.RecordingDiagnosticExchangePort;
import uds.fixture.config.SystemClock;
import uds.fixture.port.FileDiagnosticExchangePort;
import uds.fixture.repo.FixtureRepository;
import uds.fixture.sim.StatefulFixtureEcuSimulator;
import uds.interpreter.decode.UdsInterpreter;
import uds.read.service.ReadDataService;
import uds.write.service.WriteDataService;

/** Analysis-session-scoped wiring: single File/Recording/Simulator instances. */
public final class AppSession {

  private final UdsCatalog catalog;
  private final ActiveDiagnosticContext context;
  private final FileDiagnosticExchangePort filePort;
  private final SafeExchangeRecorder recorder;
  private final DiagnosticAccessFacade accessFacade;
  private final ReadDataService readDataService;
  private final WriteDataService writeDataService;
  private final UdsInterpreter interpreter;
  private final Path sessionDir;
  private final String analysisSessionId;

  private AppSession(
      UdsCatalog catalog,
      ActiveDiagnosticContext context,
      FileDiagnosticExchangePort filePort,
      SafeExchangeRecorder recorder,
      DiagnosticAccessFacade accessFacade,
      ReadDataService readDataService,
      WriteDataService writeDataService,
      UdsInterpreter interpreter,
      Path sessionDir,
      String analysisSessionId) {
    this.catalog = catalog;
    this.context = context;
    this.filePort = filePort;
    this.recorder = recorder;
    this.accessFacade = accessFacade;
    this.readDataService = readDataService;
    this.writeDataService = writeDataService;
    this.interpreter = interpreter;
    this.sessionDir = sessionDir;
    this.analysisSessionId = analysisSessionId;
  }

  public static AppSession open(
      Path catalogXlsx,
      Path samplesDir,
      Path passwordFile,
      Path outputsRoot,
      String analysisSessionId)
      throws Exception {
    Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    UdsCatalog catalog = CatalogLoader.load(catalogXlsx);
    FixtureRepository repository =
        new FixtureRepository(
            samplesDir.resolve("access_scenarios.csv"),
            samplesDir.resolve("read_data_scenarios.csv"),
            samplesDir.resolve("write_data_scenarios.csv"));
    StatefulFixtureEcuSimulator simulator =
        new StatefulFixtureEcuSimulator(repository, passwordFile, new SystemClock());
    FileDiagnosticExchangePort filePort = new FileDiagnosticExchangePort(simulator);
    Path sessionDir = outputsRoot.resolve(analysisSessionId);
    SafeExchangeRecorder recorder = new SafeExchangeRecorder(sessionDir, analysisSessionId);
    RecordingDiagnosticExchangePort recording =
        new RecordingDiagnosticExchangePort(filePort, recorder);
    ExchangeCoordinator coordinator = new ExchangeCoordinator(recording, new ExchangePolicy());
    ActiveDiagnosticContext context = new ActiveDiagnosticContext();
    DiagnosticAccessGuard guard = new DiagnosticAccessGuard(context, catalog);
    DiagnosticAccessFacade facade =
        new DiagnosticAccessFacade(
            context,
            new SessionControlService(context, coordinator, recorder, analysisSessionId),
            new SecurityAccessService(context, coordinator, recorder, analysisSessionId),
            filePort,
            guard);
    ReadDataService readDataService =
        new ReadDataService(context, guard, catalog, coordinator, analysisSessionId);
    WriteDataService writeDataService =
        new WriteDataService(context, guard, catalog, coordinator, analysisSessionId);
    UdsInterpreter interpreter = new UdsInterpreter(catalog);
    return new AppSession(
        catalog,
        context,
        filePort,
        recorder,
        facade,
        readDataService,
        writeDataService,
        interpreter,
        sessionDir,
        analysisSessionId);
  }

  public UdsCatalog catalog() {
    return catalog;
  }

  public ActiveDiagnosticContext context() {
    return context;
  }

  public FileDiagnosticExchangePort filePort() {
    return filePort;
  }

  public SafeExchangeRecorder recorder() {
    return recorder;
  }

  public DiagnosticAccessFacade accessFacade() {
    return accessFacade;
  }

  public ReadDataService readDataService() {
    return readDataService;
  }

  public WriteDataService writeDataService() {
    return writeDataService;
  }

  public UdsInterpreter interpreter() {
    return interpreter;
  }

  public Path sessionDir() {
    return sessionDir;
  }

  public String analysisSessionId() {
    return analysisSessionId;
  }
}
