package uds.exchange.recording;

import java.util.Locale;
import java.util.Objects;
import uds.analysis.record.SafeExchangeRecorder;
import uds.exchange.api.DiagnosticExchangePort;
import uds.exchange.api.ExchangeRequest;
import uds.exchange.api.ExchangeResponse;

/**
 * Decorator that unifies exchange recording. Records 0x10/0x22/0x2E raw to JSONL; for 0x27 discards
 * raw payload and returns masked metadata only (FR-28, NFR-20).
 */
public final class RecordingDiagnosticExchangePort implements DiagnosticExchangePort {

  private final DiagnosticExchangePort delegate;
  private final SafeExchangeRecorder recorder;

  public RecordingDiagnosticExchangePort(
      DiagnosticExchangePort delegate, SafeExchangeRecorder recorder) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.recorder = Objects.requireNonNull(recorder, "recorder");
  }

  @Override
  public ExchangeResponse exchange(ExchangeRequest request) {
    Objects.requireNonNull(request, "request");
    int sid = requestSid(request.hex());
    ExchangeResponse raw = delegate.exchange(request);

    if (sid == 0x27) {
      // Do not persist raw request/response hex. Return masked meta only.
      String maskedReqHex = maskSecurityHex(request.hex());
      String maskedRspHex = maskSecurityHex(raw.hex());
      // Intentionally skip recordExchange — JSONL must not contain seed/key bytes.
      return raw.withHexAndRedacted(maskedRspHex, true);
    }

    if (sid == 0x10 || sid == 0x22 || sid == 0x2E) {
      recorder.recordExchange(request, raw);
      return raw;
    }

    // Other SIDs: pass through without JSONL (guardrail lists only 10/22/2E).
    return raw;
  }

  private static int requestSid(String hex) {
    byte[] bytes = parseCompact(hex);
    return bytes.length == 0 ? -1 : bytes[0] & 0xFF;
  }

  /**
   * Masks 0x27/0x67 seed and key payloads as {@code <REDACTED>} while keeping SID/sub-function
   * headers when present.
   */
  static String maskSecurityHex(String hex) {
    byte[] bytes = parseCompact(hex);
    if (bytes.length == 0) {
      return "";
    }
    int sid = bytes[0] & 0xFF;
    if (sid == 0x27) {
      if (bytes.length >= 2 && (bytes[1] & 0xFF) == 0x01) {
        return "27 01 <REDACTED>";
      }
      if (bytes.length >= 2 && (bytes[1] & 0xFF) == 0x02) {
        return "27 02 <REDACTED>";
      }
      return "27 <REDACTED>";
    }
    if (sid == 0x67) {
      if (bytes.length >= 2 && (bytes[1] & 0xFF) == 0x01) {
        return "67 01 <REDACTED>";
      }
      if (bytes.length >= 2 && (bytes[1] & 0xFF) == 0x02) {
        return "67 02";
      }
      return "67 <REDACTED>";
    }
    if (sid == 0x7F && bytes.length >= 2 && (bytes[1] & 0xFF) == 0x27) {
      // Negative response for security access — NRC only, no seed/key
      return formatHex(bytes);
    }
    return formatHex(bytes);
  }

  private static byte[] parseCompact(String hex) {
    String compact =
        hex == null ? "" : hex.replace(" ", "").replace("_", "").toUpperCase(Locale.ROOT);
    if (compact.isEmpty() || compact.contains("<")) {
      // Already masked or empty
      if (hex == null) {
        return new byte[0];
      }
      // Try to parse only leading hex tokens before <REDACTED>
      String cleaned = hex.toUpperCase(Locale.ROOT).replace("<REDACTED>", "").trim();
      StringBuilder digits = new StringBuilder();
      for (int i = 0; i < cleaned.length(); i++) {
        char c = cleaned.charAt(i);
        if (Character.isDigit(c) || (c >= 'A' && c <= 'F')) {
          digits.append(c);
        }
      }
      if (digits.length() == 0 || digits.length() % 2 != 0) {
        return new byte[0];
      }
      byte[] out = new byte[digits.length() / 2];
      for (int i = 0; i < out.length; i++) {
        out[i] = (byte) Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16);
      }
      return out;
    }
    byte[] out = new byte[compact.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  private static String formatHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 3);
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
    }
    return sb.toString();
  }
}
