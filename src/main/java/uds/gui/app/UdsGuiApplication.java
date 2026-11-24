package uds.gui.app;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import uds.access.AccessOutcome;
import uds.access.DiagnosticAccessState;
import uds.ai.ExternalAiClient;
import uds.gui.DiagnosticSessionChoice;
import uds.gui.MainViewModel;

/** JavaFX entry — diagnostic shell (TEST offline / future CAN) with Control Bar + gated tabs. */
public final class UdsGuiApplication extends Application {

  private MainViewModel main;
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

    Tab interpreter = InterpreterTab.create(main, testMode, stage);
    Tab read = ReadDataTab.create(main);
    Tab write = WriteDataTab.create(main);
    Tab dtc = DtcTab.create(main, testMode);
    Tab ai = AiAdvisorTab.create(main);

    read.disableProperty().bind(main.readTabEnabledProperty().not());
    write.disableProperty().bind(main.writeTabEnabledProperty().not());
    dtc.disableProperty().bind(main.dtcTabEnabledProperty().not());
    ai.disableProperty().bind(main.aiTabEnabledProperty().not());

    TabPane tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    for (Tab t : List.of(interpreter, read, write, dtc, ai)) {
      t.setClosable(false);
    }
    // Analysis Session save/restore UI removed — in-memory store still used by AI/tests.
    tabs.getTabs().addAll(interpreter, read, write, dtc, ai);

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
   * Project / install root that contains DID/DTC + SID catalog masters under {@code catalog/}.
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
    if (dir == null) {
      return false;
    }
    Path catalog = dir.resolve("catalog");
    return Files.isRegularFile(catalog.resolve(uds.catalog.load.CatalogLoader.DEFAULT_DID_DTC_FILE))
        && Files.isRegularFile(catalog.resolve(uds.catalog.load.CatalogLoader.DEFAULT_SID_FILE));
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
