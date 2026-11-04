package uds.read.parse;

import java.math.BigDecimal;
import java.util.Optional;
import uds.read.finding.ReadStatus;

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
