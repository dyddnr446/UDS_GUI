package uds.read.finding;

import java.util.Objects;

/** Deterministic Finding row (FR-16). */
public record Finding(
    String did, String displayValue, ReadStatus status, FindingDetailCode detailCode, String evidence) {

  public Finding {
    Objects.requireNonNull(did, "did");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(detailCode, "detailCode");
    if (displayValue == null) {
      displayValue = "";
    }
  }
}
