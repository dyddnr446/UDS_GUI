package uds.gui.app;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import uds.gui.interpreter.InterpreterController;
import uds.gui.shell.MainViewModel;
import uds.read.finding.ReadDataResult;
import uds.write.parse.WriteResult;

/** JavaFX entry — Access/Interpreter/Read/Write/Analysis/AI Advisor. UX-01..06. */
public final class UdsGuiApplication extends Application {

  private MainViewModel main;
  private TabPane tabs;

  @Override
  public void start(Stage stage) throws Exception {
    DotEnv.loadNear(Path.of(System.getProperty("user.dir", ".")));
    Path root = resolveAppRoot();
    DotEnv.load(root);
    AppSession session =
        AppSession.open(
            root.resolve("docs").resolve("uds_catalog.xlsx"),
            root.resolve("samples"),
            root.resolve("config").resolve("demo_password.txt"),
            root.resolve("outputs"),
            "gui-session");
    main = new MainViewModel(session, root.resolve("samples"));

    tabs = new TabPane();
    tabs.getTabs()
        .addAll(
            accessTab(),
            interpreterTab(root.resolve("samples").resolve("demo_session.log")),
            readTab(),
            writeTab(),
            analysisTab(),
            aiTab());

    main.requestedTabIndexProperty()
        .addListener(
            (obs, o, idx) -> {
              int i = idx.intValue();
              if (i >= 0 && i < tabs.getTabs().size()) {
                tabs.getSelectionModel().select(i);
              }
            });

    VBox top = new VBox(6, statusBar(), demoStepper(), demoPresets());
    BorderPane rootPane = new BorderPane(tabs);
    rootPane.setTop(top);
    rootPane.setPadding(new Insets(8));
    stage.setTitle("UDS Diagnostic GUI");
    stage.setScene(new Scene(rootPane, 1024, 720));
    stage.show();
  }

  private HBox statusBar() {
    Label profile = new Label();
    profile.textProperty().bind(main.statusProfileProperty());
    Label session = new Label();
    session.textProperty().bind(main.statusSessionProperty());
    Label security = new Label();
    security.textProperty().bind(main.statusSecurityProperty());
    Label redaction = new Label();
    redaction.textProperty().bind(main.statusRedactionProperty());
    for (Label chip : new Label[] {profile, session, security, redaction}) {
      chip.setStyle(
          "-fx-background-color: #e8eef5; -fx-padding: 4 8; -fx-background-radius: 4;");
    }
    HBox bar = new HBox(8, profile, session, security, redaction);
    bar.setPadding(new Insets(4, 8, 4, 8));
    return bar;
  }

  private HBox demoStepper() {
    Button s1 = stepButton("Step 1: 로그 해석 (UC-01)", 1, main.step1DoneProperty());
    Button s2 = stepButton("Step 2: 접근 해제 (UC-06)", 2, main.step2DoneProperty());
    Button s3 = stepButton("Step 3: 급정지·AI (UC-02)", 3, main.step3DoneProperty());
    Button s4 = stepButton("Step 4: 데이터 쓰기 (UC-05)", 4, main.step4DoneProperty());
    Button s5 = stepButton("Step 5: 세션 저장·복원 (UC-03)", 5, main.step5DoneProperty());
    HBox box = new HBox(6, s1, s2, s3, s4, s5);
    box.setPadding(new Insets(0, 8, 4, 8));
    return box;
  }

  private Button stepButton(
      String title, int step, javafx.beans.property.BooleanProperty done) {
    Button b = new Button(title);
    Runnable refresh =
        () -> b.setText(done.get() ? ("✓ " + title) : title);
    done.addListener((o, a, v) -> refresh.run());
    refresh.run();
    b.setOnAction(e -> main.goDemoStep(step));
    return b;
  }

  private HBox demoPresets() {
    Button a = new Button("Preset A 급정지");
    a.setOnAction(e -> main.applyDemoPresetA());
    Button b = new Button("Preset B Locked");
    b.setOnAction(e -> main.applyDemoPresetB());
    Button c = new Button("Preset C Write");
    c.setOnAction(e -> main.applyDemoPresetC());
    HBox box = new HBox(6, new Label("Demo Presets:"), a, b, c);
    box.setPadding(new Insets(0, 8, 8, 8));
    return box;
  }

  private Tab accessTab() {
    ComboBox<String> profiles =
        new ComboBox<>(FXCollections.observableArrayList(main.access().profileIds()));
    ComboBox<String> flows = new ComboBox<>();
    PasswordField password = new PasswordField();
    Label state = new Label("state: DEFAULT_LOCKED");
    Label msg = new Label("");

    profiles.setOnAction(
        e -> {
          String p = profiles.getValue();
          if (p == null) {
            return;
          }
          main.selectProfile(p);
          flows.setItems(FXCollections.observableArrayList(main.access().flowIds()));
          if (!main.access().flowIds().isEmpty()) {
            flows.getSelectionModel().select(0);
            main.access().selectFlow(flows.getValue());
          }
          state.setText("state: " + main.access().accessState());
        });
    flows.setOnAction(
        e -> {
          if (flows.getValue() != null) {
            main.access().selectFlow(flows.getValue());
          }
        });

    Button extended = new Button("Enter Extended");
    extended.setOnAction(
        e -> {
          main.enterExtended();
          state.setText("state: " + main.access().accessState());
          msg.setText(main.access().statusMessage());
        });
    Button unlock = new Button("Unlock");
    unlock.setOnAction(
        e -> {
          main.access().setPassword(password.getText());
          main.unlock();
          password.clear();
          state.setText("state: " + main.access().accessState());
          msg.setText(main.access().statusMessage());
        });

    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(12));
    grid.add(new Label("Profile"), 0, 0);
    grid.add(profiles, 1, 0);
    grid.add(new Label("Access flow"), 0, 1);
    grid.add(flows, 1, 1);
    grid.add(new Label("Password"), 0, 2);
    grid.add(password, 1, 2);
    grid.add(new HBox(8, extended, unlock), 1, 3);
    grid.add(state, 1, 4);
    grid.add(msg, 1, 5);
    return new Tab("Access", grid);
  }

  private Tab interpreterTab(Path demoLog) {
    TextArea area = new TextArea();
    area.setEditable(false);
    Label summary = new Label();
    summary.textProperty().bind(main.interpreter().summaryChipProperty());

    Runnable paint =
        () -> {
          StringBuilder sb = new StringBuilder();
          for (String line : main.interpreter().filteredDisplayLines()) {
            sb.append(line).append('\n');
          }
          area.setText(sb.toString());
        };

    Button load = new Button("Load demo_session.log");
    load.setOnAction(
        e -> {
          main.interpreter().loadLog(demoLog);
          main.markStep1LogLoaded();
          paint.run();
        });
    Button openFile = new Button("Open log…");
    openFile.setOnAction(
        e -> {
          FileChooser chooser = new FileChooser();
          chooser.setTitle("Open UDS log");
          java.io.File f = chooser.showOpenDialog(tabs.getScene().getWindow());
          if (f != null) {
            main.interpreter().loadLog(f.toPath());
            main.markStep1LogLoaded();
            paint.run();
          }
        });

    ToggleGroup filterGroup = new ToggleGroup();
    ToggleButton all = new ToggleButton("전체 보기");
    ToggleButton errors = new ToggleButton("오류 줄만");
    ToggleButton redacted = new ToggleButton("0x27 REDACTED만");
    all.setToggleGroup(filterGroup);
    errors.setToggleGroup(filterGroup);
    redacted.setToggleGroup(filterGroup);
    all.setSelected(true);
    all.setOnAction(
        e -> {
          main.interpreter().setFilterMode(InterpreterController.FilterMode.ALL);
          paint.run();
        });
    errors.setOnAction(
        e -> {
          main.interpreter().setFilterMode(InterpreterController.FilterMode.ERRORS_ONLY);
          paint.run();
        });
    redacted.setOnAction(
        e -> {
          main.interpreter().setFilterMode(InterpreterController.FilterMode.REDACTED_ONLY);
          paint.run();
        });

    VBox box =
        new VBox(
            8,
            new HBox(8, load, openFile),
            summary,
            new HBox(8, all, errors, redacted),
            area);
    box.setPadding(new Insets(12));
    VBox.setVgrow(area, Priority.ALWAYS);
    return new Tab("Interpreter", box);
  }

  private Tab readTab() {
    ComboBox<String> dids = new ComboBox<>(main.read().runtimeDidIds());
    ComboBox<String> scenarios = new ComboBox<>(main.read().scenarioIds());
    Label result = new Label("");
    Label deniedMsg = new Label();
    deniedMsg.textProperty().bind(main.read().accessDeniedMessageProperty());
    deniedMsg.visibleProperty().bind(main.read().accessDeniedProperty());
    deniedMsg.managedProperty().bind(main.read().accessDeniedProperty());
    Button goAccess = new Button("Access 탭으로 이동하여 Unlock");
    goAccess.visibleProperty().bind(main.read().accessDeniedProperty());
    goAccess.managedProperty().bind(main.read().accessDeniedProperty());
    goAccess.setOnAction(e -> main.requestAccessTab());
    syncComboSelection(dids, scenarios, main.read()::selectedDid, main.read()::selectedScenarioId);

    Button read = new Button("Read");
    read.setOnAction(
        e -> {
          if (dids.getValue() != null) {
            main.read().selectDid(dids.getValue());
          }
          if (scenarios.getValue() != null) {
            main.read().selectScenario(scenarios.getValue());
          }
          ReadDataResult r = main.read().readSelected();
          result.setText(r.displayValue() + " / " + r.status());
        });

    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(12));
    grid.add(new Label("DID (auto from profile)"), 0, 0);
    grid.add(dids, 1, 0);
    grid.add(new Label("Scenario"), 0, 1);
    grid.add(scenarios, 1, 1);
    grid.add(read, 1, 2);
    grid.add(result, 1, 3);
    grid.add(deniedMsg, 1, 4);
    grid.add(goAccess, 1, 5);
    return new Tab("ReadData", grid);
  }

  private Tab writeTab() {
    ComboBox<String> dids = new ComboBox<>(main.write().runtimeDidIds());
    ComboBox<String> scenarios = new ComboBox<>(main.write().scenarioIds());
    TextField value = new TextField();
    Label result = new Label("");
    Label lastAccepted = new Label("lastAccepted: (none)");
    Label deniedMsg = new Label();
    deniedMsg.textProperty().bind(main.write().accessDeniedMessageProperty());
    deniedMsg.visibleProperty().bind(main.write().accessDeniedProperty());
    deniedMsg.managedProperty().bind(main.write().accessDeniedProperty());
    Button goAccess = new Button("Access 탭으로 이동하여 Unlock");
    goAccess.visibleProperty().bind(main.write().accessDeniedProperty());
    goAccess.managedProperty().bind(main.write().accessDeniedProperty());
    goAccess.setOnAction(e -> main.requestAccessTab());
    syncComboSelection(
        dids, scenarios, main.write()::selectedDid, main.write()::selectedScenarioId);

    Button write = new Button("Write");
    write.setOnAction(
        e -> {
          if (dids.getValue() != null) {
            main.write().selectDid(dids.getValue());
          }
          if (scenarios.getValue() != null) {
            main.write().selectScenario(scenarios.getValue());
          }
          main.write().setInputValue(value.getText());
          WriteResult r = main.write().writeSelected();
          result.setText(r.status() + " req=" + r.requestHex());
          if (main.write().lastAccepted() != null) {
            WriteResult a = main.write().lastAccepted();
            lastAccepted.setText(
                "lastAccepted: " + a.did() + " " + a.inputValue() + " / " + a.status());
            main.markStep4WriteDone();
          }
        });

    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(12));
    grid.add(new Label("DID (auto from profile)"), 0, 0);
    grid.add(dids, 1, 0);
    grid.add(new Label("Scenario"), 0, 1);
    grid.add(scenarios, 1, 1);
    grid.add(new Label("Value"), 0, 2);
    grid.add(value, 1, 2);
    grid.add(write, 1, 3);
    grid.add(result, 1, 4);
    grid.add(lastAccepted, 1, 5);
    grid.add(deniedMsg, 1, 6);
    grid.add(goAccess, 1, 7);
    return new Tab("WriteData", grid);
  }

  private static void syncComboSelection(
      ComboBox<String> dids,
      ComboBox<String> scenarios,
      java.util.function.Supplier<String> selectedDid,
      java.util.function.Supplier<String> selectedScenario) {
    javafx.collections.ListChangeListener<String> sync =
        c -> {
          String did = selectedDid.get();
          if (did != null && dids.getItems().contains(did)) {
            dids.getSelectionModel().select(did);
          } else {
            dids.getSelectionModel().clearSelection();
          }
          String sc = selectedScenario.get();
          if (sc != null && scenarios.getItems().contains(sc)) {
            scenarios.getSelectionModel().select(sc);
          } else {
            scenarios.getSelectionModel().clearSelection();
          }
        };
    dids.getItems().addListener(sync);
    scenarios.getItems().addListener(sync);
  }

  private Tab analysisTab() {
    Label msg = new Label("");
    Label summary = new Label();
    summary.textProperty().bind(main.analysis().restoreSummaryProperty());
    Label banner = new Label();
    banner.textProperty().bind(main.analysis().securityBannerTextProperty());
    banner.visibleProperty().bind(main.analysis().securityBannerVisibleProperty());
    banner.managedProperty().bind(main.analysis().securityBannerVisibleProperty());
    banner.setWrapText(true);
    banner.setStyle("-fx-text-fill: #a40; -fx-font-weight: bold;");

    Button save = new Button("Save session");
    save.setOnAction(
        e -> {
          main.analysis().bindProfile(main.access().selectedProfileId());
          main.analysis().save();
          main.markStep5SessionDone();
          msg.setText(String.join(" | ", main.analysis().messages()));
          main.refreshDiagnosticStatus();
        });
    Button restore = new Button("Restore this session dir");
    restore.setOnAction(
        e -> {
          main.analysis().restoreFrom(main.session().sessionDir());
          main.markStep5SessionDone();
          main.refreshDiagnosticStatus();
          msg.setText(
              "state="
                  + main.analysis().accessState()
                  + " | "
                  + String.join(" | ", main.analysis().messages()));
        });
    VBox box = new VBox(8, save, restore, summary, banner, msg);
    box.setPadding(new Insets(12));
    return new Tab("Analysis Session", box);
  }

  private Tab aiTab() {
    TextField symptom = new TextField();
    symptom.setPromptText("현상 (예: 급정지)");
    TextArea factZone = new TextArea();
    factZone.setEditable(false);
    factZone.setPromptText("사실 구역");
    ListView<String> factList = new ListView<>();
    factList.setPrefHeight(140);
    TextArea aiZone = new TextArea();
    aiZone.setEditable(false);
    aiZone.setPromptText("[AI 설명]");
    Label status = new Label("");
    FlowPane evidenceChips = new FlowPane(6, 6);

    Runnable refreshZones =
        () -> {
          factZone.setText(main.ai().factZone());
          factList.getItems().setAll(main.ai().factEvidenceLines());
          aiZone.setText(main.ai().aiZone());
          evidenceChips.getChildren().clear();
          for (String id : main.ai().displayedEvidenceIds()) {
            Button chip = new Button("#" + id);
            chip.setOnAction(
                ev -> {
                  main.ai().selectEvidence(id);
                  for (int i = 0; i < factList.getItems().size(); i++) {
                    if (factList.getItems().get(i).startsWith(id)) {
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
            for (int i = 0; i < factList.getItems().size(); i++) {
              if (factList.getItems().get(i).startsWith(highlight)) {
                factList.getSelectionModel().select(i);
                factList.scrollTo(i);
                break;
              }
            }
          }
          if (!main.ai().findings().isEmpty() || !main.ai().aiZone().isBlank()) {
            main.markStep3AiOrFinding();
          }
        };

    Button suggest = new Button("Suggest DIDs");
    suggest.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().onProfileChanged(main.access().selectedProfileId());
          main.ai().refreshSuggestions();
          refreshZones.run();
        });
    Button readSuggested = new Button("Read suggested DIDs");
    readSuggested.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().refreshSuggestions();
          main.ai().runSuggestedReads();
          refreshZones.run();
        });
    Button oneClick = new Button("원클릭 진단");
    oneClick.disableProperty().bind(main.ai().runningProperty());
    oneClick.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().runOneClickDiagnosis();
          refreshZones.run();
        });
    Button ask = new Button("Ask AI");
    ask.disableProperty().bind(main.ai().runningProperty());
    ask.setOnAction(
        e -> {
          main.ai().setSymptomText(symptom.getText());
          main.ai().requestAdvice();
        });
    Button cancel = new Button("Cancel AI");
    cancel.setOnAction(e -> main.ai().cancel());

    ProgressIndicator progress = new ProgressIndicator();
    progress.setMaxSize(24, 24);
    progress.visibleProperty().bind(main.ai().runningProperty());
    progress.managedProperty().bind(main.ai().runningProperty());
    status.textProperty().bind(main.ai().statusMessageProperty());
    Label errorBanner = new Label();
    errorBanner.textProperty().bind(main.ai().errorBannerProperty());
    errorBanner.setWrapText(true);
    main.ai().runningProperty().addListener((obs, was, is) -> {
      if (!is) {
        refreshZones.run();
      }
    });
    main.ai().statusMessageProperty().addListener((obs, o, n) -> refreshZones.run());

    VBox box =
        new VBox(
            8,
            new Label("현상"),
            symptom,
            new HBox(8, suggest, readSuggested, oneClick, ask, cancel, progress),
            status,
            errorBanner,
            new Label("Evidence Chips"),
            evidenceChips,
            new Label("사실 구역 (목록)"),
            factList,
            new Label("사실 구역 (요약)"),
            factZone,
            new Label("[AI 설명]"),
            aiZone);
    box.setPadding(new Insets(12));
    return new Tab("AI Advisor", box);
  }

  public static void main(String[] args) {
    DotEnv.loadNear(Path.of(System.getProperty("user.dir", ".")));
    launch(args);
  }

  /**
   * Project / install root that contains {@code docs/uds_catalog.xlsx}.
   * Order: {@code UDS_APP_HOME} (OS or .env) → class/jar location walk-up → {@code user.dir}.
   */
  static Path resolveAppRoot() {
    String env = DotEnv.get("UDS_APP_HOME");
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
    // Last resort: walk parents of cwd (mvn javafx:run from subdir)
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
        && Files.isRegularFile(dir.resolve("docs").resolve("uds_catalog.xlsx"));
  }

  private static Path detectRootFromCodeSource() {
    try {
      URL loc = UdsGuiApplication.class.getProtectionDomain().getCodeSource().getLocation();
      if (loc == null) {
        return null;
      }
      Path codePath = Path.of(loc.toURI()).toAbsolutePath().normalize();
      // classes dir, jar file, or jpackage app/ dir
      Path start = Files.isRegularFile(codePath) ? codePath.getParent() : codePath;
      Path walk = start;
      for (int i = 0; i < 8 && walk != null; i++) {
        if (looksLikeAppRoot(walk)) {
          return walk;
        }
        // jpackage: .../UdsDiagnosticGui/app/*.jar → parent folder is install root
        walk = walk.getParent();
      }
      return start;
    } catch (URISyntaxException | SecurityException e) {
      return null;
    }
  }
}
