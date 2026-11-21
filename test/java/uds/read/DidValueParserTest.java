package uds.read;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uds.catalog.model.DidDefinition;

class DidValueParserTest {

  private final DidValueParser parser = new DidValueParser();

  @Test
  void parsesScaledVoltage() {
    DidDefinition def =
        numericDid(0x1001, "UINT16", 2, new BigDecimal("0.1"), BigDecimal.ZERO, "V");
    // raw 725 = 0x02D5 → 72.5
    ParsedDidValue parsed = parser.parse("62 10 01 02 D5", "0x1001", def);
    assertEquals(null, parsed.parseStatus());
    assertEquals(0, new BigDecimal("72.5").compareTo(parsed.physicalValue().orElseThrow()));
    assertEquals("72.5 V", parsed.displayValue());
  }

  private static DidDefinition numericDid(
      int did, String dataType, int byteLength, BigDecimal scale, BigDecimal offset, String unit) {
    return new DidDefinition(
        "DID",
        did,
        "test",
        "0x22",
        dataType,
        byteLength,
        scale,
        offset,
        unit,
        Optional.empty(),
        Optional.empty(),
        "READ",
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
