package uds.io;

import java.util.Locale;
import java.util.Optional;

/** Normalizes HEX strings; rejects odd length and non-HEX (FR-02). */
public final class HexNormalizer {

  private HexNormalizer() {}

  public static Optional<byte[]> normalize(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String compact = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    if (compact.isEmpty()) {
      return Optional.empty();
    }
    if ((compact.length() & 1) != 0) {
      return Optional.empty();
    }
    byte[] out = new byte[compact.length() / 2];
    for (int i = 0; i < out.length; i++) {
      int hi = Character.digit(compact.charAt(i * 2), 16);
      int lo = Character.digit(compact.charAt(i * 2 + 1), 16);
      if (hi < 0 || lo < 0) {
        return Optional.empty();
      }
      out[i] = (byte) ((hi << 4) | lo);
    }
    return Optional.of(out);
  }

  public static String reasonIfInvalid(String raw) {
    if (raw == null) {
      return "null hex";
    }
    String compact = raw.replaceAll("\\s+", "");
    if (compact.isEmpty()) {
      return "empty hex";
    }
    if ((compact.length() & 1) != 0) {
      return "odd hex length";
    }
    for (int i = 0; i < compact.length(); i++) {
      if (Character.digit(compact.charAt(i), 16) < 0) {
        return "non-hex character";
      }
    }
    return "invalid hex";
  }
}
