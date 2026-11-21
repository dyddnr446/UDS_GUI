package uds.access;

import java.util.Locale;
import java.util.Optional;

/**
 * Timing from DiagnosticSessionControl positive response ({@code 50 xx P2 P2*}).
 *
 * <p>ISO 14229-1: P2Server resolution 1&nbsp;ms; P2*Server resolution 10&nbsp;ms. Keep-alive uses
 * half of physical P2*.
 */
public record SessionTiming(int p2ServerMaxRaw, int p2StarServerMaxRaw) {

  public static final SessionTiming DEFAULT = new SessionTiming(50, 500);

  public SessionTiming {
    if (p2ServerMaxRaw < 0 || p2StarServerMaxRaw < 0) {
      throw new IllegalArgumentException("timing must be non-negative");
    }
  }

  /** Physical P2* in milliseconds (raw × 10). */
  public long p2StarPhysicalMs() {
    return p2StarServerMaxRaw * 10L;
  }

  /** TesterPresent period: half of P2*, floored at 200&nbsp;ms. */
  public long keepAliveIntervalMs() {
    return Math.max(200L, p2StarPhysicalMs() / 2L);
  }

  /**
   * Parses {@code 50 ss P2hi P2lo P2*hi P2*lo} hex from a positive session response.
   */
  public static Optional<SessionTiming> fromPositiveSessionHex(String hex) {
    byte[] bytes = parseHex(hex);
    if (bytes.length < 6 || (bytes[0] & 0xFF) != 0x50) {
      return Optional.empty();
    }
    int p2 = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    int p2star = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
    return Optional.of(new SessionTiming(p2, p2star));
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
