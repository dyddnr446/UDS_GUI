package uds.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.access.facade.DiagnosticAccessFacade;
import uds.access.service.AccessOutcome;
import uds.access.state.DiagnosticAccessState;
import uds.ai.advisor.AiAdvisor;
import uds.ai.provider.AiProvider;
import uds.analysis.session.AnalysisSessionStore;
import uds.fixture.repo.AccessStep;
import uds.fixture.repo.ReadScenario;
import uds.fixture.repo.WriteScenario;
import uds.gui.app.AppSession;
import uds.gui.app.UiTaskRunner;
import uds.gui.shell.MainViewModel;
import uds.interpreter.decode.UdsInterpreter;
import uds.interpreter.model.DecodeKind;
import uds.interpreter.model.DecodeResult;
import uds.io.Frame;
import uds.io.HexNormalizer;
import uds.io.LogReadResult;
import uds.io.LogReader;
import uds.read.finding.ReadDataResult;
import uds.read.finding.ReadStatus;
import uds.write.parse.WriteResult;
import uds.write.parse.WriteStatus;

/**
 * Slice 8 gate: Must requirements presence, 37-row service chain, demo E2E, zero-leak, NFR-04.
 */
class FullRequirementsRegressionTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");
  private static final Path REQUIREMENTS = ROOT.resolve("docs").resolve("requirements.xlsx");
  private static final Path PASSWORD = ROOT.resolve("config").resolve("demo_password.txt");
  private static final Path DEMO_LOG = SAMPLES.resolve("demo_session.log");

  private static final String[] LEAK_NEEDLES =
      new String[] {
        "uds-demo-2026",
        "AA BB CC DD",
        "AABBCCDD",
        "D3 AC 3F 47",
        "D3AC3F47",
        "aa bb cc dd",
        "d3 ac 3f 47"
      };

  @TempDir Path tempDir;

  private AppSession session;
  private MainViewModel main;
  private Path outputsRoot;

  @BeforeEach
  void setUp() throws Exception {
    outputsRoot = tempDir.resolve("outputs");
    AiProvider mockAi =
        prompt -> {
          // Never echo prompt secrets into advice text.
          assertNoLeak(prompt, "AI prompt");
          String findingId = "FINDING:1";
          if (!main.analysis().store().findings().isEmpty()) {
            findingId = main.analysis().store().findings().get(0).evidenceId();
          }
          return new AiProvider.ProviderSuccess(
              "전원/공급 확인",
              List.of(new AiProvider.ChecklistDraft("저전압 Finding 확인", List.of(findingId))));
        };
    session = AppSession.open(CATALOG, SAMPLES, PASSWORD, outputsRoot, "step8-full");
    main = new MainViewModel(session, SAMPLES, mockAi, new UiTaskRunner(15));
  }

  @Test
  void mustRequirements_existInRequirementsXlsx() throws Exception {
    try (Workbook wb = new XSSFWorkbook(Files.newInputStream(REQUIREMENTS))) {
      assertIdsPresent(wb.getSheet("Functional"), "FR-", Set.of("M"), 26);
      assertIdsPresent(wb.getSheet("NonFunctional"), "NFR-", Set.of("M"), 18);
      assertIdsPresent(wb.getSheet("UseCases"), "UC-", Set.of("M"), 5);
      assertIdsPresent(wb.getSheet("Completion"), "DOD-", null, 17);
    }
  }

  @Test
  void serviceChain_37Rows_access14_read11_write12() {
    var fixtures = main.session(); // touch session
    assertNotNull(fixtures);
    List<AccessStep> access = loadAccessSteps();
    List<ReadScenario> reads = loadReadScenarios();
    List<WriteScenario> writes = loadWriteScenarios();
    assertEquals(14, access.size(), "access steps");
    assertEquals(11, reads.size(), "read scenarios");
    assertEquals(12, writes.size(), "write scenarios");
    assertEquals(37, access.size() + reads.size() + writes.size());

    // Access 14 steps
    Map<String, List<AccessStep>> byFlow =
        access.stream().collect(Collectors.groupingBy(AccessStep::flowId));
    List<String> flowIds = new ArrayList<>(byFlow.keySet());
    flowIds.sort(Comparator.naturalOrder());
    DiagnosticAccessFacade facade = session.accessFacade();
    for (String flowId : flowIds) {
      List<AccessStep> steps = byFlow.get(flowId);
      steps.sort(Comparator.comparingInt(AccessStep::stepNo));
      facade.selectProfile(steps.get(0).profileId(), session.catalog().catalogVersion());
      facade.setFixtureRunConfig(flowId);
      for (AccessStep step : steps) {
        facade.primeState(DiagnosticAccessState.valueOf(step.initialState()));
        AccessOutcome outcome = runAccessAction(facade, step);
        assertEquals(
            DiagnosticAccessState.valueOf(step.expectedState()),
            outcome.resultingState(),
            flowId + "#" + step.stepNo());
        String expectedError =
            step.expectedError() == null || step.expectedError().isBlank()
                ? null
                : step.expectedError();
        assertEquals(expectedError, outcome.errorCode(), flowId + "#" + step.stepNo() + " err");
      }
    }

    // Read 11
    for (ReadScenario scenario : reads) {
      facade.selectProfile(scenario.controllerProfile(), session.catalog().catalogVersion());
      facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
      facade.setFixtureRunConfig(scenario.scenarioId());
      ReadDataResult result = session.readDataService().read(scenario.did());
      assertEquals(
          ReadStatus.valueOf(scenario.expectedStatus()),
          result.status(),
          scenario.scenarioId());
      if (scenario.expectedValue() != null && !scenario.expectedValue().isBlank()) {
        assertEquals(scenario.expectedValue().trim(), result.displayValue(), scenario.scenarioId());
      }
    }

    // Write 12
    for (WriteScenario scenario : writes) {
      facade.selectProfile(scenario.controllerProfile(), session.catalog().catalogVersion());
      facade.primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
      facade.setFixtureRunConfig(scenario.scenarioId());
      WriteResult result =
          session.writeDataService().write(scenario.did(), scenario.inputValue());
      assertEquals(
          WriteStatus.valueOf(scenario.expectedStatus()),
          result.status(),
          scenario.scenarioId());
    }
  }

  @Test
  void fr11_referenceLog_storyCompletes() throws Exception {
    LogReadResult log = LogReader.read(DEMO_LOG);
    UdsInterpreter interpreter = session.interpreter();
    List<DecodeResult> results = interpreter.parse(log);
    assertEquals(log.frames().size(), results.size());
    assertTrue(results.size() >= 10);

    List<String> sids =
        results.stream().map(DecodeResult::sid).filter(s -> s != null && !s.isBlank()).toList();
    assertTrue(sids.contains("0x10") || sids.contains("0x50"));
    assertTrue(sids.contains("0x22") || sids.stream().anyMatch(s -> s.equalsIgnoreCase("0x62")));
    assertTrue(
        results.stream().anyMatch(r -> r.kind() == DecodeKind.NEGATIVE),
        "reference story includes NRC path");
    assertTrue(
        results.stream()
            .anyMatch(
                r ->
                    "0x27".equalsIgnoreCase(r.sid())
                        || "0x67".equalsIgnoreCase(r.sid())
                        || (r.fields() != null
                            && r.fields().values().stream()
                                .anyMatch(v -> v != null && v.contains("REDACTED")))),
        "security frames present and/or redacted");
  }

  @Test
  void fr19_multiReadScenarios_accumulateFindings() {
    main.selectProfile("FLIPSKY_75100_VESC");
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.access().runAccessSuccessFlow("uds-demo-2026");
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());

    AnalysisSessionStore store = main.analysis().store();
    List<String> scenarioOrder = List.of("RD-VESC-001", "RD-VESC-002");
    for (String scenarioId : scenarioOrder) {
      ReadScenario scenario =
          loadReadScenarios().stream()
              .filter(s -> s.scenarioId().equals(scenarioId))
              .findFirst()
              .orElseThrow();
      main.read().selectScenario(scenarioId);
      main.read().selectDid(scenario.did());
      ReadDataResult result = main.read().readSelected();
      store.appendRead(result);
      assertEquals(ReadStatus.valueOf(scenario.expectedStatus()), result.status(), scenarioId);
    }
    assertEquals(2, store.readResults().size());
    assertEquals(2, store.findings().size());
    String read1 = store.readResults().get(0).evidenceId();
    String read2 = store.readResults().get(1).evidenceId();
    assertTrue(read1.startsWith("READ:"), read1);
    assertTrue(read2.startsWith("READ:"), read2);
    assertFalse(read1.equals(read2));
    assertEquals(scenarioOrder.get(0), "RD-VESC-001");
    assertEquals(
        ReadStatus.valueOf(
            loadReadScenarios().stream()
                .filter(s -> s.scenarioId().equals("RD-VESC-001"))
                .findFirst()
                .orElseThrow()
                .expectedStatus()),
        store.readResults().get(0).result().status());
    assertEquals(
        ReadStatus.valueOf(
            loadReadScenarios().stream()
                .filter(s -> s.scenarioId().equals("RD-VESC-002"))
                .findFirst()
                .orElseThrow()
                .expectedStatus()),
        store.readResults().get(1).result().status());
    assertTrue(store.findings().get(0).evidenceId().startsWith("FINDING:"));
    assertTrue(store.findings().get(1).evidenceId().startsWith("FINDING:"));
    assertFalse(
        store.findings().get(0).evidenceId().equals(store.findings().get(1).evidenceId()));
  }

  @Test
  void demoE2E_uc01_06_02_04_05_03() throws Exception {
    // UC-01 Interpreter
    main.interpreter().loadLog(DEMO_LOG);
    assertFalse(main.interpreter().decodeResults().isEmpty());
    for (DecodeResult d : main.interpreter().decodeResults()) {
      main.analysis().store().appendDecode(d);
    }

    // UC-06 Access
    main.selectProfile("FLIPSKY_75100_VESC");
    main.access().selectFlow("ACCESS-VESC-SUCCESS");
    main.access().runAccessSuccessFlow("uds-demo-2026");
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());

    // UC-02 symptom → suggest → read → AI
    main.ai().setSymptomText("모터 급정지");
    main.ai().refreshSuggestions();
    assertEquals(List.of("0x1001"), main.ai().runtimeExecutableDids());
    main.read().selectScenario("RD-VESC-001");
    main.ai().runSuggestedReads();
    assertFalse(main.analysis().store().findings().isEmpty());
    assertTrue(main.ai().factZone().contains("사실 구역"));
    AiAdvisor.AdviseResult advice =
        new AiAdvisor(
                prompt ->
                    new AiProvider.ProviderSuccess(
                        "AI 전원 점검",
                        List.of(
                            new AiProvider.ChecklistDraft(
                                "check",
                                List.of(
                                    main.analysis().store().findings().get(0).evidenceId())))))
            .advise("모터 급정지", "FLIPSKY_75100_VESC", main.analysis().store());
    AiAdvisor.AdviseResult.Ok ok = assertInstanceOf(AiAdvisor.AdviseResult.Ok.class, advice);
    main.analysis().store().setAiAdvice(ok.advice());
    assertTrue(ok.advice().evidenceIds().size() >= 1);
    assertTrue(main.ai().factZone().contains("Finding") || !main.ai().findings().isEmpty());

    // UC-04 static/VIN style frame via read service parse
    ReadDataResult vin =
        session
            .readDataService()
            .parseFrame("62 F1 90 57 58 59 31 32 33 34 35 36 37 38 39 41 42 43", "0xF190");
    assertNotNull(vin.status());
    main.analysis().store().appendRead(vin);

    // UC-05 Write
    main.write().onProfileChanged("FLIPSKY_75100_VESC");
    WriteScenario wd =
        loadWriteScenarios().stream()
            .filter(s -> "FLIPSKY_75100_VESC".equals(s.controllerProfile()))
            .filter(s -> "ACCEPTED".equals(s.expectedStatus()))
            .findFirst()
            .orElseThrow();
    main.write().selectDid(wd.did());
    main.write().selectScenario(wd.scenarioId());
    main.write().setInputValue(wd.inputValue());
    WriteResult write = main.write().writeSelected();
    assertEquals(WriteStatus.ACCEPTED, write.status());
    main.analysis().store().appendWrite(write);

    // UC-03 save / restore (team share)
    main.analysis().bindProfile("FLIPSKY_75100_VESC");
    main.analysis().save();
    Path sessionDir = session.sessionDir();
    assertTrue(Files.exists(sessionDir.resolve("analysis-session.json")));
    assertTrue(Files.exists(sessionDir.resolve("analysis.json")));

    // Past unlock must not restore privilege
    session.context().primeState(DiagnosticAccessState.EXTENDED_UNLOCKED);
    var restored = main.analysis().restoreFrom(sessionDir);
    assertNotNull(restored.store());
    assertEquals(DiagnosticAccessState.DEFAULT_LOCKED, main.analysis().accessState());
    assertFalse(restored.store().findings().isEmpty());
    assertTrue(restored.store().aiAdvice().isPresent());
  }

  @Test
  void dod16_zeroLeakage_afterAccessAndAiContext() throws Exception {
    main.selectProfile("FARDRIVER_ND72530");
    main.access().selectFlow("ACCESS-ND-SUCCESS");
    main.access().runAccessSuccessFlow("uds-demo-2026");
    assertEquals(DiagnosticAccessState.EXTENDED_UNLOCKED, main.access().accessState());

    main.ai().setSymptomText("급정지 password=uds-demo-2026 seed=AA BB CC DD key=D3 AC 3F 47");
    main.ai().refreshSuggestions();
    // Force sanitize prompt path
    String prompt =
        new uds.ai.context.AdvisorPromptBuilder()
            .build(
                new uds.ai.context.AdvisorContextSanitizer()
                    .sanitize(
                        main.ai().symptomText(),
                        "FARDRIVER_ND72530",
                        main.analysis().store()));
    assertNoLeak(prompt, "sanitized prompt");

    main.analysis().save();
    scanTreeForLeaks(session.sessionDir());
  }

  @Test
  void nfr04_200Frames_decodeWithin3Seconds() {
    List<Frame> frames = new ArrayList<>(200);
    for (int i = 0; i < 200; i++) {
      String hex = (i % 2 == 0) ? "22 10 01" : "62 10 01 01 F4";
      byte[] bytes = HexNormalizer.normalize(hex).orElseThrow();
      frames.add(new Frame(String.format(Locale.ROOT, "%.3f", i * 0.001), i % 2 == 0 ? "REQ" : "RES", hex, bytes));
    }
    UdsInterpreter interpreter = session.interpreter();
    long start = System.nanoTime();
    List<DecodeResult> results = interpreter.parseAll(frames);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
    assertEquals(200, results.size());
    assertTrue(elapsedMs <= 3_000L, "NFR-04 decode took " + elapsedMs + "ms");
  }

  private static void assertIdsPresent(
      Sheet sheet, String prefix, Set<String> priorities, int expectedCount) {
    DataFormatter fmt = new DataFormatter();
    Row header = sheet.getRow(0);
    int idCol = 0;
    int priCol = -1;
    for (int c = 0; c < header.getLastCellNum(); c++) {
      String name = fmt.formatCellValue(header.getCell(c)).trim().toLowerCase(Locale.ROOT);
      if (name.equals("id")) {
        idCol = c;
      }
      if (name.contains("우선") || name.equals("priority") || name.equals("우선순위")) {
        priCol = c;
      }
    }
    // Functional/NFR often use column index 3 for priority (M/S/C)
    if (priCol < 0) {
      priCol = 3;
    }
    Set<String> ids = new LinkedHashSet<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null) {
        continue;
      }
      String id = fmt.formatCellValue(row.getCell(idCol)).trim();
      if (!id.startsWith(prefix)) {
        continue;
      }
      if (priorities != null) {
        String pri = fmt.formatCellValue(row.getCell(priCol)).trim();
        if (!priorities.contains(pri)) {
          continue;
        }
      }
      ids.add(id);
    }
    assertEquals(expectedCount, ids.size(), prefix + " Must/Completion count: " + ids);
  }

  private AccessOutcome runAccessAction(DiagnosticAccessFacade facade, AccessStep step) {
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

  private List<AccessStep> loadAccessSteps() {
    return new uds.fixture.repo.FixtureRepository(
            SAMPLES.resolve("access_scenarios.csv"),
            SAMPLES.resolve("read_data_scenarios.csv"),
            SAMPLES.resolve("write_data_scenarios.csv"))
        .accessSteps();
  }

  private List<ReadScenario> loadReadScenarios() {
    return new uds.fixture.repo.FixtureRepository(
            SAMPLES.resolve("access_scenarios.csv"),
            SAMPLES.resolve("read_data_scenarios.csv"),
            SAMPLES.resolve("write_data_scenarios.csv"))
        .readScenarios();
  }

  private List<WriteScenario> loadWriteScenarios() {
    return new uds.fixture.repo.FixtureRepository(
            SAMPLES.resolve("access_scenarios.csv"),
            SAMPLES.resolve("read_data_scenarios.csv"),
            SAMPLES.resolve("write_data_scenarios.csv"))
        .writeScenarios();
  }

  private static void assertNoLeak(String text, String where) {
    if (text == null) {
      return;
    }
    for (String needle : LEAK_NEEDLES) {
      assertFalse(
          text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT)),
          where + " contains leak '" + needle + "'");
    }
  }

  private static void scanTreeForLeaks(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path p : walk.filter(Files::isRegularFile).toList()) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".json")
            || name.endsWith(".jsonl")
            || name.endsWith(".txt")
            || name.endsWith(".log"))) {
          continue;
        }
        String content = Files.readString(p, StandardCharsets.UTF_8);
        assertNoLeak(content, p.toString());
      }
    }
  }
}
