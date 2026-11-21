package uds.gui.app;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Modal popups for operator feedback — keeps the Control Bar layout stable. */
public final class UiDialogs {

  private UiDialogs() {}

  public static void info(Window owner, String title, String message) {
    show(owner, Alert.AlertType.INFORMATION, title, message);
  }

  public static void warn(Window owner, String title, String message) {
    show(owner, Alert.AlertType.WARNING, title, message);
  }

  public static void error(Window owner, String title, String message) {
    show(owner, Alert.AlertType.ERROR, title, message);
  }

  /**
   * Password prompt for Security Unlock. Empty input is allowed (caller may fall back to demo
   * password from env).
   */
  public static Optional<String> promptPassword(
      Window owner, String title, String header, String prompt) {
    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle(title == null || title.isBlank() ? "Unlock" : title);
    dialog.setHeaderText(header);
    if (owner != null) {
      dialog.initOwner(owner);
    }

    ButtonType unlockType = new ButtonType("Unlock", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(unlockType, ButtonType.CANCEL);

    PasswordField field = new PasswordField();
    field.setPromptText(prompt == null ? "" : prompt);
    field.setPrefWidth(320);
    Label hint = new Label("최초 Extended 접속 시 한 번만 묻습니다. 비우면 .env 데모 비밀번호.");
    hint.getStyleClass().add("control-label");
    VBox box = new VBox(8, field, hint);
    box.setPadding(new Insets(8, 4, 0, 4));
    dialog.getDialogPane().setContent(box);
    dialog.getDialogPane().setPrefWidth(420);

    dialog.setResultConverter(
        button -> {
          if (button == unlockType) {
            return field.getText() == null ? "" : field.getText();
          }
          return null;
        });

    dialog.setOnShown(e -> field.requestFocus());
    return dialog.showAndWait();
  }

  private static void show(Window owner, Alert.AlertType type, String title, String message) {
    Alert alert = new Alert(type, message == null ? "" : message, ButtonType.OK);
    alert.setTitle(title == null || title.isBlank() ? "UDS Diagnostic" : title);
    alert.setHeaderText(null);
    alert.setResizable(true);
    if (owner != null) {
      alert.initOwner(owner);
    }
    alert.getDialogPane().setPrefWidth(420);
    alert.showAndWait();
  }
}
