package uds.access;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import uds.analysis.AccessEvidence;
import uds.analysis.SafeExchangeRecorder;
import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;
import uds.access.AccessSnapshot;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;

/** DiagnosticSessionControl (0x10) — FR-23. */
public final class SessionControlService {

  private final ActiveDiagnosticContext context;
  private final DiagnosticExchangePort port;
  private final SafeExchangeRecorder recorder;
  private final String analysisSessionId;
  private final TesterPresentKeepAlive keepAlive;

  public SessionControlService(
      ActiveDiagnosticContext context,
      DiagnosticExchangePort port,
      SafeExchangeRecorder recorder,
      String analysisSessionId) {
    this(context, port, recorder, analysisSessionId, null);
  }

  public SessionControlService(
      ActiveDiagnosticContext context,
      DiagnosticExchangePort port,
      SafeExchangeRecorder recorder,
      String analysisSessionId,
      TesterPresentKeepAlive keepAlive) {
    this.context = Objects.requireNonNull(context, "context");
    this.port = Objects.requireNonNull(port, "port");
    this.recorder = Objects.requireNonNull(recorder, "recorder");
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    this.keepAlive = keepAlive;
  }

  public AccessOutcome enterExtended() {
    AccessSnapshot before = context.snapshot();
    if (!before.profileSelected()) {
      return AccessOutcome.of(
          false, before.accessState(), "PROFILE_NOT_SELECTED", null, null, null);
    }
    long gen = before.generation();
    ExchangeRequest request =
        new ExchangeRequest(
            analysisSessionId,
            "req-" + UUID.randomUUID(),
            before.profileId(),
            "0x10",
            null,
            "10 03",
            gen);
    ExchangeResponse response = port.exchange(request);
    if (context.snapshot().generation() != gen) {
      return AccessOutcome.stale(context.snapshot().accessState());
    }

    byte[] rsp = parseHex(response.hex());
    String kind = response.kind();
    String error = null;
    DiagnosticAccessState next = DiagnosticAccessState.DEFAULT_LOCKED;

    if (rsp.length >= 2 && (rsp[0] & 0xFF) == 0x50) {
      int sessionType = rsp[1] & 0x7F;
      if (sessionType == 0x03) {
        next = DiagnosticAccessState.EXTENDED_LOCKED;
        kind = "POSITIVE";
      } else {
        error = "SESSION_ECHO_MISMATCH";
        next = DiagnosticAccessState.DEFAULT_LOCKED;
        kind = "POSITIVE";
      }
    } else if (rsp.length >= 3 && (rsp[0] & 0xFF) == 0x7F) {
      kind = "NEGATIVE";
      error = mapSessionNrc(rsp[2] & 0xFF);
      next = DiagnosticAccessState.DEFAULT_LOCKED;
    } else {
      error = "UNEXPECTED_RESPONSE";
      next = DiagnosticAccessState.DEFAULT_LOCKED;
    }

    boolean applied = context.tryApplyState(gen, next);
    if (!applied) {
      return AccessOutcome.stale(context.snapshot().accessState());
    }
    String evidenceId = "ACCESS:session-" + UUID.randomUUID();
    recorder.recordAccessEvidence(
        new AccessEvidence(
            evidenceId,
            analysisSessionId,
            "EXTENDED_SESSION",
            kind,
            error,
            next.name(),
            true));
    syncKeepAlive(next, response.hex());
    return AccessOutcome.of(true, next, error, evidenceId, kind, response.hex());
  }

  /** DiagnosticSessionControl defaultSession ({@code 10 01}) — returns to DEFAULT_LOCKED. */
  public AccessOutcome enterDefault() {
    AccessSnapshot before = context.snapshot();
    if (!before.profileSelected()) {
      return AccessOutcome.of(
          false, before.accessState(), "PROFILE_NOT_SELECTED", null, null, null);
    }
    long gen = before.generation();
    ExchangeRequest request =
        new ExchangeRequest(
            analysisSessionId,
            "req-" + UUID.randomUUID(),
            before.profileId(),
            "0x10",
            null,
            "10 01",
            gen);
    ExchangeResponse response = port.exchange(request);
    if (context.snapshot().generation() != gen) {
      return AccessOutcome.stale(context.snapshot().accessState());
    }

    byte[] rsp = parseHex(response.hex());
    String kind = response.kind();
    String error = null;
    DiagnosticAccessState next = DiagnosticAccessState.DEFAULT_LOCKED;

    if (rsp.length >= 2 && (rsp[0] & 0xFF) == 0x50) {
      int sessionType = rsp[1] & 0x7F;
      if (sessionType == 0x01) {
        next = DiagnosticAccessState.DEFAULT_LOCKED;
        kind = "POSITIVE";
      } else {
        error = "SESSION_ECHO_MISMATCH";
        kind = "POSITIVE";
      }
    } else if (rsp.length >= 3 && (rsp[0] & 0xFF) == 0x7F) {
      kind = "NEGATIVE";
      error = mapSessionNrc(rsp[2] & 0xFF);
    } else {
      error = "UNEXPECTED_RESPONSE";
    }

    boolean applied = context.tryApplyState(gen, next);
    if (!applied) {
      return AccessOutcome.stale(context.snapshot().accessState());
    }
    String evidenceId = "ACCESS:session-" + UUID.randomUUID();
    recorder.recordAccessEvidence(
        new AccessEvidence(
            evidenceId,
            analysisSessionId,
            "DEFAULT_SESSION",
            kind,
            error,
            next.name(),
            true));
    syncKeepAlive(next, response.hex());
    return AccessOutcome.of(true, next, error, evidenceId, kind, response.hex());
  }

  private void syncKeepAlive(DiagnosticAccessState next, String responseHex) {
    if (keepAlive == null) {
      return;
    }
    if (next == DiagnosticAccessState.EXTENDED_LOCKED
        || next == DiagnosticAccessState.EXTENDED_UNLOCKED) {
      SessionTiming timing =
          SessionTiming.fromPositiveSessionHex(responseHex).orElse(SessionTiming.DEFAULT);
      keepAlive.start(timing);
    } else {
      keepAlive.stop();
    }
  }

  private static String mapSessionNrc(int nrc) {
    return switch (nrc) {
      case 0x22 -> "CONDITIONS_NOT_CORRECT";
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
