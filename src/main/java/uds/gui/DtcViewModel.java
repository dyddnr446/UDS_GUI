package uds.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import uds.read.DtcItem;
import uds.read.DtcPipelineSnapshot;
import uds.read.DtcResult;
import uds.read.DtcService;

/** DTC tab ViewModel — options, preview, pipeline, multi-row result (FR-30). */
public final class DtcViewModel {

  public record Option(int value, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  private final DtcService dtcService;

  private final ObservableList<Option> subFunctionOptions =
      FXCollections.observableArrayList(
          new Option(DtcService.SUB_REPORT_BY_STATUS_MASK, "[0x02] reportDTCByStatusMask"),
          new Option(DtcService.SUB_REPORT_NUMBER, "[0x01] reportNumberOfDTCByStatusMask"));
  private final ObservableList<Option> statusMaskOptions =
      FXCollections.observableArrayList(
          new Option(0xFF, "[0xFF] All DTCs"),
          new Option(0x01, "[0x01] testFailed"),
          new Option(0x04, "[0x04] pendingDTC"),
          new Option(0x08, "[0x08] confirmedDTC"));
  private final ObservableList<Option> groupOptions =
      FXCollections.observableArrayList(new Option(DtcService.GROUP_ALL, "[0xFFFFFF] All Groups"));

  private Option selectedSubFunction = subFunctionOptions.get(0);
  private Option selectedStatusMask = statusMaskOptions.get(0);
  private Option selectedGroup = groupOptions.get(0);

  private final StringProperty requestPreview = new SimpleStringProperty();
  private final StringProperty byteMeaning = new SimpleStringProperty();
  private final StringProperty clearPreview = new SimpleStringProperty();
  private final StringProperty clearByteMeaning = new SimpleStringProperty();
  private final StringProperty accessRequirement =
      new SimpleStringProperty("EXTENDED_UNLOCKED (세션 Extended + Security Unlock)");

  private final StringProperty statusLabel = new SimpleStringProperty("—");
  private final StringProperty nrcText = new SimpleStringProperty("—");
  private final StringProperty rawRequest = new SimpleStringProperty("—");
  private final StringProperty rawResponse = new SimpleStringProperty("—");
  private final StringProperty dtcCountText = new SimpleStringProperty("—");
  private final StringProperty lastAction = new SimpleStringProperty("Last Action: —");
  private final StringProperty emptyHint = new SimpleStringProperty("");

  private final ObservableList<DtcRow> rows = FXCollections.observableArrayList();
  private DtcPipelineSnapshot lastPipeline = DtcPipelineSnapshot.empty();

  public DtcViewModel(DtcService dtcService) {
    this.dtcService = dtcService;
    refreshPreview();
  }

  public ObservableList<Option> subFunctionOptions() {
    return subFunctionOptions;
  }

  public ObservableList<Option> statusMaskOptions() {
    return statusMaskOptions;
  }

  public ObservableList<Option> groupOptions() {
    return groupOptions;
  }

  public Option selectedSubFunction() {
    return selectedSubFunction;
  }

  public void setSelectedSubFunction(Option option) {
    if (option != null) {
      selectedSubFunction = option;
      refreshPreview();
    }
  }

  public Option selectedStatusMask() {
    return selectedStatusMask;
  }

  public void setSelectedStatusMask(Option option) {
    if (option != null) {
      selectedStatusMask = option;
      refreshPreview();
    }
  }

  public Option selectedGroup() {
    return selectedGroup;
  }

  public void setSelectedGroup(Option option) {
    if (option != null) {
      selectedGroup = option;
      refreshPreview();
    }
  }

  public void refreshPreview() {
    int sub = selectedSubFunction.value();
    int mask = selectedStatusMask.value();
    int group = selectedGroup.value();
    requestPreview.set(DtcService.buildReadRequestHex(sub, mask));
    byteMeaning.set(
        "SID 0x19 · Sub-Function 0x"
            + String.format("%02X", sub)
            + " · Status Mask 0x"
            + String.format("%02X", mask));
    clearPreview.set(DtcService.buildClearRequestHex(group));
    clearByteMeaning.set(
        "SID 0x14 · Group of DTC 0x" + String.format("%06X", group & 0xFFFFFF));
  }

  public StringProperty requestPreviewProperty() {
    return requestPreview;
  }

  public StringProperty byteMeaningProperty() {
    return byteMeaning;
  }

  public StringProperty clearPreviewProperty() {
    return clearPreview;
  }

  public StringProperty clearByteMeaningProperty() {
    return clearByteMeaning;
  }

  public StringProperty accessRequirementProperty() {
    return accessRequirement;
  }

  public StringProperty statusLabelProperty() {
    return statusLabel;
  }

  public StringProperty nrcTextProperty() {
    return nrcText;
  }

  public StringProperty rawRequestProperty() {
    return rawRequest;
  }

  public StringProperty rawResponseProperty() {
    return rawResponse;
  }

  public StringProperty dtcCountTextProperty() {
    return dtcCountText;
  }

  public StringProperty lastActionProperty() {
    return lastAction;
  }

  public StringProperty emptyHintProperty() {
    return emptyHint;
  }

  public ObservableList<DtcRow> rows() {
    return rows;
  }

  public DtcPipelineSnapshot lastPipeline() {
    return lastPipeline;
  }

  public void readDtc() {
    apply(dtcService.readDtc(selectedSubFunction.value(), selectedStatusMask.value()));
  }

  public void clearDtc() {
    apply(dtcService.clearDtc(selectedGroup.value()));
  }

  private void apply(DtcResult r) {
    lastPipeline = r.pipeline();
    statusLabel.set(blankDash(r.statusLabel()));
    nrcText.set(blankDash(r.nrcHex()));
    rawRequest.set(blankDash(r.requestHex()));
    rawResponse.set(blankDash(r.responseHex()));
    lastAction.set(
        r.pipeline().lastAction().isBlank() ? "Last Action: —" : r.pipeline().lastAction());

    rows.clear();
    if (selectedSubFunction.value() == DtcService.SUB_REPORT_NUMBER
        && "POSITIVE".equals(r.statusLabel())) {
      dtcCountText.set("DTC Count: " + r.dtcCount());
      emptyHint.set("");
    } else {
      for (DtcItem item : r.items()) {
        rows.add(new DtcRow(item));
      }
      dtcCountText.set("DTC Count: " + r.items().size());
      if ("POSITIVE".equals(r.statusLabel()) && r.items().isEmpty() && !"0x14".equalsIgnoreCase(r.serviceHex())) {
        emptyHint.set("저장된 고장 코드(DTC)가 없습니다.");
      } else if ("0x14".equalsIgnoreCase(r.serviceHex()) && "POSITIVE".equals(r.statusLabel())) {
        emptyHint.set("Clear 완료 — 다음 Read에서 목록이 비어 있어야 합니다.");
        dtcCountText.set("DTC Count: 0 (cleared)");
      } else {
        emptyHint.set("");
      }
    }
  }

  private static String blankDash(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }
}
