package uds.analysis;

import java.util.Objects;

/**
 * Redacted access evidence row. resultingState is recorded only after Context transition by Access
 * services (Step 3); Step 2B provides the recorder API and DTO.
 */
public record AccessEvidence(
    String evidenceId,
    String analysisSessionId,
    String operation,
    String responseKind,
    String nrc,
    String resultingState,
    boolean payloadRedacted) {

  public static final String EVIDENCE_PREFIX = "ACCESS:";

  public AccessEvidence {
    Objects.requireNonNull(evidenceId, "evidenceId");
    Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(responseKind, "responseKind");
    Objects.requireNonNull(resultingState, "resultingState");
    if (!evidenceId.startsWith(EVIDENCE_PREFIX)) {
      throw new IllegalArgumentException("evidenceId must start with ACCESS:");
    }
  }
}
