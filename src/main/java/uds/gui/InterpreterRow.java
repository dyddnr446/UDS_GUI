package uds.gui;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import uds.interpreter.SidDecoder;
import uds.interpreter.DecodeKind;
import uds.interpreter.DecodeResult;
import uds.io.LineError;

/**
 * UI-only table row wrapping {@link DecodeResult} or {@link LineError}. Does not alter domain
 * models.
 */
public final class InterpreterRow {

  private final DecodeResult decode;
  private final LineError error;
  private final boolean redacted;
  /** Original frame hex for detail pane; never mutate DecodeResult. */
  private final String hexRaw;

  private InterpreterRow(DecodeResult decode, LineError error, boolean redacted, String hexRaw) {
    this.decode = decode;
    this.error = error;
    this.redacted = redacted;
    this.hexRaw = hexRaw;
  }

  public static InterpreterRow ofDecode(DecodeResult result) {
    return ofDecode(result, null);
  }

  public static InterpreterRow ofDecode(DecodeResult result, String hexRaw) {
    Objects.requireNonNull(result, "result");
    return new InterpreterRow(result, null, detectRedacted(result), hexRaw);
  }

  public static InterpreterRow ofError(LineError err) {
    Objects.requireNonNull(err, "err");
    return new InterpreterRow(null, err, false, err.raw());
  }

  public boolean isError() {
    return error != null;
  }

  /** Filter "오류 줄만": CSV LineError + UDS Negative Response (NRC). */
  public boolean isProblem() {
    if (error != null) {
      return true;
    }
    return decode != null && decode.kind() == DecodeKind.NEGATIVE;
  }

  public boolean isRedacted() {
    return redacted;
  }

  /** TesterPresent keep-alive frames ({@code 0x3E}/{@code 0x7E}) — hidden at Normal log level. */
  public boolean isTesterPresent() {
    if (error != null || decode == null) {
      return false;
    }
    String s = sid().trim().toUpperCase(Locale.ROOT);
    return "0X3E".equals(s)
        || "3E".equals(s)
        || "0X7E".equals(s)
        || "7E".equals(s);
  }

  public DecodeResult decode() {
    return decode;
  }

  public LineError error() {
    return error;
  }

  public DecodeKind kind() {
    return decode == null ? null : decode.kind();
  }

  public String time() {
    if (error != null) {
      return "L" + error.lineNo();
    }
    String ref = decode.frameRef();
    int slash = ref.indexOf('/');
    return slash > 0 ? ref.substring(0, slash) : ref;
  }

  public String dir() {
    if (error != null) {
      return "ERR";
    }
    return switch (decode.kind()) {
      case REQUEST -> "REQ";
      case POSITIVE -> "RES";
      case NEGATIVE -> "NEG";
      case UNKNOWN -> "UNK";
    };
  }

  public String sid() {
    if (error != null) {
      return "";
    }
    return decode.sid() == null ? "" : decode.sid();
  }

  public String service() {
    if (error != null) {
      return "LineError";
    }
    return decode.serviceName() == null ? "" : decode.serviceName();
  }

  /** Smart column: subFunction → NRC → DID from fields. */
  public String subDidNrc() {
    if (error != null) {
      return error.reason() == null ? "" : error.reason();
    }
    if (decode.subFunctionName() != null && !decode.subFunctionName().isBlank()) {
      return decode.subFunctionName();
    }
    if (decode.nrcName() != null && !decode.nrcName().isBlank()) {
      return decode.nrcName();
    }
    if (decode.nrcCode() != null && !decode.nrcCode().isBlank()) {
      return decode.nrcCode();
    }
    Map<String, String> fields = decode.fields();
    if (fields.containsKey("did")) {
      return fields.get("did");
    }
    if (fields.containsKey("didId")) {
      return fields.get("didId");
    }
    return "";
  }

  public String data() {
    if (error != null) {
      String raw = error.raw() == null ? "" : error.raw();
      String reason = error.reason() == null ? "" : error.reason();
      return reason + (raw.isBlank() ? "" : " | " + raw);
    }
    StringJoiner joiner = new StringJoiner(", ");
    for (Map.Entry<String, String> e : decode.fields().entrySet()) {
      joiner.add(e.getKey() + "=" + e.getValue());
    }
    if (decode.suppressPosRsp() != null) {
      joiner.add("suppressPosRsp=" + decode.suppressPosRsp());
    }
    return joiner.toString();
  }

  /** Full detail for SplitPane pane (no column truncation). */
  public String detailText() {
    StringBuilder sb = new StringBuilder();
    sb.append("Time: ").append(time()).append('\n');
    sb.append("Dir: ").append(dir()).append('\n');
    sb.append("SID: ").append(sid()).append('\n');
    sb.append("Service: ").append(service()).append('\n');
    sb.append("Sub/DID/NRC: ").append(subDidNrc()).append('\n');
    sb.append("Data / Fields: ").append(data()).append('\n');
    sb.append("Raw Hex: ").append(safeHexDisplay());
    if (decode != null && decode.frameRef() != null) {
      sb.append('\n').append("FrameRef: ").append(decode.frameRef());
    }
    return sb.toString();
  }

  /** Tab-separated columns for clipboard copy. */
  public String copyTsv() {
    return String.join(
        "\t",
        nullToEmpty(time()),
        nullToEmpty(dir()),
        nullToEmpty(sid()),
        nullToEmpty(service()),
        nullToEmpty(subDidNrc()),
        nullToEmpty(data()),
        nullToEmpty(safeHexDisplay()));
  }

  String safeHexDisplay() {
    if (mustRedactHex()) {
      return SidDecoder.REDACTED;
    }
    if (hexRaw == null || hexRaw.isBlank()) {
      return "";
    }
    return hexRaw;
  }

  private boolean mustRedactHex() {
    if (redacted) {
      return true;
    }
    String s = sid() == null ? "" : sid().trim().toUpperCase(Locale.ROOT);
    return "0X27".equals(s) || "0X67".equals(s) || "27".equals(s) || "67".equals(s);
  }

  /** Stable text for headless filter tests (keeps ERR prefix for errors). */
  public String displayLine() {
    if (error != null) {
      return "ERR " + error;
    }
    return String.valueOf(decode);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static boolean detectRedacted(DecodeResult r) {
    String s = String.valueOf(r).toUpperCase(Locale.ROOT);
    if (s.contains(SidDecoder.REDACTED.toUpperCase(Locale.ROOT)) || s.contains("REDACTED")) {
      return true;
    }
    for (String v : r.fields().values()) {
      if (v != null && v.toUpperCase(Locale.ROOT).contains("REDACTED")) {
        return true;
      }
    }
    return false;
  }
}
