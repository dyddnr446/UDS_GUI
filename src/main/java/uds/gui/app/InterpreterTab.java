package uds.gui.app;

import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;
import uds.gui.InterpreterController;
import uds.gui.InterpreterRow;
import uds.gui.MainViewModel;
import uds.interpreter.DecodeKind;

final class InterpreterTab {

  private InterpreterTab() {}

  static Tab create(MainViewModel main, boolean testMode, Window owner) {
    Label summary = new Label();
    summary.textProperty().bind(main.interpreter().summaryChipProperty());
    Label exportStatus = new Label("");

    TableView<InterpreterRow> table = new TableView<>(main.interpreter().filteredRows());
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    table.getStyleClass().add("interp-table");

    TableColumn<InterpreterRow, String> timeCol = new TableColumn<>();
    Label timeHeader = new Label("Time");
    timeHeader.setTooltip(new Tooltip("HH:mm:ss.SSS (local)  ·  sample: +elapsed s"));
    timeCol.setGraphic(timeHeader);
    timeCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().time()));
    timeCol.setMinWidth(100);
    timeCol.setPrefWidth(110);

    TableColumn<InterpreterRow, String> dirCol = new TableColumn<>("Dir");
    dirCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().dir()));
    dirCol.setMinWidth(50);
    dirCol.setPrefWidth(55);

    TableColumn<InterpreterRow, String> sidCol = new TableColumn<>("SID");
    sidCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().sid()));
    sidCol.setMinWidth(60);
    sidCol.setPrefWidth(70);

    TableColumn<InterpreterRow, String> serviceCol = new TableColumn<>("Service");
    serviceCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().service()));
    serviceCol.setMinWidth(140);
    serviceCol.setPrefWidth(180);

    TableColumn<InterpreterRow, String> subCol = new TableColumn<>("Sub/DID/NRC");
    subCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().subDidNrc()));
    subCol.setMinWidth(120);
    subCol.setPrefWidth(150);

    TableColumn<InterpreterRow, String> dataCol = new TableColumn<>("Data / Fields");
    dataCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().data()));
    dataCol.setMinWidth(200);

    table.getColumns().addAll(List.of(timeCol, dirCol, sidCol, serviceCol, subCol, dataCol));

    TextArea detail = new TextArea();
    detail.setEditable(false);
    detail.setWrapText(true);
    detail.setPromptText("Select a row to inspect full fields and Raw Hex");
    detail.setPrefRowCount(8);

    MenuItem copyRow = new MenuItem("선택 행 복사 (Copy Row)");
    copyRow.setOnAction(
        e -> {
          InterpreterRow selected = table.getSelectionModel().getSelectedItem();
          if (selected == null) {
            return;
          }
          ClipboardContent content = new ClipboardContent();
          content.putString(selected.copyTsv());
          Clipboard.getSystemClipboard().setContent(content);
        });
    ContextMenu rowMenu = new ContextMenu(copyRow);

    table.setRowFactory(
        tv -> {
          TableRow<InterpreterRow> row =
              new TableRow<>() {
                @Override
                protected void updateItem(InterpreterRow item, boolean empty) {
                  super.updateItem(item, empty);
                  getStyleClass()
                      .removeAll(
                          "interp-row-negative", "interp-row-error", "interp-row-redacted");
                  if (empty || item == null) {
                    return;
                  }
                  if (item.isError()) {
                    getStyleClass().add("interp-row-error");
                  } else if (item.kind() == DecodeKind.NEGATIVE) {
                    getStyleClass().add("interp-row-negative");
                  } else if (item.isRedacted()) {
                    getStyleClass().add("interp-row-redacted");
                  }
                }
              };
          row.contextMenuProperty()
              .bind(
                  Bindings.when(row.emptyProperty())
                      .then((ContextMenu) null)
                      .otherwise(rowMenu));
          return row;
        });

    table
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, oldRow, newRow) -> {
              if (newRow == null) {
                detail.clear();
              } else {
                detail.setText(newRow.detailText());
              }
            });

    Button loadLive = new Button("이번 세션 로그");
    loadLive.setDisable(!testMode);
    loadLive.setOnAction(e -> main.refreshInterpreterLive());
    Button clearLive = new Button("로그 지우기");
    clearLive.setDisable(!testMode);
    clearLive.setOnAction(e -> main.clearInterpreterLive());
    if (testMode) {
      main.refreshInterpreterLive();
    }

    Button exportJson = new Button("Export JSON…");
    exportJson.setDisable(!testMode);
    exportJson.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
    exportJson.setOnAction(
        e -> {
          FileChooser chooser = new FileChooser();
          chooser.setTitle("Export decoded JSON");
          chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
          chooser.setInitialFileName("decoded_frames.json");
          java.io.File f = chooser.showSaveDialog(owner);
          if (f != null) {
            main.interpreter().exportDecodedJson(f.toPath());
            exportStatus.setText("exported " + f.getName());
          }
        });

    ComboBox<InterpreterController.FilterMode> logLevel = new ComboBox<>();
    logLevel
        .getItems()
        .addAll(
            InterpreterController.FilterMode.NORMAL,
            InterpreterController.FilterMode.VERBOSE,
            InterpreterController.FilterMode.ERRORS_ONLY);
    logLevel.setValue(InterpreterController.FilterMode.NORMAL);
    logLevel.setDisable(!testMode);
    logLevel.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(InterpreterController.FilterMode mode) {
            if (mode == null) {
              return "";
            }
            return switch (mode) {
              case NORMAL -> "Normal (3E 숨김)";
              case VERBOSE -> "Verbose (전체)";
              case ERRORS_ONLY -> "Errors only";
            };
          }

          @Override
          public InterpreterController.FilterMode fromString(String string) {
            return InterpreterController.FilterMode.NORMAL;
          }
        });
    logLevel
        .valueProperty()
        .addListener(
            (obs, o, mode) -> {
              if (mode != null) {
                main.interpreter().setFilterMode(mode);
              }
            });

    Label logLevelLabel = new Label("Log level");
    Region filterSpacer = new Region();
    HBox.setHgrow(filterSpacer, Priority.ALWAYS);
    HBox filterRow =
        new HBox(8, logLevelLabel, logLevel, filterSpacer, exportJson, exportStatus);
    filterRow.setAlignment(Pos.CENTER_LEFT);

    SplitPane split = new SplitPane(table, detail);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.72);

    VBox box =
        new VBox(8, new HBox(8, loadLive, clearLive), summary, filterRow, split);
    box.setPadding(new Insets(12));
    VBox.setVgrow(split, Priority.ALWAYS);
    Tab tab = new Tab("Interpreter", box);
    tab.setOnSelectionChanged(
        e -> {
          if (tab.isSelected() && testMode) {
            main.refreshInterpreterLive();
          }
        });
    return tab;
  }
}
