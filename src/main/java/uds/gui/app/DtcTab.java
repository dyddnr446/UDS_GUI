package uds.gui.app;

import static uds.gui.app.TabControls.addKvRow;
import static uds.gui.app.TabControls.applyPipelineBadge;
import static uds.gui.app.TabControls.kvGrid;
import static uds.gui.app.TabControls.pipelineBadge;

import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import uds.gui.DtcRow;
import uds.gui.DtcViewModel;
import uds.gui.MainViewModel;
import uds.read.DtcPipelineSnapshot;

final class DtcTab {

  private DtcTab() {}

  static Tab create(MainViewModel main, boolean testMode) {
    final double comboW = 260;
    DtcViewModel dtcVm = main.dtc();

    Label hint = new Label("Unlock(Extended) 후 DTC를 읽거나 지웁니다. Offline fixture · Status Mask 필터.");
    hint.getStyleClass().add("control-label");
    hint.setWrapText(true);

    ComboBox<DtcViewModel.Option> subFn = new ComboBox<>(dtcVm.subFunctionOptions());
    subFn.setValue(dtcVm.selectedSubFunction());
    subFn.setPrefWidth(comboW);
    subFn.setMaxWidth(comboW);
    ComboBox<DtcViewModel.Option> mask = new ComboBox<>(dtcVm.statusMaskOptions());
    mask.setValue(dtcVm.selectedStatusMask());
    mask.setPrefWidth(comboW);
    mask.setMaxWidth(comboW);
    subFn.valueProperty().addListener((o, a, n) -> dtcVm.setSelectedSubFunction(n));
    mask.valueProperty().addListener((o, a, n) -> dtcVm.setSelectedStatusMask(n));

    Button readDtc = new Button("Read DTC (0x19)");
    readDtc.getStyleClass().add("dtc-btn-primary");

    ComboBox<DtcViewModel.Option> group = new ComboBox<>(dtcVm.groupOptions());
    group.setValue(dtcVm.selectedGroup());
    group.setPrefWidth(comboW);
    group.setMaxWidth(comboW);
    group.valueProperty().addListener((o, a, n) -> dtcVm.setSelectedGroup(n));

    Button clearDtc = new Button("Clear DTC (0x14)");
    clearDtc.getStyleClass().add("dtc-btn-danger");

    Label badgeGuard = pipelineBadge("세션/보안 (Guard)", "—");
    Label badgeExchange = pipelineBadge("ECU 응답 (Exchange)", "—");
    Label badgeParser = pipelineBadge("DTC 파싱 (Parser)", "—");
    shrinkPipelineBadge(badgeGuard);
    shrinkPipelineBadge(badgeExchange);
    shrinkPipelineBadge(badgeParser);

    Label lastActionBox = new Label();
    lastActionBox.getStyleClass().add("last-accepted-box");
    lastActionBox.setWrapText(true);
    lastActionBox.textProperty().bind(dtcVm.lastActionProperty());

    Label statusBadge = new Label("—");
    statusBadge.getStyleClass().addAll("status-badge");
    statusBadge.textProperty().bind(dtcVm.statusLabelProperty());

    Label nrcValue = new Label("—");
    Label rawReq = new Label("—");
    Label rawRsp = new Label("—");
    Label countLabel = new Label("—");
    for (Label v : new Label[] {nrcValue, rawReq, rawRsp, countLabel}) {
      v.getStyleClass().add("kv-value");
      v.setWrapText(true);
    }
    nrcValue.textProperty().bind(dtcVm.nrcTextProperty());
    rawReq.textProperty().bind(dtcVm.rawRequestProperty());
    rawRsp.textProperty().bind(dtcVm.rawResponseProperty());
    countLabel.textProperty().bind(dtcVm.dtcCountTextProperty());

    Runnable afterExchange =
        () -> {
          if (testMode) {
            main.refreshInterpreterLive();
          }
          applyDtcStatusBadgeStyle(statusBadge, dtcVm.statusLabelProperty().get());
          DtcPipelineSnapshot pipe = dtcVm.lastPipeline();
          applyPipelineBadge(
              badgeGuard, "세션/보안 (Guard)", pipe.guardLabel(), pipe.guardStyle());
          applyPipelineBadge(
              badgeExchange, "ECU 응답 (Exchange)", pipe.exchangeLabel(), pipe.exchangeStyle());
          applyPipelineBadge(
              badgeParser, "DTC 파싱 (Parser)", pipe.parserLabel(), pipe.parserStyle());
          shrinkPipelineBadge(badgeGuard);
          shrinkPipelineBadge(badgeExchange);
          shrinkPipelineBadge(badgeParser);
        };

    readDtc.setOnAction(
        e -> {
          dtcVm.readDtc();
          afterExchange.run();
        });
    clearDtc.setOnAction(
        e -> {
          Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
          confirm.setTitle("Clear DTC");
          confirm.setHeaderText("고장 코드(DTC)를 지우겠습니까?");
          confirm.setContentText("실차에서는 고장 기록이 삭제됩니다. Offline에서도 fixture 목록이 비워집니다.");
          confirm
              .showAndWait()
              .ifPresent(
                  btn -> {
                    if (btn == ButtonType.OK) {
                      dtcVm.clearDtc();
                      afterExchange.run();
                    }
                  });
        });
    Label readPreview = new Label();
    Label readBytes = new Label();
    Label clearPreview = new Label();
    Label clearBytes = new Label();
    Label access = new Label();
    for (Label v : new Label[] {readPreview, readBytes, clearPreview, clearBytes, access}) {
      v.getStyleClass().add("kv-value");
      v.setWrapText(true);
    }
    readPreview.textProperty().bind(dtcVm.requestPreviewProperty());
    readBytes.textProperty().bind(dtcVm.byteMeaningProperty());
    clearPreview.textProperty().bind(dtcVm.clearPreviewProperty());
    clearBytes.textProperty().bind(dtcVm.clearByteMeaningProperty());
    access.textProperty().bind(dtcVm.accessRequirementProperty());

    GridPane specGrid = kvGrid();
    addKvRow(specGrid, 0, "Read Preview", readPreview);
    addKvRow(specGrid, 1, "Read Bytes", readBytes);
    addKvRow(specGrid, 2, "Clear Preview", clearPreview);
    addKvRow(specGrid, 3, "Clear Bytes", clearBytes);
    addKvRow(specGrid, 4, "Access", access);
    Label specTitle = new Label("DTC Request Specification");
    specTitle.getStyleClass().add("inspector-title");
    VBox specCard = new VBox(6, specTitle, specGrid);
    specCard.getStyleClass().addAll("inspector-card", "dtc-compact-card");
    VBox.setVgrow(specCard, Priority.NEVER);

    HBox pipelineRow = new HBox(8, badgeGuard, badgeExchange, badgeParser);
    pipelineRow.setAlignment(Pos.CENTER_LEFT);
    Label pipeTitle = new Label("DTC Pipeline");
    pipeTitle.getStyleClass().add("inspector-title");
    VBox pipeCard = new VBox(6, pipeTitle, pipelineRow, lastActionBox);
    pipeCard.getStyleClass().addAll("inspector-card", "dtc-compact-card");

    VBox leftCol = new VBox(10, specCard, pipeCard);
    leftCol.setMinWidth(360);
    leftCol.setPrefWidth(420);
    HBox.setHgrow(leftCol, Priority.SOMETIMES);

    TableView<DtcRow> table = new TableView<>(dtcVm.rows());
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPrefHeight(220);
    table.setMinHeight(160);
    table.setPlaceholder(new Label("Read DTC 를 누르면 고장 코드 목록이 여기에 표시됩니다."));
    TableColumn<DtcRow, String> colCode = new TableColumn<>("DTC Code");
    colCode.setMinWidth(80);
    colCode.setCellValueFactory(c -> c.getValue().codeProperty());
    TableColumn<DtcRow, String> colName = new TableColumn<>("설명 (교육용)");
    colName.setMinWidth(140);
    colName.setCellValueFactory(c -> c.getValue().nameProperty());
    TableColumn<DtcRow, String> colSt = new TableColumn<>("Status");
    colSt.setMinWidth(60);
    colSt.setCellValueFactory(c -> c.getValue().statusByteProperty());
    TableColumn<DtcRow, String> colFlags = new TableColumn<>("활성 플래그");
    colFlags.setMinWidth(120);
    colFlags.setCellValueFactory(c -> c.getValue().flagsProperty());
    table.getColumns().addAll(List.of(colCode, colName, colSt, colFlags));
    VBox.setVgrow(table, Priority.ALWAYS);

    Label selectedDetail = new Label("표를 클릭하면 선택한 DTC 설명이 여기에 표시됩니다.");
    selectedDetail.getStyleClass().add("kv-value");
    selectedDetail.setWrapText(true);
    selectedDetail.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
    table
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, oldRow, row) -> {
              if (row == null) {
                selectedDetail.setText("표를 클릭하면 선택한 DTC 설명이 여기에 표시됩니다.");
                return;
              }
              selectedDetail.setText(
                  row.codeProperty().get()
                      + "  ·  "
                      + row.nameProperty().get()
                      + "\n상태 "
                      + row.statusByteProperty().get()
                      + "  →  "
                      + row.flagsProperty().get());
            });

    Label emptyHint = new Label();
    emptyHint.getStyleClass().add("control-label");
    emptyHint.setWrapText(true);
    emptyHint.textProperty().bind(dtcVm.emptyHintProperty());
    emptyHint.visibleProperty().bind(Bindings.isNotEmpty(dtcVm.emptyHintProperty()));
    emptyHint.managedProperty().bind(emptyHint.visibleProperty());

    GridPane resultGrid = kvGrid();
    addKvRow(resultGrid, 0, "Status", statusBadge);
    addKvRow(resultGrid, 1, "NRC", nrcValue);
    addKvRow(resultGrid, 2, "Raw Request", rawReq);
    addKvRow(resultGrid, 3, "Raw Response", rawRsp);
    addKvRow(resultGrid, 4, "Summary", countLabel);

    Label resultTitle = new Label("DTC Result");
    resultTitle.getStyleClass().add("inspector-title");
    Label detailTitle = new Label("선택 DTC 설명");
    detailTitle.getStyleClass().add("kv-key");
    VBox resultCard =
        new VBox(6, resultTitle, resultGrid, table, detailTitle, selectedDetail, emptyHint);
    resultCard.getStyleClass().addAll("inspector-card", "dtc-compact-card");
    HBox.setHgrow(resultCard, Priority.ALWAYS);
    resultCard.setMinWidth(420);

    Label subLbl = new Label("Sub-Function");
    subLbl.getStyleClass().add("kv-key");
    Label maskLbl = new Label("Status Mask");
    maskLbl.getStyleClass().add("kv-key");
    Label groupLbl = new Label("Group of DTC");
    groupLbl.getStyleClass().add("kv-key");

    HBox readRow = new HBox(10, subLbl, subFn, maskLbl, mask, readDtc);
    readRow.setAlignment(Pos.CENTER_LEFT);
    HBox clearRow = new HBox(10, groupLbl, group, clearDtc);
    clearRow.setAlignment(Pos.CENTER_LEFT);

    VBox controls = new VBox(8, hint, readRow, clearRow);
    controls.getStyleClass().add("dtc-controls");

    HBox body = new HBox(12, leftCol, resultCard);
    body.setAlignment(Pos.TOP_LEFT);
    HBox.setHgrow(resultCard, Priority.ALWAYS);

    VBox form = new VBox(12, controls, body);
    form.setPadding(new Insets(12, 16, 16, 16));
    form.getStyleClass().add("dtc-tab-root");

    ScrollPane scroll = new ScrollPane(form);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.getStyleClass().add("dtc-scroll");
    return new Tab("DTC", scroll);
  }

  private static void shrinkPipelineBadge(Label badge) {
    badge.setMinWidth(120);
    badge.setMaxWidth(150);
    badge.setPrefWidth(140);
  }

  private static void applyDtcStatusBadgeStyle(Label statusBadge, String status) {
    statusBadge.getStyleClass().setAll("status-badge");
    if (status == null) {
      return;
    }
    switch (status) {
      case "POSITIVE" -> statusBadge.getStyleClass().add("status-badge-normal");
      case "NEGATIVE", "ACCESS_DENIED", "EMPTY" ->
          statusBadge.getStyleClass().add("status-badge-error");
      default -> {
        /* keep base */
      }
    }
  }
}
