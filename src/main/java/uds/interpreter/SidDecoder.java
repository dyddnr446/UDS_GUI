package uds.interpreter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import uds.catalog.load.UdsCatalog;

/** Deep field decode for demo SIDs (FR-07). Security payloads are redacted (NFR-20). */
public final class SidDecoder {

  public static final String REDACTED = "<REDACTED>";

  private SidDecoder() {}

  public static Map<String, String> decodeFields(
      int sidByte, byte[] bytes, DecodeKind kind, UdsCatalog catalog) {
    return decodeFields(sidByte, bytes, kind, catalog, false);
  }

  /**
   * @param revealSecurity when true (Learning mode), seed/key HEX are shown instead of {@link
   *     #REDACTED}
   */
  public static Map<String, String> decodeFields(
      int sidByte, byte[] bytes, DecodeKind kind, UdsCatalog catalog, boolean revealSecurity) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (bytes == null || bytes.length == 0) {
      return fields;
    }

    int reqSid = kind == DecodeKind.POSITIVE ? (sidByte - 0x40) & 0xFF : sidByte & 0xFF;
    if (kind == DecodeKind.NEGATIVE) {
      return fields;
    }

    switch (reqSid) {
      case 0x10 -> decodeSession(bytes, kind, fields);
      case 0x11 -> decodeEcuReset(bytes, kind, fields);
      case 0x14 -> decodeClearDtc(bytes, kind, fields);
      case 0x19 -> decodeReadDtc(bytes, kind, fields);
      case 0x22, 0x2E -> decodeDid(bytes, kind, fields);
      case 0x27 -> decodeSecurity(bytes, kind, fields, revealSecurity);
      case 0x3E -> decodeTesterPresent(bytes, kind, fields);
      default -> {
        /* leave empty beyond parser-owned names */
      }
    }
    return fields;
  }

  private static void decodeSession(byte[] bytes, DecodeKind kind, Map<String, String> fields) {
    if (bytes.length >= 2) {
      fields.put("sessionType", hexByte(subValue(bytes[1], true)));
    }
    if (kind == DecodeKind.POSITIVE && bytes.length >= 6) {
      int p2 = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
      int p2star = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
      fields.put("p2ServerMax", hexWord(p2));
      fields.put("p2StarServerMax", hexWord(p2star));
    }
  }

  private static void decodeEcuReset(byte[] bytes, DecodeKind kind, Map<String, String> fields) {
    if (bytes.length >= 2) {
      fields.put("resetType", hexByte(subValue(bytes[1], true)));
    }
  }

  private static void decodeClearDtc(byte[] bytes, DecodeKind kind, Map<String, String> fields) {
    if (kind == DecodeKind.REQUEST && bytes.length >= 4) {
      int group =
          ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
      fields.put("groupOfDTC", String.format(Locale.ROOT, "0x%06X", group));
    }
  }

  private static void decodeReadDtc(byte[] bytes, DecodeKind kind, Map<String, String> fields) {
    if (bytes.length >= 2) {
      fields.put("reportType", hexByte(subValue(bytes[1], true)));
    }
    if (kind == DecodeKind.REQUEST && bytes.length >= 3) {
      fields.put("statusMask", hexByte(bytes[2] & 0xFF));
    }
    if (kind == DecodeKind.POSITIVE && bytes.length >= 3) {
      fields.put("statusAvailabilityMask", hexByte(bytes[2] & 0xFF));
    }
    if (kind == DecodeKind.POSITIVE && bytes.length >= 7) {
      int dtc =
          ((bytes[3] & 0xFF) << 16) | ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
      fields.put("dtc", String.format(Locale.ROOT, "0x%06X", dtc));
      fields.put("dtcStatus", hexByte(bytes[6] & 0xFF));
    }
  }

  private static void decodeDid(byte[] bytes, DecodeKind kind, Map<String, String> fields) {
    if (bytes.length >= 3) {
      int did = ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
      fields.put("did", String.format(Locale.ROOT, "0x%04X", did));
    }
    if (kind == DecodeKind.POSITIVE && bytes.length > 3) {
      byte[] data = new byte[bytes.length - 3];
      System.arraycopy(bytes, 3, data, 0, data.length);
      fields.put("dataHex", toHex(data));
      // Binary DID payloads look like mojibake as ASCII — only show when mostly text.
      asciiPreview(data).ifPresent(ascii -> fields.put("dataAscii", ascii));
    }
  }

  private static void decodeSecurity(
      byte[] bytes, DecodeKind kind, Map<String, String> fields, boolean revealSecurity) {
    if (bytes.length >= 2) {
      int access = subValue(bytes[1], true);
      fields.put("accessType", hexByte(access));
      fields.put("securityOperation", (access & 1) == 1 ? "requestSeed" : "sendKey");
    }
    if (bytes.length > 2) {
      byte[] payload = new byte[bytes.length - 2];
      System.arraycopy(bytes, 2, payload, 0, payload.length);
      String shown = revealSecurity ? toHex(payload) : REDACTED;
      fields.put("securityPayload", shown);
      if ((subValue(bytes[1], true) & 1) == 1 && kind == DecodeKind.POSITIVE) {
        fields.put("seed", shown);
      }
      if ((subValue(bytes[1], true) & 1) == 0 && kind == DecodeKind.REQUEST) {
        fields.put("key", shown);
      }
    }
  }

  private static void decodeTesterPresent(byte[] bytes, DecodeKind kind, Map<String, String> fields) {
    if (bytes.length >= 2) {
      int raw = bytes[1] & 0xFF;
      fields.put("zeroSubFunction", hexByte(raw & 0x7F));
      fields.put("suppressBit", Boolean.toString((raw & 0x80) != 0));
    }
  }

  private static int subValue(byte b, boolean mask) {
    int v = b & 0xFF;
    return mask ? (v & 0x7F) : v;
  }

  private static String hexByte(int v) {
    return String.format(Locale.ROOT, "0x%02X", v & 0xFF);
  }

  private static String hexWord(int v) {
    return String.format(Locale.ROOT, "0x%04X", v & 0xFFFF);
  }

  private static String toHex(byte[] data) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < data.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(String.format(Locale.ROOT, "%02X", data[i] & 0xFF));
    }
    return sb.toString();
  }

  /**
   * Returns an ASCII preview only when at least 75% of bytes are printable ({@code 0x20..0x7E}).
   * Non-printable bytes become {@code '.'}; otherwise empty (caller should rely on {@code dataHex}).
   */
  static Optional<String> asciiPreview(byte[] data) {
    if (data == null || data.length == 0) {
      return Optional.empty();
    }
    int printable = 0;
    StringBuilder sb = new StringBuilder(data.length);
    for (byte b : data) {
      int v = b & 0xFF;
      if (v >= 0x20 && v <= 0x7E) {
        printable++;
        sb.append((char) v);
      } else {
        sb.append('.');
      }
    }
    if (printable * 100 < data.length * 75) {
      return Optional.empty();
    }
    return Optional.of(sb.toString());
  }
}
