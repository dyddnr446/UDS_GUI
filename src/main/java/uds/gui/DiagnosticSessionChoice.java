package uds.gui;

import java.util.Locale;
import java.util.Objects;
import uds.catalog.model.ControllerProfile;
import uds.catalog.model.SubFunctionDefinition;

/** One DiagnosticSessionControl (0x10) option for the Session Control ComboBox. */
public final class DiagnosticSessionChoice {

  private final String key;
  private final String displayName;
  private final String subFunctionHex;
  private final String catalogName;

  public DiagnosticSessionChoice(
      String key, String displayName, String subFunctionHex, String catalogName) {
    this.key = Objects.requireNonNull(key, "key");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.subFunctionHex = Objects.requireNonNull(subFunctionHex, "subFunctionHex");
    this.catalogName = catalogName == null ? "" : catalogName;
  }

  public static DiagnosticSessionChoice fromSubFunction(SubFunctionDefinition sub) {
    String name = sub.name();
    String key = ControllerProfile.aliasKey(name.isBlank() ? sub.subFunction() : name);
    if (key.isBlank()) {
      key = sub.subFunction().toUpperCase(Locale.ROOT);
    }
    return new DiagnosticSessionChoice(key, humanize(name, key), sub.subFunction(), name);
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public String subFunctionHex() {
    return subFunctionHex;
  }

  public String catalogName() {
    return catalogName;
  }

  public boolean isDefault() {
    return "DEFAULT".equals(key);
  }

  public boolean isExtended() {
    return "EXTENDED".equals(key);
  }

  private static String humanize(String catalogName, String key) {
    return switch (key) {
      case "DEFAULT" -> "Default Session";
      case "EXTENDED" -> "Extended Session";
      case "PROGRAMMING" -> "Programming Session";
      case "SAFETY" -> "Safety Session";
      default -> {
        if (catalogName != null && !catalogName.isBlank()) {
          String spaced =
              catalogName
                  .replaceAll("([a-z])([A-Z])", "$1 $2")
                  .replace('_', ' ')
                  .trim();
          if (!spaced.isEmpty()) {
            yield Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
          }
        }
        yield key;
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DiagnosticSessionChoice other)) {
      return false;
    }
    return key.equals(other.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    return displayName;
  }
}
