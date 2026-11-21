package uds.access;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import uds.access.AccessSnapshot;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.DidProfileSupport;
import uds.catalog.model.DidDefinition;

/**
 * Pre-exchange Read/Write gate (FR-26). Session/security requirements come from OemExtensions
 * columns on {@link DidDefinition}; profile membership from DidProfileSupport.
 */
public final class DiagnosticAccessGuard {

  public enum Operation {
    READ,
    WRITE
  }

  private final ActiveDiagnosticContext context;
  private final UdsCatalog catalog;

  public DiagnosticAccessGuard(ActiveDiagnosticContext context, UdsCatalog catalog) {
    this.context = Objects.requireNonNull(context, "context");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
  }

  public GuardDecision check(String didId, Operation operation) {
    AccessSnapshot snap = context.snapshot();
    if (!snap.profileSelected()) {
      return GuardDecision.ACCESS_DENIED;
    }

    String normalized = normalizeDid(didId);
    Optional<DidProfileSupport> support =
        catalog.getEcuConfig().didProfileSupports().stream()
            .filter(
                s ->
                    s.profileId().equals(snap.profileId())
                        && normalizeDid(s.didId()).equals(normalized))
            .findFirst();
    if (support.isEmpty()) {
      return GuardDecision.ACCESS_DENIED;
    }
    DidProfileSupport s = support.get();
    if (!s.runtimeEnabled()) {
      return GuardDecision.ACCESS_DENIED;
    }
    if (operation == Operation.READ && !s.readEnabled()) {
      return GuardDecision.ACCESS_DENIED;
    }
    if (operation == Operation.WRITE && !s.writeEnabled()) {
      return GuardDecision.ACCESS_DENIED;
    }

    Optional<DidDefinition> def =
        catalog.getPayload().didDefinitions().stream()
            .filter(d -> normalizeDid(d.id()).equals(normalized))
            .findFirst();
    if (def.isEmpty()) {
      return GuardDecision.ACCESS_DENIED;
    }
    DidDefinition d = def.get();
    if (!d.runtimeEnabled()) {
      return GuardDecision.ACCESS_DENIED;
    }
    if (!sessionSatisfied(d.requiredSession(), snap.accessState())) {
      return GuardDecision.ACCESS_DENIED;
    }
    if (!securitySatisfied(d, snap.accessState())) {
      return GuardDecision.ACCESS_DENIED;
    }
    return GuardDecision.ALLOW;
  }

  /**
   * EXTENDED required → EXTENDED_LOCKED or EXTENDED_UNLOCKED. Empty / DEFAULT → any session
   * including DEFAULT_LOCKED.
   */
  static boolean sessionSatisfied(String requiredSession, DiagnosticAccessState state) {
    String req = requiredSession == null ? "" : requiredSession.trim().toUpperCase(Locale.ROOT);
    if (req.isEmpty() || req.contains("DEFAULT")) {
      return true;
    }
    if (req.contains("EXTENDED")) {
      return state == DiagnosticAccessState.EXTENDED_LOCKED
          || state == DiagnosticAccessState.EXTENDED_UNLOCKED;
    }
    // Unknown token → fail closed
    return false;
  }

  /**
   * SECURITY_ACCESS / 0x27 / level≥1 → EXTENDED_UNLOCKED. NONE / blank / level 0 → session only.
   */
  static boolean securitySatisfied(DidDefinition d, DiagnosticAccessState state) {
    String mech =
        d.requiredSecurityMechanism() == null
            ? ""
            : d.requiredSecurityMechanism().trim().toUpperCase(Locale.ROOT);
    boolean needsUnlock =
        mech.contains("SECURITY") || mech.contains("0X27") || "27".equals(mech);
    int level = parseSecurityLevel(d.requiredSecurityLevel());
    if (level >= 1) {
      needsUnlock = true;
    }
    if (!needsUnlock) {
      return true;
    }
    return state == DiagnosticAccessState.EXTENDED_UNLOCKED;
  }

  private static int parseSecurityLevel(String raw) {
    if (raw == null || raw.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String normalizeDid(String didId) {
    if (didId == null) {
      return "";
    }
    String s = didId.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("0X")) {
      s = s.substring(2);
    }
    return s;
  }
}
