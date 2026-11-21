package uds.interpreter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * When enabled, local decode/live CSV may show 0x27 seed/key HEX. AI path must still redact
 * independently.
 */
public final class SecurityRevealMode {

  private static final AtomicBoolean ENABLED = new AtomicBoolean(false);

  private SecurityRevealMode() {}

  public static boolean isEnabled() {
    return ENABLED.get();
  }

  public static void setEnabled(boolean enabled) {
    ENABLED.set(enabled);
  }
}
