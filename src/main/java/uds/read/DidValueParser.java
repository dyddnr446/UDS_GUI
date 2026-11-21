package uds.read;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.DidDefinition;
import uds.read.ReadStatus;
import uds.read.ReadDataRequestGenerator;

/**
 * Shared 0x62 / 7F 22 parser for live File Port responses and static log frames (FR-15).
 * runtime_enabled is ignored.
 */
public final class DidValueParser {

  public ParsedDidValue parse(String responseHex, String expectedDid, DidDefinition def) {
    Objects.requireNonNull(def, "def");
    byte[] bytes = parseHex(responseHex);
    if (bytes.length == 0) {
      return new ParsedDidValue(
          expectedDid, ReadStatus.PARSE_ERROR_LENGTH, Optional.empty(), "", null, responseHex);
    }

    int sid = bytes[0] & 0xFF;
    if (sid == 0x7F) {
      String nrc = bytes.length >= 3 ? String.format(Locale.ROOT, "%02X", bytes[2] & 0xFF) : null;
      return new ParsedDidValue(
          expectedDid, ReadStatus.REJECTED, Optional.empty(), "", nrc, responseHex);
    }
    if (sid != 0x62 || bytes.length < 3) {
      return new ParsedDidValue(
          expectedDid, ReadStatus.PARSE_ERROR_LENGTH, Optional.empty(), "", null, responseHex);
    }

    int echoDid = ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
    int expected = ReadDataRequestGenerator.parseDid(expectedDid);
    if (echoDid != expected) {
      return new ParsedDidValue(
          expectedDid,
          ReadStatus.PARSE_ERROR_DID_MISMATCH,
          Optional.empty(),
          "",
          null,
          responseHex);
    }

    byte[] payload = new byte[bytes.length - 3];
    System.arraycopy(bytes, 3, payload, 0, payload.length);

    String type = def.dataType() == null ? "" : def.dataType().toUpperCase(Locale.ROOT);
    boolean referenceOnly =
        def.lengthPolicy() != null && def.lengthPolicy().toUpperCase(Locale.ROOT).contains("REFERENCE");
    if (!referenceOnly && payload.length != def.byteLength()) {
      return new ParsedDidValue(
          expectedDid, ReadStatus.PARSE_ERROR_LENGTH, Optional.empty(), "", null, responseHex);
    }
    if (referenceOnly && "ASCII".equals(type) && payload.length == 0) {
      return new ParsedDidValue(
          expectedDid, ReadStatus.PARSE_ERROR_LENGTH, Optional.empty(), "", null, responseHex);
    }

    if ("ASCII".equals(type)) {
      String ascii = new String(payload, StandardCharsets.US_ASCII).replace("\0", "").trim();
      return new ParsedDidValue(
          expectedDid, null, Optional.empty(), ascii, null, responseHex);
    }

    long raw = decodeInteger(payload, type);
    BigDecimal scale = def.scale() == null ? BigDecimal.ONE : def.scale();
    BigDecimal offset = def.offset() == null ? BigDecimal.ZERO : def.offset();
    BigDecimal physical = BigDecimal.valueOf(raw).multiply(scale).add(offset);
    String display = formatDisplay(physical, scale, def.unit());
    return new ParsedDidValue(
        expectedDid, null, Optional.of(physical), display, null, responseHex);
  }

  private static long decodeInteger(byte[] payload, String type) {
    return switch (type) {
      case "UINT8" -> payload[0] & 0xFFL;
      case "UINT16" -> ((payload[0] & 0xFFL) << 8) | (payload[1] & 0xFFL);
      case "UINT32" ->
          ((payload[0] & 0xFFL) << 24)
              | ((payload[1] & 0xFFL) << 16)
              | ((payload[2] & 0xFFL) << 8)
              | (payload[3] & 0xFFL);
      case "INT16" -> {
        int v = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        if ((v & 0x8000) != 0) {
          v |= 0xFFFF0000;
        }
        yield v;
      }
      case "INT32" ->
          (payload[0] << 24) | ((payload[1] & 0xFF) << 16) | ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
      default -> throw new IllegalArgumentException("unsupported data_type: " + type);
    };
  }

  static String formatDisplay(BigDecimal physical, BigDecimal scale, String unit) {
    int fractionDigits = Math.max(0, scale.stripTrailingZeros().scale());
    // scale 0.1 → scale()=1; 0.01 → 2; 0.001 → 3; 1 → 0
    if (scale.compareTo(BigDecimal.ONE) != 0) {
      fractionDigits = Math.max(fractionDigits, scale.scale());
    }
    BigDecimal rounded = physical.setScale(fractionDigits, RoundingMode.HALF_UP);
    String number = rounded.toPlainString();
    if (unit == null || unit.isBlank()) {
      return number;
    }
    return number + " " + unit;
  }

  private static byte[] parseHex(String hex) {
    if (hex == null || hex.isBlank()) {
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
