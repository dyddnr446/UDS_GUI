package uds.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import uds.interpreter.SecurityRevealMode;

/**
 * Classroom Learning toggle bound to {@link SecurityRevealMode}. AI sanitization always redacts
 * regardless (FR-31).
 */
public final class LearningMode {

  private static final BooleanProperty PROPERTY = new SimpleBooleanProperty(false);

  static {
    PROPERTY.addListener(
        (obs, o, n) -> SecurityRevealMode.setEnabled(Boolean.TRUE.equals(n)));
  }

  private LearningMode() {}

  public static boolean isEnabled() {
    return SecurityRevealMode.isEnabled();
  }

  public static void setEnabled(boolean enabled) {
    PROPERTY.set(enabled);
    SecurityRevealMode.setEnabled(enabled);
  }

  public static BooleanProperty enabledProperty() {
    return PROPERTY;
  }
}
