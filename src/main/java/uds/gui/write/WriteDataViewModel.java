package uds.gui.write;

import java.util.ArrayList;
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
import uds.fixture.repo.WriteScenario;
import uds.write.parse.WriteResult;
import uds.write.parse.WriteStatus;
import uds.write.service.WriteDataService;

/** Write tab — preserves last ACCEPTED on later failures (NFR-08). UDS-UX-01. */
public final class WriteDataViewModel {

  private final DiagnosticAccessFacade facade;
  private final WriteDataService writeDataService;
  private final UdsCatalog catalog;
  private final FixtureRepository fixtures;

  private final ObservableList<String> runtimeDidIds = FXCollections.observableArrayList();
  private final ObservableList<String> scenarioIds = FXCollections.observableArrayList();
  private String selectedDid;
  private String selectedScenarioId;
  private String inputValue = "";
  private WriteResult lastResult;
  private WriteResult lastAccepted;
  private final BooleanProperty accessDenied = new SimpleBooleanProperty(false);
  private final StringProperty accessDeniedMessage = new SimpleStringProperty("");

  public WriteDataViewModel(
      DiagnosticAccessFacade facade,
      WriteDataService writeDataService,
      UdsCatalog catalog,
      FixtureRepository fixtures) {
    this.facade = Objects.requireNonNull(facade, "facade");
    this.writeDataService = Objects.requireNonNull(writeDataService, "writeDataService");
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

  public String inputValue() {
    return inputValue;
  }

  public void setInputValue(String inputValue) {
    this.inputValue = inputValue == null ? "" : inputValue;
  }

  public WriteResult lastResult() {
    return lastResult;
  }

  public WriteResult lastAccepted() {
    return lastAccepted;
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

  public void onProfileChanged(String profileId) {
    selectedDid = null;
    selectedScenarioId = null;
    lastResult = null;
    lastAccepted = null;
    clearAccessDenied();
    if (profileId == null || profileId.isBlank()) {
      runtimeDidIds.clear();
      scenarioIds.clear();
      return;
    }
    runtimeDidIds.setAll(catalog.runtimeWriteDids(profileId));
    if (!runtimeDidIds.isEmpty()) {
      selectedDid = runtimeDidIds.get(0);
    }
    ArrayList<String> nextScenarios = new ArrayList<>();
    for (WriteScenario s : fixtures.writeScenarios()) {
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

  public WriteResult writeSelected() {
    return write(selectedScenarioId, selectedDid, inputValue);
  }

  public WriteResult write(String scenarioId, String did, String value) {
    if (scenarioId != null && !scenarioId.isBlank()) {
      facade.setFixtureRunConfig(scenarioId);
    }
    selectDid(did);
    selectScenario(scenarioId);
    setInputValue(value);
    lastResult = writeDataService.write(did, value);
    if (lastResult.status() == WriteStatus.ACCEPTED) {
      lastAccepted = lastResult;
    }
    if (lastResult.status() == WriteStatus.ACCESS_DENIED) {
      accessDenied.set(true);
      accessDeniedMessage.set("권한 부족: Extended 세션 및 Security Unlock이 필요합니다");
    } else {
      clearAccessDenied();
    }
    return lastResult;
  }
}
