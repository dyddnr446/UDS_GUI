package uds.access.guard;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.DidDefinition;
import uds.catalog.model.DidProfileSupport;
import uds.catalog.load.UdsCatalog;
import uds.access.state.AccessSnapshot;
import uds.access.state.ActiveDiagnosticContext;
import uds.access.state.DiagnosticAccessState;

/** Pre-exchange Read/Write gate (FR-26). */
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
    if (snap.accessState() != DiagnosticAccessState.EXTENDED_UNLOCKED) {
      return GuardDecision.ACCESS_DENIED;
    }

    String normalized = normalizeDid(didId);
    Optional<DidProfileSupport> support =
        catalog.didProfileSupports().stream()
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
        catalog.didDefinitions().stream()
            .filter(d -> normalizeDid(d.id()).equals(normalized))
            .findFirst();
    if (def.isPresent()) {
      DidDefinition d = def.get();
      if (!d.runtimeEnabled()) {
        return GuardDecision.ACCESS_DENIED;
      }
      String reqSession = d.requiredSession() == null ? "" : d.requiredSession().toUpperCase(Locale.ROOT);
      if (reqSession.contains("EXTENDED")
          && snap.accessState() != DiagnosticAccessState.EXTENDED_UNLOCKED
          && snap.accessState() != DiagnosticAccessState.EXTENDED_LOCKED) {
        return GuardDecision.ACCESS_DENIED;
      }
      String mech =
          d.requiredSecurityMechanism() == null
              ? ""
              : d.requiredSecurityMechanism().toUpperCase(Locale.ROOT);
      if ((mech.contains("SECURITY") || mech.contains("0x27") || mech.contains("27"))
          && snap.accessState() != DiagnosticAccessState.EXTENDED_UNLOCKED) {
        return GuardDecision.ACCESS_DENIED;
      }
    }
    return GuardDecision.ALLOW;
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
