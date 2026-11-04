package uds.gui.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads a local {@code .env} into {@code System} properties ({@code uds.env.*}).
 * Does not override OS environment variables. Never logs secret values.
 */
public final class DotEnv {

  public static final String PROPERTY_PREFIX = "uds.env.";

  private DotEnv() {}

  /** Property key used after load, e.g. {@code uds.env.UDS_AI_API_KEY}. */
  public static String propertyName(String envKey) {
    return PROPERTY_PREFIX + Objects.requireNonNull(envKey, "envKey");
  }

  /**
   * Resolve config: OS {@link System#getenv(String)} first, then {@code uds.env.*} property.
   */
  public static String get(String envKey) {
    String fromOs = System.getenv(envKey);
    if (fromOs != null && !fromOs.isBlank()) {
      return fromOs.trim();
    }
    String fromProp = System.getProperty(propertyName(envKey));
    if (fromProp != null && !fromProp.isBlank()) {
      return fromProp.trim();
    }
    return null;
  }

  public static String getOrDefault(String envKey, String defaultValue) {
    String v = get(envKey);
    return v == null || v.isBlank() ? defaultValue : v;
  }

  /** Load {@code dir/.env} if present. Returns number of keys applied. */
  public static int load(Path dir) {
    if (dir == null) {
      return 0;
    }
    Path file = dir.toAbsolutePath().normalize().resolve(".env");
    if (!Files.isRegularFile(file)) {
      return 0;
    }
    Map<String, String> parsed = parseFile(file);
    int applied = 0;
    for (Map.Entry<String, String> e : parsed.entrySet()) {
      if (apply(e.getKey(), e.getValue())) {
        applied++;
      }
    }
    return applied;
  }

  /** Walk {@code start} and parents (up to 8) looking for {@code .env}. */
  public static int loadNear(Path start) {
    Path walk = start == null ? Path.of(".").toAbsolutePath().normalize() : start.toAbsolutePath().normalize();
    for (int i = 0; i < 8 && walk != null; i++) {
      if (Files.isRegularFile(walk.resolve(".env"))) {
        return load(walk);
      }
      walk = walk.getParent();
    }
    return 0;
  }

  static boolean apply(String key, String value) {
    if (key == null || key.isBlank()) {
      return false;
    }
    String k = key.trim();
    String v = value == null ? "" : value.trim();
    if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
      v = v.substring(1, v.length() - 1);
    }
    String os = System.getenv(k);
    if (os != null && !os.isBlank()) {
      return false; // OS wins
    }
    String prop = propertyName(k);
    if (System.getProperty(prop) != null) {
      return false; // already set this JVM
    }
    System.setProperty(prop, v);
    return true;
  }

  static Map<String, String> parseFile(Path file) {
    Map<String, String> out = new LinkedHashMap<>();
    try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        if (line.startsWith("export ")) {
          line = line.substring("export ".length()).trim();
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = line.substring(0, eq).trim();
        String val = line.substring(eq + 1).trim();
        out.put(key, val);
      }
    } catch (IOException ignored) {
      return Map.of();
    }
    return out;
  }
}
