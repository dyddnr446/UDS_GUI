package uds.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import uds.access.DiagnosticAccessFacade;
import uds.access.AccessOutcome;
import uds.access.DiagnosticAccessState;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.ControllerProfile;
import uds.catalog.model.SubFunctionDefinition;
import uds.fixture.AccessStep;
import uds.fixture.FixtureRepository;

/** Access tab state — no UDS protocol re-implementation (NFR-05). */
public final class AccessViewModel {

  private final DiagnosticAccessFacade facade;
  private final UdsCatalog catalog;
  private final FixtureRepository fixtures;

  private final List<String> profileIds = new ArrayList<>();
  private final List<String> flowIds = new ArrayList<>();
  private String selectedProfileId;
  private String selectedFlowId;
  private String password = "";
  private String statusMessage = "";

  public AccessViewModel(
      DiagnosticAccessFacade facade, UdsCatalog catalog, FixtureRepository fixtures) {
    this.facade = Objects.requireNonNull(facade, "facade");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
    for (ControllerProfile p : catalog.getEcuConfig().runtimeEnabledProfiles()) {
      profileIds.add(p.profileId());
    }
    refreshFlowsForProfile(null);
  }

  public List<String> profileIds() {
    return List.copyOf(profileIds);
  }

  public List<String> flowIds() {
    return List.copyOf(flowIds);
  }

  /**
   * Session Control options for {@code profileId}: SubFunctions(0x10) intersected with the
   * profile's {@code supported_sessions}. Empty profile → empty list.
   */
  public List<DiagnosticSessionChoice> sessionsForProfile(String profileId) {
    if (profileId == null || profileId.isBlank()) {
      return List.of();
    }
    ControllerProfile profile =
        catalog.getEcuConfig().findProfile(profileId).orElse(null);
    if (profile == null) {
      return List.of();
    }
    List<SubFunctionDefinition> all = catalog.getStandard().diagnosticSessions();
    List<DiagnosticSessionChoice> out = new ArrayList<>();
    boolean filter = !profile.supportedSessions().isEmpty();
    for (SubFunctionDefinition sub : all) {
      if (filter && !profile.supportsSessionName(sub.name()) && !profile.supportsSessionName(sub.subFunction())) {
        continue;
      }
      out.add(DiagnosticSessionChoice.fromSubFunction(sub));
    }
    if (out.isEmpty() && !filter) {
      // No SubFunctions rows — fall back to DEFAULT/EXTENDED labels for UI stability.
      out.add(
          new DiagnosticSessionChoice("DEFAULT", "Default Session", "0x01", "defaultSession"));
      out.add(
          new DiagnosticSessionChoice(
              "EXTENDED", "Extended Diagnostic Session", "0x03", "extendedDiagnosticSession"));
    }
    return List.copyOf(out);
  }

  public String selectedProfileId() {
    return selectedProfileId;
  }

  public String selectedFlowId() {
    return selectedFlowId;
  }

  public String password() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password == null ? "" : password;
  }

  public String statusMessage() {
    return statusMessage;
  }

  public DiagnosticAccessState accessState() {
    return facade.snapshot().accessState();
  }

  public void selectProfile(String profileId) {
    this.selectedProfileId = profileId;
    facade.selectProfile(profileId, catalog.catalogVersion());
    refreshFlowsForProfile(profileId);
    this.selectedFlowId = preferSuccessFlow(flowIds);
    statusMessage = "profile=" + profileId + " state=" + accessState();
  }

  public void selectFlow(String flowId) {
    this.selectedFlowId = flowId;
  }

  public AccessOutcome enterExtended() {
    ensureFlowConfigured();
    AccessOutcome out = facade.enterExtended();
    statusMessage = "enterExtended → " + out.resultingState() + " err=" + out.errorCode();
    return out;
  }

  public AccessOutcome enterDefault() {
    AccessOutcome out = facade.enterDefault();
    statusMessage = "enterDefault → " + out.resultingState() + " err=" + out.errorCode();
    return out;
  }

  /**
   * REQUEST_SEED + SEND_KEY then clear password field immediately (NFR-18 / milestone).
   */
  public AccessOutcome unlock() {
    ensureFlowConfigured();
    if (password == null || password.isBlank()) {
      statusMessage = "unlock → err=EMPTY_PASSWORD (enter Seed/Key Password)";
      return AccessOutcome.of(
          false, accessState(), "EMPTY_PASSWORD", null, "NEGATIVE", null);
    }
    char[] chars = password.toCharArray();
    try {
      facade.requestSeed();
      AccessOutcome out = facade.unlock(chars);
      statusMessage = "unlock → " + out.resultingState() + " err=" + out.errorCode();
      return out;
    } finally {
      Arrays.fill(chars, '\0');
      password = "";
    }
  }

  /** Full UC-06 success path used by milestone tests. Clears password afterward. */
  public AccessOutcome runAccessSuccessFlow(String demoPassword) {
    setPassword(demoPassword);
    ensureFlowConfigured();
    enterExtended();
    return unlock();
  }

  private void ensureFlowConfigured() {
    if (selectedFlowId == null || selectedFlowId.isBlank()) {
      throw new IllegalStateException("access flow not selected");
    }
    facade.setFixtureRunConfig(selectedFlowId);
  }

  private void refreshFlowsForProfile(String profileId) {
    flowIds.clear();
    for (AccessStep step : fixtures.accessSteps()) {
      if (profileId != null && !step.profileId().equals(profileId)) {
        continue;
      }
      if (!flowIds.contains(step.flowId())) {
        flowIds.add(step.flowId());
      }
    }
  }

  /** Prefer happy-path ACCESS-*-SUCCESS (no scenario combo in UI). */
  static String preferSuccessFlow(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    for (String id : ids) {
      if (id != null && id.toUpperCase(Locale.ROOT).contains("SUCCESS")) {
        return id;
      }
    }
    return ids.get(0);
  }
}
