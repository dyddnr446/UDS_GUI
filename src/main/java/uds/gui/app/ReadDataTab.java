package uds.gui.app;

import static uds.gui.app.TabControls.addKvRow;
import static uds.gui.app.TabControls.blankToDash;
import static uds.gui.app.TabControls.kvGrid;
import static uds.gui.app.TabControls.syncDidSelection;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import uds.catalog.model.DidDefinition;
import uds.gui.MainViewModel;
import uds.gui.ReadDataViewModel;
import uds.read.FindingDetailCode;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;

final class ReadDataTab {

  private ReadDataTab() {}

  static Tab create(MainViewModel main) {
    final double readWidth = 560;

    ComboBox<String> dids = new ComboBox<>(main.read().accessibleDidIds());
    dids.setMaxWidth(readWidth);
    dids.setPrefWidth(readWidth);
    dids.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(String didId) {
            return main.read().formatDidLabel(didId);
          }

          @Override
          public String fromString(String string) {
            return string;
          }
        });
    dids.setCellFactory(
        list ->
            new ListCell<>() {
              @Override
              protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : main.read().formatDidLabel(item));
              }
            });
    dids.setButtonCell(
        new ListCell<>() {
          @Override
          protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : main.read().formatDidLabel(item));
          }
        });

    Label emptyHint = new Label("사용 가능한 DID 없음 — 상단에서 Extended Session 후 사용");
    emptyHint.visibleProperty().bind(Bindings.isEmpty(main.read().accessibleDidIds()));
    emptyHint.managedProperty().bind(emptyHint.visibleProperty());
    syncDidSelection(dids, main.read()::selectedDid);

    Label specDescription = new Label("—");
    Label specDataType = new Label("—");
    Label specFormula = new Label("—");
    Label specUnit = new Label("—");
    Label specRange = new Label("—");
    for (Label v :
        new Label[] {specDescription, specDataType, specFormula, specUnit, specRange}) {
      v.getStyleClass().add("kv-value");
      v.setWrapText(true);
    }

    GridPane specGrid = kvGrid();
    addKvRow(specGrid, 0, "Description", specDescription);
    addKvRow(specGrid, 1, "Data Type", specDataType);
    addKvRow(specGrid, 2, "Formula", specFormula);
    addKvRow(specGrid, 3, "Unit", specUnit);
    addKvRow(specGrid, 4, "Normal Range", specRange);

    Label specTitle = new Label("DID Specification");
    specTitle.getStyleClass().add("inspector-title");
    VBox specCard = new VBox(10, specTitle, specGrid);
    specCard.getStyleClass().add("inspector-card");
    specCard.setMaxWidth(readWidth);

    Runnable refreshSpec =
        () -> {
          String didId = dids.getValue();
          if (didId != null) {
            main.read().selectDid(didId);
          }
          DidDefinition def = main.read().findDid(didId).orElse(null);
          if (def == null) {
            specDescription.setText(didId == null || didId.isBlank() ? "—" : didId);
            specDataType.setText("—");
            specFormula.setText("—");
            specUnit.setText("—");
            specRange.setText("—");
            return;
          }
          specDescription.setText(def.name().isBlank() ? def.didHex() : def.name());
          specDataType.setText(
              (def.dataType().isBlank() ? "(unknown)" : def.dataType())
                  + " / "
                  + def.byteLength()
                  + " bytes");
          specFormula.setText(ReadDataViewModel.formatFormula(def));
          specUnit.setText(def.unit().isBlank() ? "(none)" : def.unit());
          specRange.setText(ReadDataViewModel.formatNormalRange(def));
        };
    dids.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refreshSpec.run());
    dids.getItems().addListener((javafx.collections.ListChangeListener<String>) c -> refreshSpec.run());
    refreshSpec.run();

    Label rawRequest = new Label("(none)");
    Label rawResponse = new Label("(none)");
    Label physicalValue = new Label("(none)");
    Label statusBadge = new Label("—");
    for (Label v : new Label[] {rawRequest, rawResponse, physicalValue}) {
      v.getStyleClass().add("kv-value");
      v.setWrapText(true);
    }
    statusBadge.getStyleClass().addAll("status-badge");

    GridPane resultGrid = kvGrid();
    addKvRow(resultGrid, 0, "Raw Request", rawRequest);
    addKvRow(resultGrid, 1, "Raw Response", rawResponse);
    addKvRow(resultGrid, 2, "Physical Value", physicalValue);
    addKvRow(resultGrid, 3, "Status", statusBadge);

    Label resultTitle = new Label("Read Result");
    resultTitle.getStyleClass().add("inspector-title");
    VBox resultCard = new VBox(10, resultTitle, resultGrid);
    resultCard.getStyleClass().add("inspector-card");
    resultCard.setMaxWidth(readWidth);

    Button read = new Button("Read");
    read.disableProperty().bind(Bindings.isEmpty(main.read().accessibleDidIds()));
    read.setOnAction(
        e -> {
          if (dids.getValue() != null) {
            main.read().selectDid(dids.getValue());
          }
          ReadDataResult r = main.readSelectedAndRecord();
          applyReadResult(r, rawRequest, rawResponse, physicalValue, statusBadge);
        });

    VBox inputSection = new VBox(8, new Label("DID"), dids, read, emptyHint);
    inputSection.setMaxWidth(readWidth);

    VBox form =
        new VBox(15, inputSection, new Separator(), specCard, new Separator(), resultCard);
    form.setPadding(new Insets(20));
    form.setMaxWidth(readWidth + 40);

    StackPane wrap = new StackPane(form);
    wrap.setAlignment(Pos.TOP_LEFT);
    return new Tab("ReadData", wrap);
  }

  private static void applyReadResult(
      ReadDataResult r,
      Label rawRequest,
      Label rawResponse,
      Label physicalValue,
      Label statusBadge) {
    if (r == null) {
      rawRequest.setText("(none)");
      rawResponse.setText("(none)");
      physicalValue.setText("(none)");
      statusBadge.setText("—");
      statusBadge.getStyleClass().setAll("status-badge");
      return;
    }
    rawRequest.setText(blankToDash(r.requestHex()));
    rawResponse.setText(blankToDash(r.responseHex()));

    ReadStatus status = r.status();
    statusBadge.getStyleClass().setAll("status-badge");
    if (status == ReadStatus.NORMAL) {
      statusBadge.setText("NORMAL");
      statusBadge.getStyleClass().add("status-badge-normal");
      physicalValue.setText(blankToDash(r.displayValue()));
    } else if (status == ReadStatus.ABNORMAL) {
      statusBadge.setText("OUT_OF_RANGE");
      statusBadge.getStyleClass().add("status-badge-warning");
      physicalValue.setText(blankToDash(r.displayValue()));
    } else {
      statusBadge.setText("ERROR");
      statusBadge.getStyleClass().add("status-badge-error");
      physicalValue.setText(formatReadError(r));
    }
  }

  private static String formatReadError(ReadDataResult r) {
    StringBuilder sb = new StringBuilder();
    sb.append(r.status());
    if (r.nrcCode() != null && !r.nrcCode().isBlank()) {
      sb.append(" / NRC ").append(r.nrcCode());
    }
    if (r.detailCode() != null && r.detailCode() != FindingDetailCode.NONE) {
      sb.append(" / ").append(r.detailCode());
    }
    if (r.displayValue() != null && !r.displayValue().isBlank()) {
      sb.append(" — ").append(r.displayValue());
    }
    return sb.toString();
  }
}
