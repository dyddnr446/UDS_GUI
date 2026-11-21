package uds.gui;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uds.access.DiagnosticAccessFacade;
import uds.access.SessionCapabilityPolicy;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.DidDefinition;
import uds.fixture.FixtureRepository;
import uds.fixture.ReadScenario;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;
import uds.read.ReadDataService;

/** Read tab — runtime DIDs only; scenario via setFixtureRunConfig (NFR-03). UDS-UX-01. */
public final class ReadDataViewModel {

  private final DiagnosticAccessFacade facade;
  private final SessionCapabilityPolicy capabilityPolicy;
  private final ReadDataService readDataService;
  private final UdsCatalog catalog;
  private final FixtureRepository fixtures;

  private final ObservableList<String> runtimeDidIds = FXCollections.observableArrayList();
  private final ObservableList<String> accessibleDidIds = FXCollections.observableArrayList();
  private final ObservableList<String> scenarioIds = FXCollections.observableArrayList();
  private String selectedDid;
  private String selectedScenarioId;
  private ReadDataResult lastResult;
  private final BooleanProperty accessDenied = new SimpleBooleanProperty(false);
  private final StringProperty accessDeniedMessage = new SimpleStringProperty("");

  public ReadDataViewModel(
      DiagnosticAccessFacade facade,
      SessionCapabilityPolicy capabilityPolicy,
      ReadDataService readDataService,
      UdsCatalog catalog,
      FixtureRepository fixtures) {
    this.facade = Objects.requireNonNull(facade, "facade");
    this.capabilityPolicy = Objects.requireNonNull(capabilityPolicy, "capabilityPolicy");
    this.readDataService = Objects.requireNonNull(readDataService, "readDataService");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
  }

  /** Profile catalog DID list (UDS-UX-01). */
  public ObservableList<String> runtimeDidIds() {
    return runtimeDidIds;
  }

  /** Session-capable DID list for ComboBox (Guard ALLOW only). */
  public ObservableList<String> accessibleDidIds() {
    return accessibleDidIds;
  }

  /** Live list for ComboBox binding (UDS-UX-01). */
  public ObservableList<String> scenarioIds() {
    return scenarioIds;
  }

  public String selectedDid() {
    return selectedDid;
  }

  public String selectedScenarioId() {
    return selectedScenarioId;
  }

  public ReadDataResult lastResult() {
    return lastResult;
  }

  public BooleanProperty accessDeniedProperty() {
    return accessDenied;
  }

  public StringProperty accessDeniedMessageProperty() {
    return accessDeniedMessage;
  }

  public void clearAccessDenied() {
    accessDenied.set(false);
    accessDeniedMessage.set("");
  }

  /** Refresh DID/scenario lists for the active profile (immediate Observable update). */
  public void onProfileChanged(String profileId) {
    selectedDid = null;
    selectedScenarioId = null;
    lastResult = null;
    clearAccessDenied();
    if (profileId == null || profileId.isBlank()) {
      runtimeDidIds.clear();
      scenarioIds.clear();
      accessibleDidIds.clear();
      return;
    }
    runtimeDidIds.setAll(catalog.getEcuConfig().runtimeReadDids(profileId));
    if (!runtimeDidIds.isEmpty()) {
      selectedDid = runtimeDidIds.get(0);
    }
    ArrayList<String> nextScenarios = new ArrayList<>();
    for (ReadScenario s : fixtures.readScenarios()) {
      if (s.controllerProfile().equals(profileId) && !nextScenarios.contains(s.scenarioId())) {
        nextScenarios.add(s.scenarioId());
      }
    }
    scenarioIds.setAll(nextScenarios);
    // Scenario is resolved per DID on Read — no UI combo.
    selectedScenarioId = null;
    refreshAccessibleDids();
  }

  /** Re-evaluate Guard-allowed DIDs for the current access state. */
  public void refreshAccessibleDids() {
    accessibleDidIds.setAll(capabilityPolicy.availableReadDids(runtimeDidIds));
    // When unlocked, prefer an accessible selection. When locked, keep profile catalog
    // selection so Guard ACCESS_DENIED paths remain testable.
    if (!accessibleDidIds.isEmpty()
        && (selectedDid == null || !accessibleDidIds.contains(selectedDid))) {
      selectedDid = accessibleDidIds.get(0);
    }
  }

  public void selectDid(String did) {
    if (did == null || !did.equals(this.selectedDid)) {
      // DID마다 fixture 시나리오가 다름 — 이전 DID 시나리오를 붙잡지 않는다.
      this.selectedScenarioId = null;
    }
    this.selectedDid = did;
  }

  public void selectScenario(String scenarioId) {
    this.selectedScenarioId = scenarioId;
  }

  public ReadDataResult readSelected() {
    if (selectedDid == null) {
      throw new IllegalStateException("DID not selected");
    }
    String scenario = selectedScenarioId;
    if (scenario == null || scenario.isBlank()) {
      scenario = resolveDefaultScenario(selectedDid);
      selectedScenarioId = scenario;
    }
    if (scenario != null && !scenario.isBlank()) {
      facade.setFixtureRunConfig(scenario);
    }
    lastResult = readDataService.read(selectedDid);
    updateAccessDenied(lastResult);
    return lastResult;
  }

  /** Prefer NORMAL response for the DID; otherwise first matching fixture row. */
  String resolveDefaultScenario(String did) {
    String profile = facade.snapshot().profileId();
    if (profile == null || profile.isBlank() || did == null) {
      return null;
    }
    String want = normalizeDid(did);
    String fallback = null;
    for (ReadScenario s : fixtures.readScenarios()) {
      if (!profile.equals(s.controllerProfile())) {
        continue;
      }
      if (!want.equals(normalizeDid(s.did()))) {
        continue;
      }
      if ("NORMAL".equalsIgnoreCase(s.expectedStatus())) {
        return s.scenarioId();
      }
      if (fallback == null) {
        fallback = s.scenarioId();
      }
    }
    return fallback;
  }

  public ReadDataResult read(String scenarioId, String did) {
    facade.setFixtureRunConfig(scenarioId);
    selectDid(did);
    selectScenario(scenarioId);
    lastResult = readDataService.read(did);
    updateAccessDenied(lastResult);
    return lastResult;
  }

  private void updateAccessDenied(ReadDataResult result) {
    if (result != null && result.status() == ReadStatus.ACCESS_DENIED) {
      accessDenied.set(true);
      accessDeniedMessage.set("권한 부족: Extended 세션 및 Security Unlock이 필요합니다");
    } else {
      clearAccessDenied();
    }
  }

  public static String normalizeDid(String did) {
    String s = did.trim().toUpperCase(Locale.ROOT);
    if (!s.startsWith("0X")) {
      s = "0x" + s;
    } else {
      s = "0x" + s.substring(2);
    }
    return s;
  }

  /** Catalog lookup for the Read Inspector (UI only; no exchange). */
  public Optional<DidDefinition> findDid(String didId) {
    if (didId == null || didId.isBlank()) {
      return Optional.empty();
    }
    return catalog.getPayload().getDid(normalizeDid(didId));
  }

  /** ComboBox display: {@code [0x1001] Input Voltage}. */
  public String formatDidLabel(String didId) {
    if (didId == null || didId.isBlank()) {
      return "";
    }
    String hex = normalizeDid(didId);
    return findDid(hex)
        .map(DidDefinition::name)
        .filter(n -> !n.isBlank())
        .map(name -> "[" + hex + "] " + name)
        .orElse("[" + hex + "]");
  }

  public static String formatFormula(DidDefinition def) {
    if (def == null) {
      return "(n/a)";
    }
    return "(Raw * " + def.scale().toPlainString() + ") + " + def.offset().toPlainString();
  }

  public static String formatNormalRange(DidDefinition def) {
    if (def == null) {
      return "(n/a)";
    }
    if (def.normalMin().isEmpty() && def.normalMax().isEmpty()) {
      return "(n/a)";
    }
    String min = def.normalMin().map(v -> v.toPlainString()).orElse("?");
    String max = def.normalMax().map(v -> v.toPlainString()).orElse("?");
    return min + " ~ " + max;
  }
}
