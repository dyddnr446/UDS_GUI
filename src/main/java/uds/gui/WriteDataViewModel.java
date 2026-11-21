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
import uds.fixture.WriteScenario;
import uds.write.WriteResult;
import uds.write.WriteStatus;
import uds.write.WriteDataService;

/** Write tab — preserves last ACCEPTED on later failures (NFR-08). UDS-UX-01. */
public final class WriteDataViewModel {

  private final DiagnosticAccessFacade facade;
  private final SessionCapabilityPolicy capabilityPolicy;
  private final WriteDataService writeDataService;
  private final UdsCatalog catalog;
  private final FixtureRepository fixtures;

  private final ObservableList<String> runtimeDidIds = FXCollections.observableArrayList();
  private final ObservableList<String> accessibleDidIds = FXCollections.observableArrayList();
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
      SessionCapabilityPolicy capabilityPolicy,
      WriteDataService writeDataService,
      UdsCatalog catalog,
      FixtureRepository fixtures) {
    this.facade = Objects.requireNonNull(facade, "facade");
    this.capabilityPolicy = Objects.requireNonNull(capabilityPolicy, "capabilityPolicy");
    this.writeDataService = Objects.requireNonNull(writeDataService, "writeDataService");
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
      accessibleDidIds.clear();
      return;
    }
    runtimeDidIds.setAll(catalog.getEcuConfig().runtimeWriteDids(profileId));
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
    // Scenario is resolved per DID on Write — no UI combo.
    selectedScenarioId = null;
    refreshAccessibleDids();
  }

  /** Re-evaluate Guard-allowed DIDs for the current access state. */
  public void refreshAccessibleDids() {
    accessibleDidIds.setAll(capabilityPolicy.availableWriteDids(runtimeDidIds));
    if (!accessibleDidIds.isEmpty()
        && (selectedDid == null || !accessibleDidIds.contains(selectedDid))) {
      selectedDid = accessibleDidIds.get(0);
    }
  }

  public void selectDid(String did) {
    if (did == null || !did.equals(this.selectedDid)) {
      this.selectedScenarioId = null;
    }
    this.selectedDid = did;
  }

  public void selectScenario(String scenarioId) {
    this.selectedScenarioId = scenarioId;
  }

  public WriteResult writeSelected() {
    String scenario = selectedScenarioId;
    if (scenario == null || scenario.isBlank()) {
      scenario = resolveDefaultScenario(selectedDid);
    }
    return write(scenario, selectedDid, inputValue);
  }

  public WriteResult write(String scenarioId, String did, String value) {
    String resolved = scenarioId;
    if (resolved == null || resolved.isBlank()) {
      resolved = resolveDefaultScenario(did);
    }
    if (resolved != null && !resolved.isBlank()) {
      facade.setFixtureRunConfig(resolved);
    }
    selectDid(did);
    selectScenario(resolved);
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

  /** Prefer ACCEPTED fixture row for the DID; otherwise first match. */
  String resolveDefaultScenario(String did) {
    String profile = facade.snapshot().profileId();
    if (profile == null || profile.isBlank() || did == null) {
      return null;
    }
    String want = did.trim().toUpperCase(Locale.ROOT);
    if (!want.startsWith("0X")) {
      want = "0X" + want;
    }
    String wantNorm = "0x" + want.substring(2);
    String fallback = null;
    for (WriteScenario s : fixtures.writeScenarios()) {
      if (!profile.equals(s.controllerProfile())) {
        continue;
      }
      String sd = s.did() == null ? "" : s.did().trim().toUpperCase(Locale.ROOT);
      if (!sd.startsWith("0X")) {
        sd = "0X" + sd;
      }
      String sn = "0x" + sd.substring(2);
      if (!wantNorm.equalsIgnoreCase(sn)) {
        continue;
      }
      if ("ACCEPTED".equalsIgnoreCase(s.expectedStatus())) {
        return s.scenarioId();
      }
      if (fallback == null) {
        fallback = s.scenarioId();
      }
    }
    return fallback;
  }

  /** Catalog lookup for Write Inspector (UI only). */
  public Optional<DidDefinition> findDid(String didId) {
    if (didId == null || didId.isBlank()) {
      return Optional.empty();
    }
    return catalog.getPayload().getDid(normalizeDid(didId));
  }

  /** ComboBox display: {@code [0x2001] ND target RPM}. */
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

  public static String formatDataTypeLength(DidDefinition def) {
    if (def == null) {
      return "—";
    }
    String type = def.dataType().isBlank() ? "(unknown)" : def.dataType();
    return type + ", " + def.byteLength() + " bytes";
  }

  public static String formatUnitScale(DidDefinition def) {
    if (def == null) {
      return "—";
    }
    String unit = def.unit().isBlank() ? "(none)" : def.unit();
    return unit + ", Scale " + def.scale().toPlainString();
  }

  public static String formatWriteRange(DidDefinition def) {
    if (def == null) {
      return "(n/a)";
    }
    if (def.writeMin().isEmpty() && def.writeMax().isEmpty()) {
      return "(n/a)";
    }
    String min = def.writeMin().map(v -> v.toPlainString()).orElse("?");
    String max = def.writeMax().map(v -> v.toPlainString()).orElse("?");
    return min + " ~ " + max;
  }

  public String formatLastAccepted() {
    WriteResult a = lastAccepted;
    if (a == null) {
      return "Last Accepted: (none)";
    }
    String unit =
        findDid(a.did()).map(DidDefinition::unit).filter(u -> !u.isBlank()).orElse("");
    String physical = a.inputValue() + (unit.isEmpty() ? "" : " " + unit);
    String hex = a.encodedHex() == null || a.encodedHex().isBlank() ? "(no hex)" : a.encodedHex();
    return "Last Accepted: " + physical + " (" + hex + ")";
  }

  /** Map WriteResult → 3-stage badge labels for the Write Result panel (UI only). */
  public static WritePipelineSnapshot pipelineOf(WriteResult result) {
    if (result == null) {
      return WritePipelineSnapshot.empty();
    }
    WriteStatus s = result.status();
    return switch (s) {
      case ACCESS_DENIED ->
          new WritePipelineSnapshot(
              "—", "idle",
              "DENIED", "warn",
              "—", "idle",
              failureReason(result));
      case VALIDATION_ERROR_TYPE, VALIDATION_ERROR_RANGE ->
          new WritePipelineSnapshot(
              "FAIL", "error",
              "GRANTED", "ok",
              "—", "idle",
              failureReason(result));
      case ACCEPTED ->
          new WritePipelineSnapshot(
              "PASS", "ok",
              "GRANTED", "ok",
              "ACCEPTED", "info",
              "");
      case REJECTED ->
          new WritePipelineSnapshot(
              "PASS", "ok",
              "GRANTED", "ok",
              "REJECTED", "error",
              failureReason(result));
      case PARSE_ERROR_DID_MISMATCH, PARSE_ERROR_LENGTH ->
          new WritePipelineSnapshot(
              "PASS", "ok",
              "GRANTED", "ok",
              "TIMEOUT", "idle",
              failureReason(result));
    };
  }

  private static String failureReason(WriteResult result) {
    WriteStatus s = result.status();
    return switch (s) {
      case ACCESS_DENIED -> "Security Unlock이 필요합니다 (Extended 세션 + Unlock)";
      case VALIDATION_ERROR_TYPE -> "입력값 타입이 DID 스펙과 맞지 않습니다";
      case VALIDATION_ERROR_RANGE -> "입력값이 허용 범위를 벗어났습니다";
      case REJECTED ->
          "ECU가 쓰기를 거절했습니다"
              + (result.responseHex() == null || result.responseHex().isBlank()
                  ? ""
                  : " (" + result.responseHex() + ")");
      case PARSE_ERROR_DID_MISMATCH -> "응답 DID가 요청과 일치하지 않습니다";
      case PARSE_ERROR_LENGTH -> "응답 길이/포맷이 올바르지 않습니다 (통신 오류 가능)";
      case ACCEPTED -> "";
    };
  }

  static String normalizeDid(String did) {
    String s = did.trim().toUpperCase(Locale.ROOT);
    if (!s.startsWith("0X")) {
      s = "0x" + s;
    } else {
      s = "0x" + s.substring(2);
    }
    return s;
  }

  /** UI-only snapshot of Validator / Guard / Exchange badges. */
  public record WritePipelineSnapshot(
      String validatorLabel,
      String validatorStyle,
      String guardLabel,
      String guardStyle,
      String exchangeLabel,
      String exchangeStyle,
      String failureReason) {

    public static WritePipelineSnapshot empty() {
      return new WritePipelineSnapshot("—", "idle", "—", "idle", "—", "idle", "");
    }
  }
}
