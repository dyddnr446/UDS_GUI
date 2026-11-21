package uds.read;

import java.util.Objects;

/** Immutable ReadData outcome. */
public record ReadDataResult(
    String requestId,
    String responseId,
    String did,
    String requestHex,
    String responseHex,
    String displayValue,
    ReadStatus status,
    FindingDetailCode detailCode,
    String nrcCode,
    Finding finding) {

  public ReadDataResult {
    Objects.requireNonNull(did, "did");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(detailCode, "detailCode");
    if (displayValue == null) {
      displayValue = "";
    }
  }
}
