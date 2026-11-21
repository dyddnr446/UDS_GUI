package uds.read;

import java.util.Locale;

/** Builds Diagnostic ReadDataByIdentifier (0x22) request hex. */
public final class ReadDataRequestGenerator {

  private ReadDataRequestGenerator() {}

  public static String toHex22(String did) {
    int value = parseDid(did);
    int hi = (value >> 8) & 0xFF;
    int lo = value & 0xFF;
    return String.format(Locale.ROOT, "22 %02X %02X", hi, lo);
  }

  public static int parseDid(String did) {
    if (did == null || did.isBlank()) {
      throw new IllegalArgumentException("did blank");
    }
    String s = did.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("0X")) {
      s = s.substring(2);
    }
    return Integer.parseInt(s, 16);
  }
}
