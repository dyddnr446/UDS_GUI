package uds.gui.read;

import java.util.Locale;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uds.access.facade.DiagnosticAccessFacade;
import uds.catalog.load.UdsCatalog;
import uds.fixture.repo.FixtureRepository;
import uds.fixture.repo.ReadScenario;
import uds.read.finding.ReadDataResult;
import uds.read.finding.ReadStatus;
import uds.read.service.ReadDataService;

/** Read tab — runtime DIDs only; scenario via setFixtureRunConfig (NFR-03). UDS-UX-01. */
public final class ReadDataViewModel {

  private final DiagnosticAccessFacade facade;
  private final ReadDataService readDataService;
  private final UdsCatalog catalog;
  private final FixtureRepository fixtures;

  private final ObservableList<String> runtimeDidIds = FXCollections.observableArrayList();
  private final ObservableList<String> scenarioIds = FXCollections.observableArrayList();
  private String selectedDid;
  private String selectedScenarioId;
  private ReadDataResult lastResult;
  private final BooleanProperty accessDenied = new SimpleBooleanProperty(false);
  private final StringProperty accessDeniedMessage = new SimpleStringProperty("");

  public ReadDataViewModel(
      DiagnosticAccessFacade facade,
      ReadDataService readDataService,
      UdsCatalog catalog,
      FixtureRepository fixtures) {
    this.facade = Objects.requireNonNull(facade, "facade");
    this.readDataService = Objects.requireNonNull(readDataService, "readDataService");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
  }

  /** Live list for ComboBox binding (UDS-UX-01). */
  public ObservableList<String> runtimeDidIds() {
    return runtimeDidIds;
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
      return;
    }
    runtimeDidIds.setAll(catalog.runtimeReadDids(profileId));
    if (!runtimeDidIds.isEmpty()) {
      selectedDid = runtimeDidIds.get(0);
    }
    java.util.ArrayList<String> nextScenarios = new java.util.ArrayList<>();
    for (ReadScenario s : fixtures.readScenarios()) {
      if (s.controllerProfile().equals(profileId) && !nextScenarios.contains(s.scenarioId())) {
        nextScenarios.add(s.scenarioId());
      }
    }
    scenarioIds.setAll(nextScenarios);
    if (!scenarioIds.isEmpty()) {
      selectedScenarioId = scenarioIds.get(0);
    }
  }

  public void selectDid(String did) {
    this.selectedDid = did;
  }

  public void selectScenario(String scenarioId) {
    this.selectedScenarioId = scenarioId;
  }

  public ReadDataResult readSelected() {
    if (selectedDid == null) {
      throw new IllegalStateException("DID not selected");
    }
    if (selectedScenarioId != null && !selectedScenarioId.isBlank()) {
      facade.setFixtureRunConfig(selectedScenarioId);
    }
    lastResult = readDataService.read(selectedDid);
    updateAccessDenied(lastResult);
    return lastResult;
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
}
