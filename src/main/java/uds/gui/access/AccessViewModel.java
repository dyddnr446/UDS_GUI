package uds.gui.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uds.access.facade.DiagnosticAccessFacade;
import uds.access.service.AccessOutcome;
import uds.access.state.DiagnosticAccessState;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.ControllerProfile;
import uds.fixture.repo.AccessStep;
import uds.fixture.repo.FixtureRepository;

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
    for (ControllerProfile p : catalog.runtimeEnabledProfiles()) {
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
    this.selectedFlowId = flowIds.isEmpty() ? null : flowIds.get(0);
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

  /**
   * REQUEST_SEED + SEND_KEY then clear password field immediately (NFR-18 / milestone).
   */
  public AccessOutcome unlock() {
    ensureFlowConfigured();
    char[] chars = password.toCharArray();
    try {
      facade.requestSeed();
      AccessOutcome out = facade.unlock(chars);
      statusMessage = "unlock → " + out.resultingState() + " err=" + out.errorCode();
      return out;
    } finally {
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
}
