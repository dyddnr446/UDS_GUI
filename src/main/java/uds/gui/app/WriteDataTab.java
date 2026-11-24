package uds.gui.app;

import static uds.gui.app.TabControls.addKvRow;
import static uds.gui.app.TabControls.applyPipelineBadge;
import static uds.gui.app.TabControls.blankToDash;
import static uds.gui.app.TabControls.kvGrid;
import static uds.gui.app.TabControls.pipelineBadge;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import uds.catalog.model.DidDefinition;
import uds.gui.MainViewModel;
import uds.gui.WriteDataViewModel;
import uds.gui.WriteDataViewModel.WritePipelineSnapshot;
import uds.write.WriteResult;
import uds.write.WriteStatus;

final class WriteDataTab {

  private WriteDataTab() {}

  static Tab create(MainViewModel main) {
    final double writeWidth = 480;

    ComboBox<String> dids = new ComboBox<>(main.write().accessibleDidIds());
    dids.setMaxWidth(writeWidth);
    dids.setPrefWidth(writeWidth);
    dids.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(String didId) {
            return main.write().formatDidLabel(didId);
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
                setText(empty || item == null ? null : main.write().formatDidLabel(item));
              }
            });
    dids.setButtonCell(
        new ListCell<>() {
          @Override
          protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : main.write().formatDidLabel(item));
          }
        });

    TextField value = new TextField();
    value.setMaxWidth(writeWidth);
    value.setPrefWidth(writeWidth);
    value.setPromptText("물리값 입력 (예: 3000)");

    Label emptyHint = new Label("사용 가능한 DID 없음 — 상단에서 Unlock 후 사용");
    emptyHint.visibleProperty().bind(Bindings.isEmpty(main.write().accessibleDidIds()));
    emptyHint.managedProperty().bind(emptyHint.visibleProperty());
    syncDidSelection(dids, main.write()::selectedDid);

    Label specDataType = new Label("—");
    Label specUnitScale = new Label("—");
    Label specRange = new Label("—");
    for (Label v : new Label[] {specDataType, specUnitScale, specRange}) {
      v.getStyleClass().add("kv-value");
      v.setWrapText(true);
    }
    GridPane specGrid = kvGrid();
    addKvRow(specGrid, 0, "Data Type & Length", specDataType);
    addKvRow(specGrid, 1, "Unit / Scale", specUnitScale);
    addKvRow(specGrid, 2, "Valid Input Range", specRange);

    Label specTitle = new Label("DID Write Specification");
    specTitle.getStyleClass().add("inspector-title");
    VBox specCard = new VBox(10, specTitle, specGrid);
    specCard.getStyleClass().add("inspector-card");
    specCard.setMaxWidth(writeWidth);

    Runnable refreshSpec =
        () -> {
          String didId = dids.getValue();
          if (didId != null) {
            main.write().selectDid(didId);
          }
          DidDefinition def = main.write().findDid(didId).orElse(null);
          specDataType.setText(WriteDataViewModel.formatDataTypeLength(def));
          specUnitScale.setText(WriteDataViewModel.formatUnitScale(def));
          specRange.setText(WriteDataViewModel.formatWriteRange(def));
        };
    dids.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refreshSpec.run());
    dids.getItems()
        .addListener((javafx.collections.ListChangeListener<String>) c -> refreshSpec.run());
    refreshSpec.run();

    Label badgeValidator = pipelineBadge("입력값 검증 (Validator)", "—");
    Label badgeGuard = pipelineBadge("세션/보안 권한 (Guard)", "—");
    Label badgeExchange = pipelineBadge("ECU 응답 (Exchange)", "—");
    HBox pipelineRow = new HBox(10, badgeValidator, badgeGuard, badgeExchange);
    pipelineRow.setAlignment(Pos.CENTER_LEFT);

    Label failureReason = new Label("");
    failureReason.getStyleClass().add("write-failure-reason");
    failureReason.setWrapText(true);
    failureReason.setVisible(false);
    failureReason.setManaged(false);

    Label rawRequest = new Label("(none)");
    Label rawEncoded = new Label("(none)");
    Label rawResponse = new Label("(none)");
    Label statusBadge = new Label("—");
    for (Label v : new Label[] {rawRequest, rawEncoded, rawResponse}) {
      v.getStyleClass().add("kv-value");
      v.setWrapText(true);
    }
    statusBadge.getStyleClass().addAll("status-badge");

    GridPane rawGrid = kvGrid();
    addKvRow(rawGrid, 0, "Raw Request", rawRequest);
    addKvRow(rawGrid, 1, "Encoded Payload", rawEncoded);
    addKvRow(rawGrid, 2, "Raw Response", rawResponse);
    addKvRow(rawGrid, 3, "Status", statusBadge);

    Label lastAcceptedBox = new Label(main.write().formatLastAccepted());
    lastAcceptedBox.getStyleClass().add("last-accepted-box");
    lastAcceptedBox.setWrapText(true);
    lastAcceptedBox.setMaxWidth(writeWidth);

    Label resultTitle = new Label("Write Pipeline");
    resultTitle.getStyleClass().add("inspector-title");
    VBox resultCard =
        new VBox(10, resultTitle, pipelineRow, failureReason, rawGrid, lastAcceptedBox);
    resultCard.getStyleClass().add("inspector-card");
    resultCard.setMaxWidth(writeWidth);

    Button write = new Button("Write");
    write.disableProperty().bind(Bindings.isEmpty(main.write().accessibleDidIds()));
    write.setOnAction(
        e -> {
          if (dids.getValue() != null) {
            main.write().selectDid(dids.getValue());
          }
          main.write().setInputValue(value.getText());
          WriteResult r = main.writeSelectedAndRecord();
          WritePipelineSnapshot pipe = WriteDataViewModel.pipelineOf(r);
          applyPipelineBadge(badgeValidator, "입력값 검증 (Validator)", pipe.validatorLabel(), pipe.validatorStyle());
          applyPipelineBadge(badgeGuard, "세션/보안 권한 (Guard)", pipe.guardLabel(), pipe.guardStyle());
          applyPipelineBadge(
              badgeExchange, "ECU 응답 (Exchange)", pipe.exchangeLabel(), pipe.exchangeStyle());
          boolean fail = pipe.failureReason() != null && !pipe.failureReason().isBlank();
          failureReason.setText(fail ? pipe.failureReason() : "");
          failureReason.setVisible(fail);
          failureReason.setManaged(fail);
          applyWriteRawResult(r, rawRequest, rawEncoded, rawResponse, statusBadge);
          lastAcceptedBox.setText(main.write().formatLastAccepted());
        });

    VBox inputSection =
        new VBox(8, new Label("DID"), dids, new Label("Value"), value, write, emptyHint);
    inputSection.setMaxWidth(writeWidth);

    VBox form =
        new VBox(15, inputSection, new Separator(), specCard, new Separator(), resultCard);
    form.setPadding(new Insets(20));
    form.setMaxWidth(writeWidth + 40);

    StackPane wrap = new StackPane(form);
    wrap.setAlignment(Pos.TOP_LEFT);
    return new Tab("WriteData", wrap);
  }

  private static void applyWriteRawResult(
      WriteResult r,
      Label rawRequest,
      Label rawEncoded,
      Label rawResponse,
      Label statusBadge) {
    if (r == null) {
      rawRequest.setText("(none)");
      rawEncoded.setText("(none)");
      rawResponse.setText("(none)");
      statusBadge.setText("—");
      statusBadge.getStyleClass().setAll("status-badge");
      return;
    }
    rawRequest.setText(blankToDash(r.requestHex()));
    rawEncoded.setText(blankToDash(r.encodedHex()));
    rawResponse.setText(blankToDash(r.responseHex()));
    statusBadge.getStyleClass().setAll("status-badge");
    WriteStatus st = r.status();
    statusBadge.setText(st == null ? "—" : st.name());
    if (st == WriteStatus.ACCEPTED) {
      statusBadge.getStyleClass().add("status-badge-normal");
    } else if (st == WriteStatus.ACCESS_DENIED || st == WriteStatus.REJECTED) {
      statusBadge.getStyleClass().add("status-badge-error");
    } else if (st == WriteStatus.VALIDATION_ERROR_TYPE || st == WriteStatus.VALIDATION_ERROR_RANGE) {
      statusBadge.getStyleClass().add("status-badge-warning");
    } else {
      statusBadge.getStyleClass().add("status-badge-error");
    }
  }
}
