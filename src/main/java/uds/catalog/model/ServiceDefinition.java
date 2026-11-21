package uds.catalog.model;

import java.util.Locale;
import java.util.OptionalInt;

/** Runtime service identity / structure (system catalog). */
public record ServiceDefinition(
    int sid,
    String name,
    OptionalInt positiveResponseSid,
    String category,
    boolean hasSubfunction,
    int subByteOffset) {

  public ServiceDefinition {
    name = nullToEmpty(name);
    positiveResponseSid = positiveResponseSid == null ? OptionalInt.empty() : positiveResponseSid;
    category = nullToEmpty(category);
  }

  public String sidHex() {
    return String.format(Locale.ROOT, "0x%02X", sid & 0xFF);
  }

  public String positiveResponseSidHex() {
    return positiveResponseSid.isPresent()
        ? String.format(Locale.ROOT, "0x%02X", positiveResponseSid.getAsInt() & 0xFF)
        : "";
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
