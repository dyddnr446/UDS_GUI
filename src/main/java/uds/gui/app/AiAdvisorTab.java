package uds.gui.app;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import uds.gui.MainViewModel;

final class AiAdvisorTab {

  private AiAdvisorTab() {}

  static Tab create(MainViewModel main) {
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
}
