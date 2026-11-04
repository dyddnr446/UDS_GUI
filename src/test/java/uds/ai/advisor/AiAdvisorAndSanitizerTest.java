package uds.ai.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uds.ai.context.AdvisorContext;
import uds.ai.context.AdvisorContextSanitizer;
import uds.ai.context.AdvisorPromptBuilder;
import uds.ai.provider.AiProvider;
import uds.analysis.evidence.AccessEvidence;
import uds.analysis.session.AnalysisSessionStore;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.interpreter.model.DecodeKind;
import uds.interpreter.model.DecodeResult;
import uds.read.finding.Finding;
import uds.read.finding.FindingDetailCode;
import uds.read.finding.ReadDataResult;
import uds.read.finding.ReadStatus;
import uds.write.parse.WriteResult;
import uds.write.parse.WriteStatus;

class AiAdvisorAndSanitizerTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("docs").resolve("uds_catalog.xlsx");

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
    assertEquals(List.of("0x1001", "0x1002", "0x1007"), nd.runtimeExecutableDids());
    assertFalse(nd.runtimeExecutableDids().contains("0x1003"));
    assertFalse(nd.runtimeExecutableDids().contains("0xF186"));

    var flip =
        suggestions.suggest(catalog, "FLIPSKY_75100_VESC", "갑자기 정지");
    assertEquals(List.of("0x1001"), flip.runtimeExecutableDids());

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
        new uds.ai.provider.ExternalAiClient(
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
