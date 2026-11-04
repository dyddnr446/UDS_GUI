package uds.read.service;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import uds.access.guard.DiagnosticAccessGuard;
import uds.access.guard.GuardDecision;
import uds.access.state.AccessSnapshot;
import uds.access.state.ActiveDiagnosticContext;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.DidDefinition;
import uds.exchange.api.DiagnosticExchangePort;
import uds.exchange.api.ExchangeRequest;
import uds.exchange.api.ExchangeResponse;
import uds.read.finding.Finding;
import uds.read.finding.FindingAnalyzer;
import uds.read.finding.FindingDetailCode;
import uds.read.finding.ReadDataResult;
import uds.read.finding.ReadStatus;
import uds.read.parse.DidValueParser;
import uds.read.parse.ParsedDidValue;
import uds.read.request.ReadDataRequestGenerator;

/** ReadDataByIdentifier application service (FR-14~16). */
public final class ReadDataService {

  private final ActiveDiagnosticContext context;
  private final DiagnosticAccessGuard guard;
  private final UdsCatalog catalog;
  private final DiagnosticExchangePort port;
  private final String analysisSessionId;
  private final DidValueParser parser = new DidValueParser();
  private final FindingAnalyzer analyzer = new FindingAnalyzer();
  private final AtomicInteger portCallCount = new AtomicInteger();

  public ReadDataService(
      ActiveDiagnosticContext context,
      DiagnosticAccessGuard guard,
      UdsCatalog catalog,
      DiagnosticExchangePort port,
      String analysisSessionId) {
    this.context = Objects.requireNonNull(context, "context");
    this.guard = Objects.requireNonNull(guard, "guard");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.port = Objects.requireNonNull(port, "port");
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
  }

  public int portCallCount() {
    return portCallCount.get();
  }

  public void resetPortCallCount() {
    portCallCount.set(0);
  }

  public ReadDataResult read(String did) {
    Objects.requireNonNull(did, "did");
    AccessSnapshot snap = context.snapshot();
    if (guard.check(did, DiagnosticAccessGuard.Operation.READ) != GuardDecision.ALLOW) {
      Finding finding =
          new Finding(did, "", ReadStatus.ACCESS_DENIED, FindingDetailCode.NONE, "GUARD");
      return new ReadDataResult(
          null, null, did, null, null, "", ReadStatus.ACCESS_DENIED, FindingDetailCode.NONE, null, finding);
    }

    DidDefinition def =
        findDid(did)
            .orElseThrow(() -> new IllegalArgumentException("DID not in catalog: " + did));
    String requestHex = ReadDataRequestGenerator.toHex22(did);
    String requestId = "req-" + UUID.randomUUID();
    ExchangeRequest request =
        new ExchangeRequest(
            analysisSessionId,
            requestId,
            snap.profileId(),
            "0x22",
            did,
            requestHex,
            snap.generation());
    portCallCount.incrementAndGet();
    ExchangeResponse response = port.exchange(request);

    ParsedDidValue parsed = parser.parse(response.hex(), did, def);
    Finding finding = analyzer.analyze(parsed, def);
    return new ReadDataResult(
        requestId,
        response.responseId(),
        did,
        requestHex,
        response.hex(),
        finding.displayValue(),
        finding.status(),
        finding.detailCode(),
        parsed.nrcCode(),
        finding);
  }

  /** Parser-only entry for UC-04 static log frames (no Port). */
  public ReadDataResult parseFrame(String responseHex, String did) {
    DidDefinition def =
        findDid(did)
            .orElseThrow(() -> new IllegalArgumentException("DID not in catalog: " + did));
    ParsedDidValue parsed = parser.parse(responseHex, did, def);
    Finding finding = analyzer.analyze(parsed, def);
    return new ReadDataResult(
        null,
        null,
        did,
        null,
        responseHex,
        finding.displayValue(),
        finding.status(),
        finding.detailCode(),
        parsed.nrcCode(),
        finding);
  }

  private Optional<DidDefinition> findDid(String did) {
    String norm = normalizeDid(did);
    return catalog.didDefinitions().stream()
        .filter(d -> normalizeDid(d.id()).equals(norm))
        .findFirst();
  }

  private static String normalizeDid(String did) {
    String s = did.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("0X")) {
      s = s.substring(2);
    }
    return s;
  }
}
