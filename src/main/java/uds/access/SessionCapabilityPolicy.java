package uds.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Strategy for session-scoped capability exposure. Delegates allow/deny to {@link
 * DiagnosticAccessGuard} (no duplicated access rules).
 */
public final class SessionCapabilityPolicy {

  private final DiagnosticAccessGuard guard;

  public SessionCapabilityPolicy(DiagnosticAccessGuard guard) {
    this.guard = Objects.requireNonNull(guard, "guard");
  }

  public List<String> availableReadDids(List<String> candidates) {
    return filter(candidates, DiagnosticAccessGuard.Operation.READ);
  }

  public List<String> availableWriteDids(List<String> candidates) {
    return filter(candidates, DiagnosticAccessGuard.Operation.WRITE);
  }

  private List<String> filter(List<String> candidates, DiagnosticAccessGuard.Operation op) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    ArrayList<String> out = new ArrayList<>();
    for (String did : candidates) {
      if (did == null || did.isBlank()) {
        continue;
      }
      if (guard.check(did, op) == GuardDecision.ALLOW) {
        out.add(did);
      }
    }
    return List.copyOf(out);
  }
}
