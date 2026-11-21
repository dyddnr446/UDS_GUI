package uds.write;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uds.catalog.model.DidDefinition;

class DidValueEncoderTest {

  private final DidValueEncoder encoder = new DidValueEncoder();

  @Test
  void encodesUnsignedBigEndian() {
    DidDefinition def = numericDid(0x2002, "UINT32", 4, new BigDecimal("0.1"), BigDecimal.ZERO);
    byte[] bytes = encoder.encode(def, new BigDecimal("300.0"));
    assertArrayEquals(new byte[] {0x00, 0x00, 0x0B, (byte) 0xB8}, bytes);
  }

  @Test
  void encodesSigned() {
    DidDefinition def = numericDid(0x1002, "INT16", 2, new BigDecimal("0.01"), BigDecimal.ZERO);
    byte[] bytes = encoder.encode(def, new BigDecimal("-12.34"));
    assertArrayEquals(new byte[] {(byte) 0xFB, 0x2E}, bytes);
  }

  @Test
  void halfUpRounding() {
    DidDefinition def = numericDid(0x2006, "UINT8", 1, new BigDecimal("0.3"), BigDecimal.ZERO);
    // 1.0 / 0.3 = 3.333... → HALF_UP to 3
    byte[] bytes = encoder.encode(def, new BigDecimal("1.0"));
    assertArrayEquals(new byte[] {0x03}, bytes);
    assertEquals("03", DidValueEncoder.toSpacedHex(bytes));
  }

  private static DidDefinition numericDid(
      int did, String dataType, int byteLength, BigDecimal scale, BigDecimal offset) {
    return new DidDefinition(
        "DID",
        did,
        "test",
        "0x2E",
        dataType,
        byteLength,
        scale,
        offset,
        "",
        Optional.empty(),
        Optional.empty(),
        "WRITE",
        Optional.empty(),
        Optional.empty(),
        "ALL",
        true,
        0,
        "",
        "NONE",
        "",
        "");
  }
}
