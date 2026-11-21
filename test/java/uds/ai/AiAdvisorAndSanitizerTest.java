package uds.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uds.ai.AdvisorContext;
import uds.ai.AdvisorContextSanitizer;
import uds.ai.AdvisorPromptBuilder;
import uds.ai.AiProvider;
import uds.analysis.AccessEvidence;
import uds.analysis.AnalysisSessionStore;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.interpreter.DecodeKind;
import uds.interpreter.DecodeResult;
import uds.read.Finding;
import uds.read.FindingDetailCode;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;
import uds.write.WriteResult;
import uds.write.WriteStatus;

class AiAdvisorAndSanitizerTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");

  private UdsCatalog catalog;
  private SymptomDidSuggestionService suggestions;
  private AdvisorContextSanitizer sanitizer;
  private AdvisorPromptBuilder promptBuilder;

  @BeforeEach
  void setUp() throws Exception {
    catalog = CatalogLoader.load(CATALOG);
    suggestions = new SymptomDidSuggestionService();
    sanitizer = new AdvisorContextSanitizer();
    promptBuilder = new AdvisorPromptBuilder();
  }

  @Test
  void fr13_suddenStop_filtersRuntimeExecutableDidsByProfile() {
    var nd =
        suggestions.suggest(catalog, "FARDRIVER_ND72530", "모터 급정지 발생");
    assertTrue(nd.candidates().size() >= 5, "candidates should include mapped DIDs");
    assertEquals(
        List.of("0x1001", "0x1002", "0x1004", "0x1005", "0x1007"), nd.runtimeExecutableDids());
    assertFalse(nd.runtimeExecutableDids().contains("0x1003"));
    assertFalse(nd.runtimeExecutableDids().contains("0xF186"));

    var flip =
        suggestions.suggest(catalog, "FLIPSKY_75100_VESC", "갑자기 정지");
    assertEquals(List.of("0x1001", "0x1002", "0x1008", "0x1201"), flip.runtimeExecutableDids());

    var sessionHint = suggestions.suggest(catalog, "FARDRIVER_ND72530", "세션 통신 확인");
    assertEquals(List.of("0xF186"), sessionHint.staticLogReferenceDids());
    assertFalse(sessionHint.runtimeExecutableDids().contains("0xF186"));

    var empty = suggestions.suggest(catalog, "FARDRIVER_ND72530", "  ");
    assertEquals("현상을 입력하세요", empty.guidance());
    assertTrue(empty.candidates().isEmpty());
  }

  @Test
  void sanitizerAndPrompt_containNoPasswordSeedOrKeyPlaintext() {
    AnalysisSessionStore store = new AnalysisSessionStore("ai-test", catalog.catalogVersion());
    store.setProfileId("FARDRIVER_ND72530");
    store.appendDecode(
        new DecodeResult(
            "frame-1",
            DecodeKind.POSITIVE,
            "0x67",
            "SecurityAccess",
            "sendKey",
            null,
            null,
            java.util.Map.of(
                "seed", "AA BB CC DD",
                "key", "D3 AC 3F 47",
                "securityPayload", "AABBCCDD"),
            false));
    String readId =
        store.appendRead(
            new ReadDataResult(
                "r1",
                "s1",
                "0x1001",
                "22 10 01",
                "62 10 01 01 F4",
                "50.0 V",
                ReadStatus.ABNORMAL,
                FindingDetailCode.NONE,
                null,
                new Finding(
                    "0x1001",
                    "50.0 V",
                    ReadStatus.ABNORMAL,
                    FindingDetailCode.NONE,
                    "password=demo-secret seed=AABBCCDD key=D3AC3F47")));
    store.appendWrite(
        new WriteResult(
            "wr",
            "ws",
            "0x2002",
            "12000",
            "00 00 2E E0",
            "2E 20 02 00 00 2E E0",
            "6E 20 02",
            WriteStatus.ACCEPTED,
            "ok key=D3 AC 3F 47"));
    store.appendAccess(
        new AccessEvidence(
            store.ids().access(),
            store.analysisSessionId(),
            "SEND_KEY",
            "POSITIVE",
            null,
            "EXTENDED_UNLOCKED",
            true));

    AdvisorContext ctx =
        sanitizer.sanitize("급정지 password=should-not-leak", "FARDRIVER_ND72530", store);
    String prompt = promptBuilder.build(ctx);
    String lower = prompt.toLowerCase(Locale.ROOT);

    assertFalse(prompt.contains("AA BB CC DD"), prompt);
    assertFalse(prompt.contains("AABBCCDD"), prompt);
    assertFalse(prompt.contains("D3 AC 3F 47"), prompt);
    assertFalse(prompt.contains("D3AC3F47"), prompt);
    assertFalse(prompt.contains("demo-secret"), prompt);
    assertFalse(prompt.contains("should-not-leak"), prompt);
    assertFalse(lower.contains("password=demo"));
    assertTrue(prompt.contains("<REDACTED>"), prompt);
    assertTrue(prompt.contains(readId));
    assertTrue(
        ctx.facts().stream().anyMatch(f -> "FINDING".equals(f.kind())),
        "Finding facts retained after sanitize");
    assertTrue(
        ctx.facts().stream().anyMatch(f -> "READ".equals(f.kind()) && readId.equals(f.evidenceId())),
        "Linked READ retained after sanitize");
    assertTrue(
        ctx.facts().stream().allMatch(f -> "FINDING".equals(f.kind()) || "READ".equals(f.kind())),
        "AI facts must be FINDING/READ only, got: "
            + ctx.facts().stream().map(AdvisorContext.FactRow::kind).toList());
    assertFalse(ctx.facts().stream().anyMatch(f -> "LOG".equals(f.kind())));
    assertFalse(ctx.facts().stream().anyMatch(f -> "WRITE".equals(f.kind())));
    assertFalse(ctx.facts().stream().anyMatch(f -> "ACCESS".equals(f.kind())));
  }

  @Test
  void sanitize_diagnosisFocus_excludesLogWriteAccessNoise() {
    AnalysisSessionStore store = new AnalysisSessionStore("ai-focus", catalog.catalogVersion());
    store.setProfileId("FARDRIVER_ND72530");
    store.appendDecode(
        new DecodeResult(
            "frame-noise",
            DecodeKind.NEGATIVE,
            "0x27",
            "SecurityAccess",
            null,
            "0x35",
            "invalidKey",
            java.util.Map.of(),
            false));
    store.appendWrite(
        new WriteResult(
            "wr",
            "ws",
            "0x2001",
            "bad",
            "",
            "",
            "",
            WriteStatus.VALIDATION_ERROR_TYPE,
            "type"));
    store.appendAccess(
        new AccessEvidence(
            store.ids().access(),
            store.analysisSessionId(),
            "SEND_KEY",
            "NEGATIVE",
            "0x35",
            "DEFAULT_LOCKED",
            true));
    store.appendRead(
        new ReadDataResult(
            "r1",
            "s1",
            "0x1004",
            "22 10 04",
            "62 10 04 00 5C",
            "92.0 C",
            ReadStatus.ABNORMAL,
            FindingDetailCode.NONE,
            null,
            new Finding(
                "0x1004", "92.0 C", ReadStatus.ABNORMAL, FindingDetailCode.NONE, "RANGE")));

    AdvisorContext ctx = sanitizer.sanitize("과열", "FARDRIVER_ND72530", store);
    assertEquals(2, ctx.facts().size(), ctx.facts().toString());
    assertTrue(ctx.facts().stream().anyMatch(f -> "FINDING".equals(f.kind())));
    assertTrue(ctx.facts().stream().anyMatch(f -> "READ".equals(f.kind())));
    assertTrue(
        ctx.facts().stream()
            .filter(f -> "FINDING".equals(f.kind()))
            .anyMatch(f -> "92.0 C".equals(f.fields().get("displayValue"))));
  }

  @Test
  void mockAi_dropsUngroundedChecklistItems() {
    AnalysisSessionStore store = seededStore();
    String findingId = store.findings().get(0).evidenceId();

    AiProvider mock =
        prompt ->
            new AiProvider.ProviderSuccess(
                "전원 확인 권고",
                List.of(
                    new AiProvider.ChecklistDraft("저전압 Finding 근거", List.of(findingId)),
                    new AiProvider.ChecklistDraft("근거 없는 추측", List.of()),
                    new AiProvider.ChecklistDraft("가짜 ID", List.of("FINDING:999"))));

    AiAdvisor advisor = new AiAdvisor(sanitizer, promptBuilder, mock);
    AiAdvisor.AdviseResult result = advisor.advise("급정지", "FARDRIVER_ND72530", store);
    AiAdvisor.AdviseResult.Ok ok = assertInstanceOf(AiAdvisor.AdviseResult.Ok.class, result);
    assertEquals(1, ok.advice().checklist().size());
    assertEquals(findingId, ok.advice().checklist().get(0).evidenceIds().get(0));
    assertEquals(List.of(findingId), ok.advice().evidenceIds());
    assertTrue(ok.advice().warnings().stream().anyMatch(w -> w.contains("ungrounded")));
  }

  @Test
  void aiError_keepsFindingAndWriteResultUntouched() {
    AnalysisSessionStore store = seededStore();
    int findingsBefore = store.findings().size();
    int writesBefore = store.writeResults().size();
    Finding findingBefore = store.findings().get(0).finding();
    WriteResult writeBefore = store.writeResults().get(0).result();

    AiProvider failing =
        prompt ->
            new AiProvider.ProviderFailure(new AiError(AiError.TIMEOUT, "AI request exceeded 15s"));
    AiAdvisor advisor = new AiAdvisor(sanitizer, promptBuilder, failing);
    AiAdvisor.AdviseResult result = advisor.advise("급정지", "FARDRIVER_ND72530", store);

    assertInstanceOf(AiAdvisor.AdviseResult.Err.class, result);
    assertEquals(findingsBefore, store.findings().size());
    assertEquals(writesBefore, store.writeResults().size());
    assertEquals(findingBefore, store.findings().get(0).finding());
    assertEquals(writeBefore, store.writeResults().get(0).result());
    assertTrue(store.aiAdvice().isEmpty());
  }

  @Test
  void externalAiClient_missingKey_returnsAiErrorWithoutThrowing() {
    AiProvider client =
        new uds.ai.ExternalAiClient(
            java.net.http.HttpClient.newHttpClient(),
            new com.fasterxml.jackson.databind.ObjectMapper(),
            java.time.Duration.ofSeconds(15),
            null,
            "https://example.invalid/v1",
            "gpt-test");
    AiProvider.ProviderResult result = client.analyze("hello");
    AiProvider.ProviderFailure failure =
        assertInstanceOf(AiProvider.ProviderFailure.class, result);
    assertEquals(AiError.KEY_MISSING, failure.error().code());
  }

  private AnalysisSessionStore seededStore() {
    AnalysisSessionStore store = new AnalysisSessionStore("ai-test-2", catalog.catalogVersion());
    store.setProfileId("FARDRIVER_ND72530");
    store.appendRead(
        new ReadDataResult(
            "r1",
            "s1",
            "0x1001",
            "22 10 01",
            "62 10 01 01 F4",
            "50.0 V",
            ReadStatus.ABNORMAL,
            FindingDetailCode.NONE,
            null,
            new Finding(
                "0x1001", "50.0 V", ReadStatus.ABNORMAL, FindingDetailCode.NONE, "RANGE")));
    store.appendWrite(
        new WriteResult(
            "wr",
            "ws",
            "0x2002",
            "12000",
            "00 00 2E E0",
            "2E 20 02",
            "6E 20 02",
            WriteStatus.ACCEPTED,
            "ACCEPTED"));
    return store;
  }
}
