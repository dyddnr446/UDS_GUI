package uds.gui.app;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import uds.access.AccessOutcome;
import uds.access.DiagnosticAccessState;
import uds.catalog.model.DidDefinition;
import uds.gui.DiagnosticSessionChoice;
import uds.gui.ReadDataViewModel;
import uds.gui.WriteDataViewModel;
import uds.gui.WriteDataViewModel.WritePipelineSnapshot;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import uds.gui.InterpreterRow;
import uds.interpreter.DecodeKind;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import uds.ai.ExternalAiClient;
import uds.gui.InterpreterController;
import uds.gui.MainViewModel;
import uds.read.FindingDetailCode;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;
import uds.write.WriteResult;

/** JavaFX entry — diagnostic shell (TEST offline / future CAN) with Control Bar + gated tabs. */
public final class UdsGuiApplication extends Application {

  private static final double FORM_MAX_WIDTH = 480;

  private MainViewModel main;
  private TabPane tabs;
  private boolean testMode;
  private String demoPassword;

  @Override
  public void start(Stage stage) throws Exception {
    try {
      startUi(stage);
    } catch (Throwable t) {
      t.printStackTrace(System.err);
      try {
        Path logDir = Path.of(System.getProperty("user.dir", ".")).resolve("output");
        Files.createDirectories(logDir);
        Files.writeString(
            logDir.resolve("crash.log"),
            stackTraceOf(t),
            java.nio.charset.StandardCharsets.UTF_8);
      } catch (Exception ignored) {
        // best-effort
      }
      throw t;
    }
  }

  private void startUi(Stage stage) throws Exception {
    DotEnv.loadNear(Path.of(System.getProperty("user.dir", ".")));
    Path root = resolveAppRoot();
    AppConfig config = AppConfig.fromEnvironment(root);
    this.testMode = config.testMode();
    this.demoPassword = config.demoPassword();
    AppSession session = AppSession.open(config, root.resolve("output"), "gui-session");
    main =
        new MainViewModel(
            session, config.samplesDir(), new ExternalAiClient(), new UiTaskRunner(), config.testMode());
    main.bindKeepAliveInterpreterRefresh();

    Tab interpreter = interpreterTab();
    Tab read = readTab();
    Tab write = writeTab();
    Tab ai = aiTab();

    read.disableProperty().bind(main.readTabEnabledProperty().not());
    write.disableProperty().bind(main.writeTabEnabledProperty().not());
    ai.disableProperty().bind(main.aiTabEnabledProperty().not());

    tabs = new TabPane();
    // Analysis Session save/restore UI removed — in-memory store still used by AI/tests.
    tabs.getTabs().addAll(interpreter, read, write, ai);

    main.requestedTabIndexProperty()
        .addListener(
            (obs, o, idx) -> {
              int i = idx.intValue();
              if (i >= 0 && i < tabs.getTabs().size()) {
                Tab t = tabs.getTabs().get(i);
                if (!t.isDisable()) {
                  tabs.getSelectionModel().select(i);
                }
              }
            });

    BorderPane rootPane = new BorderPane();
    rootPane.getStyleClass().add("root-pane");
    rootPane.setTop(controlBar());
    rootPane.setCenter(tabs);
    rootPane.setBottom(statusBar());

    stage.setTitle("UDS Diagnostic Tool");
    Image appIcon = loadAppIcon();
    if (appIcon != null) {
      stage.getIcons().setAll(appIcon);
    }
    Scene scene = new Scene(rootPane, 1100, 720);
    URL css = UdsGuiApplication.class.getResource("/css/uds-app.css");
    if (css != null) {
      scene.getStylesheets().add(css.toExternalForm());
    }
    stage.setScene(scene);
    stage.show();
  }

  /** Window / taskbar icon — {@code images.jpg} (Chiikawa). */
  private static Image loadAppIcon() {
    URL url = UdsGuiApplication.class.getResource("/images.jpg");
    if (url == null) {
      url = UdsGuiApplication.class.getResource("/images/app-icon.jpg");
    }
    if (url == null) {
      return null;
    }
    return new Image(url.toExternalForm(), 256, 256, true, true);
  }

  private HBox controlBar() {
    ComboBox<String> iface = new ComboBox<>();
    iface.getItems().add(main.connectionId());
    iface.getSelectionModel().select(0);
    iface.setDisable(true);
    iface.setPrefWidth(200);
    iface.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(String connectionId) {
            return connectionDisplayName(connectionId);
          }

          @Override
          public String fromString(String string) {
            return string;
          }
        });
    iface.setCellFactory(
        list ->
            new ListCell<>() {
              @Override
              protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : connectionDisplayName(item));
              }
            });
    iface.setButtonCell(
        new ListCell<>() {
          @Override
          protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : connectionDisplayName(item));
          }
        });

    ComboBox<String> profiles =
        new ComboBox<>(FXCollections.observableArrayList(main.access().profileIds()));
    profiles.setPrefWidth(220);
    profiles.setPromptText("Target ECU");

    ComboBox<DiagnosticSessionChoice> sessionControl = new ComboBox<>();
    sessionControl.setPrefWidth(200);
    sessionControl.setDisable(!testMode);
    sessionControl.setPromptText("Select ECU first");
    // toString() on DiagnosticSessionChoice is enough; avoid converter+buttonCell clash (blank button).
    sessionControl.setCellFactory(
        list ->
            new ListCell<>() {
              @Override
              protected void updateItem(DiagnosticSessionChoice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
              }
            });

    Runnable refreshSessions =
        () -> {
          String profileId = profiles.getValue();
          List<DiagnosticSessionChoice> sessions = main.sessionsForProfile(profileId);
          String previousKey =
              sessionControl.getValue() == null ? null : sessionControl.getValue().key();
          sessionControl.getItems().setAll(sessions);
          if (sessions.isEmpty()) {
            sessionControl.getSelectionModel().clearSelection();
            sessionControl.setPromptText(
                profileId == null || profileId.isBlank() ? "Select ECU first" : "No sessions");
            return;
          }
          DiagnosticSessionChoice keep =
              sessions.stream()
                  .filter(s -> s.key().equals(previousKey))
                  .findFirst()
                  .orElseGet(
                      () ->
                          sessions.stream()
                              .filter(DiagnosticSessionChoice::isDefault)
                              .findFirst()
                              .orElse(sessions.get(0)));
          sessionControl.getSelectionModel().select(keep);
        };

    profiles.setOnAction(
        e -> {
          String p = profiles.getValue();
          if (p != null) {
            main.selectProfile(p);
          }
          refreshSessions.run();
        });
    // Pre-select first ECU so Session Control is populated on launch.
    if (!profiles.getItems().isEmpty() && profiles.getValue() == null) {
      profiles.getSelectionModel().select(0);
      String p = profiles.getValue();
      if (p != null) {
        main.selectProfile(p);
      }
    }
    refreshSessions.run();

    Button applySession = new Button("Apply");
    applySession.setDisable(!testMode);
    applySession.setOnAction(
        e -> {
          Window owner = ownerWindow(applySession);
          DiagnosticSessionChoice sel = sessionControl.getValue();
          if (sel == null) {
            UiDialogs.warn(owner, "Session Control", "Select a session first.");
            return;
          }
          if (profiles.getValue() == null || profiles.getValue().isBlank()) {
            UiDialogs.warn(owner, "Session Control", "Select Target ECU first.");
            return;
          }
          try {
            AccessOutcome out;
            if (sel.isExtended()) {
              String password = main.rememberedUnlockPassword();
              if (main.needsUnlockPasswordPrompt()) {
                var typedOpt =
                    UiDialogs.promptPassword(
                        owner,
                        "Extended Session",
                        "Extended 접속에는 Security Unlock 비밀번호가 필요합니다",
                        "Password (empty = env demo)");
                if (typedOpt.isEmpty()) {
                  return; // cancelled — do not enter Extended
                }
                String typed = typedOpt.get();
                password =
                    typed.isBlank() && demoPassword != null && !demoPassword.isBlank()
                        ? demoPassword
                        : typed;
              }
              if (password == null || password.isBlank()) {
                UiDialogs.warn(owner, "Session Control", "Seed/Key Password required.");
                return;
              }
              out = main.applyExtendedWithPassword(password);
            } else {
              out = main.applySessionControl(sel);
            }
            // Keep Session Control combo aligned with actual access state after Apply.
            DiagnosticAccessState state = out.resultingState();
            if (state == DiagnosticAccessState.DEFAULT_LOCKED) {
              sessionControl.getItems().stream()
                  .filter(DiagnosticSessionChoice::isDefault)
                  .findFirst()
                  .ifPresent(sessionControl.getSelectionModel()::select);
            } else if (state == DiagnosticAccessState.EXTENDED_LOCKED
                || state == DiagnosticAccessState.EXTENDED_UNLOCKED) {
              sessionControl.getItems().stream()
                  .filter(DiagnosticSessionChoice::isExtended)
                  .findFirst()
                  .ifPresent(sessionControl.getSelectionModel()::select);
            }
            String err = out.errorCode();
            if (err != null && !err.isBlank()) {
              String detail =
                  out.responseHex() != null && !out.responseHex().isBlank()
                      ? out.responseHex()
                      : ("state=" + out.resultingState());
              UiDialogs.warn(
                  owner,
                  "Session Control",
                  sel.displayName() + "\n" + err + "\n" + detail);
            } else {
              UiDialogs.info(
                  owner,
                  "Session Control",
                  sel.displayName() + "\n→ " + out.resultingState());
            }
          } catch (RuntimeException ex) {
            UiDialogs.error(owner, "Session Control", ex.getMessage());
            ex.printStackTrace(System.err);
          }
        });

    VBox ifaceCol = labeledField("Connection", iface);
    VBox profileCol = labeledField("Target ECU", profiles);
    VBox sessionCol = labeledField("Session Control", sessionControl);
    VBox applyCol = labeledField(" ", applySession);

    HBox connection = new HBox(12, ifaceCol, profileCol);
    connection.setAlignment(Pos.BOTTOM_LEFT);
    HBox session = new HBox(8, sessionCol, applyCol);
    session.setAlignment(Pos.BOTTOM_LEFT);

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox bar = new HBox(16, connection, spacer, session);
    bar.getStyleClass().add("control-bar");
    bar.setAlignment(Pos.CENTER_LEFT);
    return bar;
  }

  private static VBox labeledField(String title, javafx.scene.Node field) {
    Label label = new Label(title);
    label.getStyleClass().add("control-label");
    VBox box = new VBox(4, label, field);
    return box;
  }

  private static Window ownerWindow(javafx.scene.Node node) {
    if (node == null || node.getScene() == null) {
      return null;
    }
    return node.getScene().getWindow();
  }

  /** UI-only label for connection combo; backend ids stay VIRTUAL / CAN. */
  private static String connectionDisplayName(String connectionId) {
    if (MainViewModel.INTERFACE_VIRTUAL.equals(connectionId)) {
      return "Offline Simulation";
    }
    if (MainViewModel.INTERFACE_CAN.equals(connectionId)) {
      return "CAN Bus (coming soon)";
    }
    return connectionId == null ? "" : connectionId;
  }

  private HBox statusBar() {
    Label iface = new Label();
    iface.textProperty().bind(main.statusInterfaceProperty());
    Label profile = new Label();
    profile.textProperty().bind(main.statusProfileProperty());
    Label session = new Label();
    session.textProperty().bind(main.statusSessionProperty());
    Label security = new Label();
    security.textProperty().bind(main.statusSecurityProperty());
    Label redaction = new Label();
    redaction.textProperty().bind(main.statusRedactionProperty());
    Label transport = new Label();
    transport.textProperty().bind(main.transportMessageProperty());
    transport.setWrapText(true);
    for (Label chip : new Label[] {iface, profile, session, security, redaction}) {
      chip.getStyleClass().add("status-chip");
    }
    HBox bar = new HBox(10, iface, profile, session, security, redaction, transport);
    bar.getStyleClass().add("status-bar");
    return bar;
  }

  private Tab interpreterTab() {
    Label summary = new Label();
    summary.textProperty().bind(main.interpreter().summaryChipProperty());
    Label exportStatus = new Label("");

    TableView<InterpreterRow> table = new TableView<>(main.interpreter().filteredRows());
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    table.getStyleClass().add("interp-table");

    TableColumn<InterpreterRow, String> timeCol = new TableColumn<>("Time");
    timeCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().time()));
    timeCol.setMinWidth(70);
    timeCol.setPrefWidth(80);

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
          java.io.File f = chooser.showSaveDialog(tabs.getScene().getWindow());
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

  private Tab readTab() {
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

    Label rawValue = new Label("(none)");
    Label physicalValue = new Label("(none)");
    Label statusBadge = new Label("—");
    rawValue.getStyleClass().add("kv-value");
    physicalValue.getStyleClass().add("kv-value");
    rawValue.setWrapText(true);
    physicalValue.setWrapText(true);
    statusBadge.getStyleClass().addAll("status-badge");

    GridPane resultGrid = kvGrid();
    addKvRow(resultGrid, 0, "Raw Response", rawValue);
    addKvRow(resultGrid, 1, "Physical Value", physicalValue);
    addKvRow(resultGrid, 2, "Status", statusBadge);

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
          applyReadResult(r, rawValue, physicalValue, statusBadge);
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

  private static GridPane kvGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(16);
    grid.setVgap(8);
    ColumnConstraints keyCol = new ColumnConstraints();
    keyCol.setMinWidth(120);
    ColumnConstraints valCol = new ColumnConstraints();
    valCol.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(keyCol, valCol);
    return grid;
  }

  private static void addKvRow(GridPane grid, int row, String key, javafx.scene.Node value) {
    Label keyLabel = new Label(key);
    keyLabel.getStyleClass().add("kv-key");
    grid.add(keyLabel, 0, row);
    grid.add(value, 1, row);
  }

  private static void applyReadResult(
      ReadDataResult r, Label rawValue, Label physicalValue, Label statusBadge) {
    if (r == null) {
      rawValue.setText("(none)");
      physicalValue.setText("(none)");
      statusBadge.setText("—");
      statusBadge.getStyleClass().setAll("status-badge");
      return;
    }
    String hex = r.responseHex();
    rawValue.setText(hex == null || hex.isBlank() ? "(none)" : hex);

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

  private static String blankToDash(String value) {
    return value == null || value.isBlank() ? "(none)" : value;
  }

  private Tab writeTab() {
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

    Label lastAcceptedBox = new Label(main.write().formatLastAccepted());
    lastAcceptedBox.getStyleClass().add("last-accepted-box");
    lastAcceptedBox.setWrapText(true);
    lastAcceptedBox.setMaxWidth(writeWidth);

    Label resultTitle = new Label("Write Pipeline");
    resultTitle.getStyleClass().add("inspector-title");
    VBox resultCard =
        new VBox(10, resultTitle, pipelineRow, failureReason, lastAcceptedBox);
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

  private static Label pipelineBadge(String stage, String state) {
    Label badge = new Label(stage + "\n" + state);
    badge.getStyleClass().addAll("pipeline-badge", "pipeline-badge-idle");
    badge.setWrapText(true);
    badge.setMinWidth(140);
    badge.setMaxWidth(160);
    return badge;
  }

  private static void applyPipelineBadge(Label badge, String stage, String state, String style) {
    badge.setText(stage + "\n" + state);
    badge.getStyleClass().setAll("pipeline-badge");
    switch (style == null ? "idle" : style) {
      case "ok" -> badge.getStyleClass().add("pipeline-badge-ok");
      case "error" -> badge.getStyleClass().add("pipeline-badge-error");
      case "warn" -> badge.getStyleClass().add("pipeline-badge-warn");
      case "info" -> badge.getStyleClass().add("pipeline-badge-info");
      default -> badge.getStyleClass().add("pipeline-badge-idle");
    }
  }

  private static void syncDidSelection(
      ComboBox<String> dids, java.util.function.Supplier<String> selectedDid) {
    javafx.collections.ListChangeListener<String> sync =
        c -> {
          String did = selectedDid.get();
          if (did != null && dids.getItems().contains(did)) {
            dids.getSelectionModel().select(did);
          } else {
            dids.getSelectionModel().clearSelection();
          }
        };
    dids.getItems().addListener(sync);
  }

  private Tab aiTab() {
    TextField symptom = new TextField();
    symptom.setPromptText("차량의 이상 현상이나 에러 키워드를 입력하세요 (예: 급정지)");
    symptom.getStyleClass().add("ai-search-field");
    HBox.setHgrow(symptom, Priority.ALWAYS);

    Button oneClick = new Button("원클릭 진단 실행 (Suggest → Read → AI)");
    oneClick.getStyleClass().add("ai-primary-button");
    oneClick.disableProperty().bind(main.ai().runningProperty());

    Button suggest = new Button("Suggest DIDs");
    Button readSuggested = new Button("Read suggested");
    Button ask = new Button("Ask AI");
    for (Button b : new Button[] {suggest, readSuggested, ask}) {
      b.getStyleClass().add("ai-advanced-button");
      b.disableProperty().bind(main.ai().runningProperty());
    }
    Label advancedLabel = new Label("Advanced");
    advancedLabel.getStyleClass().add("control-label");
    HBox advanced = new HBox(8, advancedLabel, suggest, readSuggested, ask);
    advanced.setAlignment(Pos.CENTER_LEFT);

    Button cancel = new Button("Cancel AI");
    cancel.getStyleClass().add("ai-cancel-button");
    cancel.visibleProperty().bind(main.ai().runningProperty());
    cancel.managedProperty().bind(main.ai().runningProperty());
    cancel.setOnAction(e -> main.ai().cancel());

    Label status = new Label();
    status.getStyleClass().add("control-label");
    status.textProperty().bind(main.ai().statusMessageProperty());
    Label errorBanner = new Label();
    errorBanner.getStyleClass().add("security-banner");
    errorBanner.textProperty().bind(main.ai().errorBannerProperty());
    errorBanner.setWrapText(true);

    HBox searchRow = new HBox(12, symptom, oneClick);
    searchRow.setAlignment(Pos.CENTER_LEFT);
    HBox metaRow = new HBox(12, advanced, cancel, status);
    metaRow.setAlignment(Pos.CENTER_LEFT);
    VBox topBar = new VBox(8, searchRow, metaRow, errorBanner);
    topBar.getStyleClass().add("ai-top-bar");

    ListView<String> factList = new ListView<>();
    factList.getStyleClass().add("ai-fact-list");
    factList.setCellFactory(
        list ->
            new ListCell<>() {
              @Override
              protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass()
                    .removeAll(
                        "fact-row-normal", "fact-row-abnormal", "fact-row-error", "fact-row-access");
                if (empty || item == null) {
                  setText(null);
                  setGraphic(null);
                  return;
                }
                setText(item);
                String upper = item.toUpperCase();
                if (upper.contains(" NORMAL")) {
                  getStyleClass().add("fact-row-normal");
                } else if (upper.contains(" ABNORMAL") || upper.contains(" OUT_OF_RANGE")) {
                  getStyleClass().add("fact-row-abnormal");
                } else if (upper.contains("REJECTED")
                    || upper.contains("PARSE_ERROR")
                    || upper.contains("ACCESS_DENIED")
                    || upper.contains(" ERROR")) {
                  getStyleClass().add("fact-row-error");
                } else if (upper.contains(" ACCESS ")) {
                  getStyleClass().add("fact-row-access");
                }
              }
            });

    Label leftTitle = new Label("진단 데이터 (Facts & Findings)");
    leftTitle.getStyleClass().add("inspector-title");
    Label factHint = new Label("수집된 DID·Finding·Access evidence");
    factHint.getStyleClass().add("control-label");
    VBox leftPanel = new VBox(10, leftTitle, factHint, factList);
    leftPanel.getStyleClass().add("ai-panel");
    VBox.setVgrow(factList, Priority.ALWAYS);

    FlowPane evidenceChips = new FlowPane(8, 8);
    evidenceChips.getStyleClass().add("evidence-chip-row");
    TextArea aiZone = new TextArea();
    aiZone.setEditable(false);
    aiZone.setWrapText(true);
    aiZone.setPromptText("AI 진단 설명이 여기에 표시됩니다.");
    aiZone.getStyleClass().add("ai-advice-area");
    VBox.setVgrow(aiZone, Priority.ALWAYS);

    Label rightTitle = new Label("AI 진단 분석 (Advisor)");
    rightTitle.getStyleClass().add("inspector-title");
    Label chipTitle = new Label("Evidence");
    chipTitle.getStyleClass().add("control-label");
    VBox rightContent = new VBox(10, rightTitle, chipTitle, evidenceChips, aiZone);
    rightContent.getStyleClass().add("ai-panel");

    ProgressIndicator overlayProgress = new ProgressIndicator();
    overlayProgress.setMaxSize(56, 56);
    Label overlayLabel = new Label("진단 진행 중…");
    overlayLabel.getStyleClass().add("ai-overlay-label");
    VBox overlayBox = new VBox(12, overlayProgress, overlayLabel);
    overlayBox.setAlignment(Pos.CENTER);
    overlayBox.getStyleClass().add("ai-overlay");
    overlayBox.visibleProperty().bind(main.ai().runningProperty());
    overlayBox.managedProperty().bind(main.ai().runningProperty());
    overlayBox.setMouseTransparent(false);

    StackPane rightStack = new StackPane(rightContent, overlayBox);
    StackPane.setAlignment(overlayBox, Pos.CENTER);

    SplitPane split = new SplitPane(leftPanel, rightStack);
    split.setDividerPositions(0.42);
    split.getStyleClass().add("ai-split");
    VBox.setVgrow(split, Priority.ALWAYS);

    Runnable refreshZones =
        () -> {
          factList.getItems().setAll(main.ai().factEvidenceLines());
          aiZone.setText(main.ai().aiZone());
          evidenceChips.getChildren().clear();
          java.util.LinkedHashSet<String> chipSeen = new java.util.LinkedHashSet<>();
          for (String id : main.ai().displayedEvidenceIds()) {
            String label = uds.gui.AiAdvisorController.shortEvidenceLabel(id);
            if (label.isBlank() || !chipSeen.add(label)) {
              continue;
            }
            if (evidenceChips.getChildren().size() >= 12) {
              break;
            }
            Button chip = new Button(label);
            chip.getStyleClass().add("evidence-chip");
            chip.setOnAction(
                ev -> {
                  main.ai().selectEvidence(id);
                  for (int i = 0; i < factList.getItems().size(); i++) {
                    if (factList.getItems().get(i).startsWith(label)) {
                      factList.getSelectionModel().select(i);
                      factList.scrollTo(i);
                      break;
                    }
                  }
                });
            evidenceChips.getChildren().add(chip);
          }
          String highlight = main.ai().highlightedEvidenceId();
          if (highlight != null && !highlight.isBlank()) {
            String hiLabel = uds.gui.AiAdvisorController.shortEvidenceLabel(highlight);
            for (int i = 0; i < factList.getItems().size(); i++) {
              if (factList.getItems().get(i).startsWith(hiLabel)) {
                factList.getSelectionModel().select(i);
                factList.scrollTo(i);
                break;
              }
            }
          }
        };

    oneClick.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().runOneClickDiagnosis();
          refreshZones.run();
        });
    suggest.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().onProfileChanged(main.access().selectedProfileId());
          main.ai().refreshSuggestions();
          refreshZones.run();
        });
    readSuggested.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().refreshSuggestions();
          main.ai().runSuggestedReads();
          refreshZones.run();
        });
    ask.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().requestAdvice();
        });
    symptom.setOnAction(e -> oneClick.fire());

    main.ai()
        .runningProperty()
        .addListener(
            (obs, was, is) -> {
              if (!is) {
                refreshZones.run();
              }
            });
    main.ai().statusMessageProperty().addListener((obs, o, n) -> refreshZones.run());
    refreshZones.run();

    VBox root = new VBox(15, topBar, split);
    root.setPadding(new Insets(20));
    root.getStyleClass().add("ai-root");
    return new Tab("AI Advisor", root);
  }

  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          error.printStackTrace(System.err);
          try {
            Path logDir = Path.of(System.getProperty("user.dir", ".")).resolve("output");
            Files.createDirectories(logDir);
            Path crash = logDir.resolve("crash.log");
            Files.writeString(
                crash,
                thread.getName() + "\n" + stackTraceOf(error) + "\n",
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
          } catch (Exception ignored) {
            // best-effort crash log
          }
        });
    DotEnv.loadNear(Path.of(System.getProperty("user.dir", ".")));
    launch(args);
  }

  private static String stackTraceOf(Throwable error) {
    java.io.StringWriter sw = new java.io.StringWriter();
    error.printStackTrace(new java.io.PrintWriter(sw));
    return sw.toString();
  }

  /**
   * Project / install root that contains the catalog (default {@code catalog/uds_system_catalog.xlsx}).
   * Order: {@code UDS_APP_HOME} (OS or .env) → class/jar location walk-up → {@code user.dir}.
   */
  static Path resolveAppRoot() {
    String env = DotEnv.get(AppConfig.KEY_APP_HOME);
    if (env != null && !env.isBlank()) {
      Path p = Path.of(env).toAbsolutePath().normalize();
      if (looksLikeAppRoot(p)) {
        return p;
      }
    }
    Path fromCode = detectRootFromCodeSource();
    if (fromCode != null && looksLikeAppRoot(fromCode)) {
      return fromCode;
    }
    Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    if (looksLikeAppRoot(cwd)) {
      return cwd;
    }
    Path walk = cwd;
    for (int i = 0; i < 6 && walk != null; i++) {
      if (looksLikeAppRoot(walk)) {
        return walk;
      }
      walk = walk.getParent();
    }
    return cwd;
  }

  private static boolean looksLikeAppRoot(Path dir) {
    return dir != null
        && Files.isRegularFile(dir.resolve("catalog").resolve("uds_system_catalog.xlsx"));
  }

  private static Path detectRootFromCodeSource() {
    try {
      URL loc = UdsGuiApplication.class.getProtectionDomain().getCodeSource().getLocation();
      if (loc == null) {
        return null;
      }
      Path codePath = Path.of(loc.toURI()).toAbsolutePath().normalize();
      Path start = Files.isRegularFile(codePath) ? codePath.getParent() : codePath;
      Path walk = start;
      for (int i = 0; i < 8 && walk != null; i++) {
        if (looksLikeAppRoot(walk)) {
          return walk;
        }
        walk = walk.getParent();
      }
      return start;
    } catch (URISyntaxException | SecurityException e) {
      return null;
    }
  }
}
