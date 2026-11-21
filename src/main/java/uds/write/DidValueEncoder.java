package uds.write;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import uds.catalog.model.DidDefinition;

/** (logical − offset) / scale → fixed-length Big-Endian bytes. */
public final class DidValueEncoder {

  public byte[] encode(DidDefinition def, BigDecimal logical) {
    Objects.requireNonNull(def, "def");
    Objects.requireNonNull(logical, "logical");
    BigDecimal scale = def.scale() == null ? BigDecimal.ONE : def.scale();
    BigDecimal offset = def.offset() == null ? BigDecimal.ZERO : def.offset();
    BigDecimal rawBd = logical.subtract(offset).divide(scale, 0, RoundingMode.HALF_UP);
    long raw = rawBd.longValueExact();
    String type = def.dataType() == null ? "" : def.dataType().toUpperCase(Locale.ROOT);
    int len = def.byteLength();
    byte[] out = new byte[len];
    switch (type) {
      case "UINT8" -> out[0] = (byte) (raw & 0xFF);
      case "UINT16" -> {
        out[0] = (byte) ((raw >> 8) & 0xFF);
        out[1] = (byte) (raw & 0xFF);
      }
      case "UINT32" -> putBe32(out, raw);
      case "INT16" -> {
        out[0] = (byte) ((raw >> 8) & 0xFF);
        out[1] = (byte) (raw & 0xFF);
      }
      case "INT32" -> putBe32(out, raw);
      default -> throw new IllegalArgumentException("unsupported write data_type: " + type);
    }
    return out;
  }

  public static String toSpacedHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 3);
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
    }
    return sb.toString();
  }

  private static void putBe32(byte[] out, long raw) {
    out[0] = (byte) ((raw >> 24) & 0xFF);
    out[1] = (byte) ((raw >> 16) & 0xFF);
    out[2] = (byte) ((raw >> 8) & 0xFF);
    out[3] = (byte) (raw & 0xFF);
  }
}
