package uds.gui.app;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

final class TabControls {

  private TabControls() {}

  static GridPane kvGrid() {
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

  static void addKvRow(GridPane grid, int row, String key, javafx.scene.Node value) {
    Label keyLabel = new Label(key);
    keyLabel.getStyleClass().add("kv-key");
    grid.add(keyLabel, 0, row);
    grid.add(value, 1, row);
  }

  static String blankToDash(String value) {
    return value == null || value.isBlank() ? "(none)" : value;
  }

  static Label pipelineBadge(String stage, String state) {
    Label badge = new Label(stage + "\n" + state);
    badge.getStyleClass().addAll("pipeline-badge", "pipeline-badge-idle");
    badge.setWrapText(true);
    badge.setMinWidth(140);
    badge.setMaxWidth(160);
    return badge;
  }

  static void applyPipelineBadge(Label badge, String stage, String state, String style) {
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

  static void syncDidSelection(
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
}
