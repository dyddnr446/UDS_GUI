package uds.interpreter.decode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import uds.catalog.load.UdsCatalog;
import uds.interpreter.model.DecodeKind;

/** Deep field decode for demo SIDs (FR-07). Security payloads are redacted (NFR-20). */
public final class SidDecoder {

  public static final String REDACTED = "<REDACTED>";

  private SidDecoder() {}

  public static Map<String, String> decodeFields(
      int sidByte, byte[] bytes, DecodeKind kind, UdsCatalog catalog) {
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
      case 0x27 -> decodeSecurity(bytes, kind, fields);
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
      fields.put("p2ServerMax", Integer.toString(p2));
      fields.put("p2StarServerMax", Integer.toString(p2star));
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
      fields.put("dataAscii", toPrintableAscii(data));
    }
  }

  private static void decodeSecurity(byte[] bytes, DecodeKind kind, Map<String, String> fields) {
    if (bytes.length >= 2) {
      int access = subValue(bytes[1], true);
      fields.put("accessType", hexByte(access));
      fields.put("securityOperation", (access & 1) == 1 ? "requestSeed" : "sendKey");
    }
    if (bytes.length > 2) {
      // Never store raw seed/key bytes or HEX (NFR-20).
      fields.put("securityPayload", REDACTED);
      if ((subValue(bytes[1], true) & 1) == 1 && kind == DecodeKind.POSITIVE) {
        fields.put("seed", REDACTED);
      }
      if ((subValue(bytes[1], true) & 1) == 0 && kind == DecodeKind.REQUEST) {
        fields.put("key", REDACTED);
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

  private static String toPrintableAscii(byte[] data) {
    return new String(data, StandardCharsets.US_ASCII).replaceAll("[\\p{Cntrl}]", ".");
  }
}
