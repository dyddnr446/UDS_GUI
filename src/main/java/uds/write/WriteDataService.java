package uds.write;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import uds.access.DiagnosticAccessGuard;
import uds.access.GuardDecision;
import uds.access.AccessSnapshot;
import uds.access.ActiveDiagnosticContext;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.DidDefinition;
import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;
import uds.write.DidValueEncoder;
import uds.write.WriteResponseParser;
import uds.write.WriteResult;
import uds.write.WriteStatus;
import uds.write.WriteDataRequestGenerator;
import uds.write.WriteValueValidator;
import uds.write.WriteValueValidator.ValidationResult;

/** WriteDataByIdentifier application service (FR-21). */
public final class WriteDataService {

  private final ActiveDiagnosticContext context;
  private final DiagnosticAccessGuard guard;
  private final UdsCatalog catalog;
  private final DiagnosticExchangePort port;
  private final String analysisSessionId;
  private final WriteValueValidator validator = new WriteValueValidator();
  private final DidValueEncoder encoder = new DidValueEncoder();
  private final WriteResponseParser parser = new WriteResponseParser();
  private final AtomicInteger portCallCount = new AtomicInteger();

  public WriteDataService(
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

  public WriteResult write(String did, String inputValue) {
    Objects.requireNonNull(did, "did");
    AccessSnapshot snap = context.snapshot();
    if (guard.check(did, DiagnosticAccessGuard.Operation.WRITE) != GuardDecision.ALLOW) {
      return new WriteResult(
          null, null, did, inputValue, "", "", "", WriteStatus.ACCESS_DENIED, "GUARD");
    }

    DidDefinition def =
        findDid(did)
            .orElseThrow(() -> new IllegalArgumentException("DID not in catalog: " + did));
    ValidationResult validation = validator.validate(def, inputValue);
    if (validation.failed()) {
      return new WriteResult(
          null,
          null,
          did,
          inputValue,
          "",
          "",
          "",
          validation.error().orElseThrow(),
          "VALIDATOR");
    }

    byte[] data = encoder.encode(def, validation.value().orElseThrow());
    String encodedHex = DidValueEncoder.toSpacedHex(data);
    String requestHex = WriteDataRequestGenerator.toHex2E(did, data);
    String requestId = "req-" + UUID.randomUUID();
    ExchangeRequest request =
        new ExchangeRequest(
            analysisSessionId,
            requestId,
            snap.profileId(),
            "0x2E",
            did,
            requestHex,
            snap.generation());
    portCallCount.incrementAndGet();
    ExchangeResponse response = port.exchange(request);
    WriteStatus status = parser.parse(response.hex(), did);
    return new WriteResult(
        requestId,
        response.responseId(),
        did,
        inputValue,
        encodedHex,
        requestHex,
        response.hex(),
        status,
        status.name());
  }

  private Optional<DidDefinition> findDid(String did) {
    String norm = normalizeDid(did);
    return catalog.getPayload().didDefinitions().stream()
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
