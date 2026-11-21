package uds.access;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import uds.analysis.AccessEvidence;
import uds.analysis.SafeExchangeRecorder;
import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;
import uds.access.DemoSeedKeyAlgorithm;
import uds.access.AccessSnapshot;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;

/** SecurityAccess (0x27) — FR-24, NFR-18. Seed bytes never taken from redacted responses. */
public final class SecurityAccessService {

  private final ActiveDiagnosticContext context;
  private final DiagnosticExchangePort port;
  private final SafeExchangeRecorder recorder;
  private final String analysisSessionId;

  public SecurityAccessService(
      ActiveDiagnosticContext context,
      DiagnosticExchangePort port,
      SafeExchangeRecorder recorder,
      String analysisSessionId) {
    this.context = Objects.requireNonNull(context, "context");
    this.port = Objects.requireNonNull(port, "port");
    this.recorder = Objects.requireNonNull(recorder, "recorder");
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
  }

  public AccessOutcome requestSeed() {
    AccessSnapshot before = context.snapshot();
    long gen = before.generation();
    ExchangeRequest request =
        new ExchangeRequest(
            analysisSessionId,
            "req-" + UUID.randomUUID(),
            before.profileId(),
            "0x27",
            null,
            "27 01",
            gen);
    ExchangeResponse response = port.exchange(request);
    if (context.snapshot().generation() != gen) {
      return AccessOutcome.stale(context.snapshot().accessState());
    }

    String hex = response.hex() == null ? "" : response.hex().toUpperCase(Locale.ROOT);
    String error = null;
    DiagnosticAccessState next = before.accessState();
    String kind = response.kind();

    if (hex.startsWith("67 01")) {
      kind = "POSITIVE";
      next = DiagnosticAccessState.EXTENDED_LOCKED;
    } else if (hex.startsWith("7F 27")) {
      kind = "NEGATIVE";
      byte[] rsp = parseHex(hex);
      error = rsp.length >= 3 ? mapSecurityNrc(rsp[2] & 0xFF) : "NEGATIVE_RESPONSE";
      next = DiagnosticAccessState.EXTENDED_LOCKED;
    } else {
      error = "UNEXPECTED_RESPONSE";
    }

    boolean applied = context.tryApplyState(gen, next);
    if (!applied) {
      return AccessOutcome.stale(context.snapshot().accessState());
    }
    String evidenceId = "ACCESS:seed-" + UUID.randomUUID();
    recorder.recordAccessEvidence(
        new AccessEvidence(
            evidenceId, analysisSessionId, "REQUEST_SEED", kind, error, next.name(), true));
    return AccessOutcome.of(true, next, error, evidenceId, kind, response.hex());
  }

  /**
   * Computes demo HMAC key and sends 27 02. Password chars are cleared in {@code finally}.
   */
  public AccessOutcome sendKey(char[] password) {
    Objects.requireNonNull(password, "password");
    byte[] key = null;
    try {
      AccessSnapshot before = context.snapshot();
      long gen = before.generation();
      key = DemoSeedKeyAlgorithm.computeKey(password, DemoSeedKeyAlgorithm.DEMO_SEED, before.profileId());
      String keyHex =
          String.format(
              Locale.ROOT,
              "27 02 %02X %02X %02X %02X",
              key[0] & 0xFF,
              key[1] & 0xFF,
              key[2] & 0xFF,
              key[3] & 0xFF);
      ExchangeRequest request =
          new ExchangeRequest(
              analysisSessionId,
              "req-" + UUID.randomUUID(),
              before.profileId(),
              "0x27",
              null,
              keyHex,
              gen);
      ExchangeResponse response = port.exchange(request);
      if (context.snapshot().generation() != gen) {
        return AccessOutcome.stale(context.snapshot().accessState());
      }

      String hex = response.hex() == null ? "" : response.hex().toUpperCase(Locale.ROOT);
      String error = null;
      DiagnosticAccessState next = DiagnosticAccessState.EXTENDED_LOCKED;
      String kind = response.kind();

      if (hex.startsWith("67 02") || "67 02".equals(hex.trim())) {
        kind = "POSITIVE";
        next = DiagnosticAccessState.EXTENDED_UNLOCKED;
      } else if (hex.startsWith("7F 27")) {
        kind = "NEGATIVE";
        byte[] rsp = parseHex(hex);
        error = rsp.length >= 3 ? mapSecurityNrc(rsp[2] & 0xFF) : "NEGATIVE_RESPONSE";
        next = DiagnosticAccessState.EXTENDED_LOCKED;
      } else if (hex.isEmpty()) {
        // Wrong key against POSITIVE CSV row yields empty from simulator
        kind = "NEGATIVE";
        error = "INVALID_KEY";
        next = DiagnosticAccessState.EXTENDED_LOCKED;
      } else {
        error = "UNEXPECTED_RESPONSE";
      }

      boolean applied = context.tryApplyState(gen, next);
      if (!applied) {
        return AccessOutcome.stale(context.snapshot().accessState());
      }
      String evidenceId = "ACCESS:key-" + UUID.randomUUID();
      recorder.recordAccessEvidence(
          new AccessEvidence(
              evidenceId, analysisSessionId, "SEND_KEY", kind, error, next.name(), true));
      return AccessOutcome.of(true, next, error, evidenceId, kind, response.hex());
    } finally {
      Arrays.fill(password, '\0');
      if (key != null) {
        Arrays.fill(key, (byte) 0);
      }
    }
  }

  private static String mapSecurityNrc(int nrc) {
    return switch (nrc) {
      case 0x24 -> "REQUEST_SEQUENCE_ERROR";
      case 0x35 -> "INVALID_KEY";
      case 0x36 -> "EXCEED_NUMBER_OF_ATTEMPTS";
      case 0x37 -> "REQUIRED_TIME_DELAY_NOT_EXPIRED";
      default -> String.format(Locale.ROOT, "NRC_%02X", nrc);
    };
  }

  private static byte[] parseHex(String hex) {
    if (hex == null || hex.isBlank() || hex.contains("<")) {
      return new byte[0];
    }
    String compact = hex.replace(" ", "").replace("_", "").toUpperCase(Locale.ROOT);
    if (compact.isEmpty() || compact.length() % 2 != 0) {
      return new byte[0];
    }
    byte[] out = new byte[compact.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
