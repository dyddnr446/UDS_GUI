package uds.read;

import java.math.BigDecimal;
import java.util.Optional;
import uds.read.ReadStatus;

/** Intermediate parse outcome before Finding analysis. */
public record ParsedDidValue(
    String did,
    ReadStatus parseStatus,
    Optional<BigDecimal> physicalValue,
    String displayValue,
    String nrcCode,
    String responseHex) {

  public boolean parseOk() {
    return parseStatus == null;
  }
}
