package uds.catalog.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Runtime controller profile (system catalog). */
public record ControllerProfile(
    String profileId,
    String displayName,
    String family,
    String didPolicy,
    List<String> supportedSessions,
    boolean runtimeEnabled,
    int sortOrder) {

  public ControllerProfile {
    profileId = nullToEmpty(profileId);
    displayName = nullToEmpty(displayName);
    family = nullToEmpty(family);
    didPolicy = nullToEmpty(didPolicy);
    supportedSessions = List.copyOf(normalizeSessions(supportedSessions));
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static List<String> normalizeSessions(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String s : raw) {
      if (s == null) {
        continue;
      }
      String t = s.trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out;
  }

  /** Parse catalog cell {@code defaultSession,extendedDiagnosticSession}. */
  public static List<String> parseSessionList(String cell) {
    if (cell == null || cell.isBlank()) {
      return List.of();
    }
    return Arrays.stream(cell.split("[,;|]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public boolean supportsSessionName(String sessionName) {
    if (sessionName == null || sessionName.isBlank() || supportedSessions.isEmpty()) {
      return false;
    }
    for (String s : supportedSessions) {
      if (s.equalsIgnoreCase(sessionName.trim())) {
        return true;
      }
      if (aliasKey(s).equals(aliasKey(sessionName))) {
        return true;
      }
    }
    return false;
  }

  /** Normalize SubFunction name / UI id to DEFAULT|EXTENDED|PROGRAMMING|SAFETY. */
  public static String aliasKey(String raw) {
    if (raw == null) {
      return "";
    }
    String v = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
    if (v.contains("DEFAULT") || v.equals("0X01") || v.equals("01")) {
      return "DEFAULT";
    }
    if (v.contains("EXTENDED") || v.equals("0X03") || v.equals("03")) {
      return "EXTENDED";
    }
    if (v.contains("PROGRAMMING") || v.equals("0X02") || v.equals("02")) {
      return "PROGRAMMING";
    }
    if (v.contains("SAFETY") || v.equals("0X04") || v.equals("04")) {
      return "SAFETY";
    }
    return v;
  }
}
