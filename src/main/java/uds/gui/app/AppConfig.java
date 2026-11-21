package uds.gui.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Runtime settings from OS env / {@code .env} (paths, demo password). AI keys stay on {@link
 * DotEnv#get(String)}.
 */
public final class AppConfig {

  public static final String KEY_CATALOG_PATH = "UDS_CATALOG_PATH";
  public static final String KEY_GUIDE_CATALOG_PATH = "UDS_GUIDE_CATALOG_PATH";
  public static final String KEY_FIXTURE_PATH = "UDS_FIXTURE_PATH";
  public static final String KEY_DEMO_PASSWORD = "UDS_DEMO_PASSWORD";
  public static final String KEY_SAMPLES_DIR = "UDS_SAMPLES_DIR";
  public static final String KEY_APP_HOME = "UDS_APP_HOME";
  /** {@code TRUE} → offline log/fixture test mode; {@code FALSE} → live CAN (future). */
  public static final String KEY_TEST = "TEST";

  public static final String DEFAULT_CATALOG_REL = "기준정보/uds_system_catalog.xlsx";
  public static final String DEFAULT_GUIDE_CATALOG_REL = "doc/guide/uds_guide_catalog.xlsx";
  public static final String DEFAULT_FIXTURE_REL = "test/fixtures/uds_fixture_scenarios.xlsx";
  /** Directory that holds demo_session.log (test/fixtures/). */
  public static final String DEFAULT_SAMPLES_REL = "test/fixtures";
  public static final String DEFAULT_DEMO_PASSWORD = "uds-demo-2026";

  private final Path appHome;
  private final Path catalogPath;
  private final Path guideCatalogPath;
  private final Path fixturePath;
  private final Path samplesDir;
  private final String demoPassword;
  private final boolean testMode;

  public AppConfig(
      Path appHome,
      Path catalogPath,
      Path guideCatalogPath,
      Path fixturePath,
      Path samplesDir,
      String demoPassword,
      boolean testMode) {
    this.appHome = Objects.requireNonNull(appHome, "appHome").toAbsolutePath().normalize();
    this.catalogPath =
        Objects.requireNonNull(catalogPath, "catalogPath").toAbsolutePath().normalize();
    this.guideCatalogPath =
        Objects.requireNonNull(guideCatalogPath, "guideCatalogPath").toAbsolutePath().normalize();
    this.fixturePath =
        Objects.requireNonNull(fixturePath, "fixturePath").toAbsolutePath().normalize();
    this.samplesDir = Objects.requireNonNull(samplesDir, "samplesDir").toAbsolutePath().normalize();
    this.demoPassword = Objects.requireNonNull(demoPassword, "demoPassword");
    this.testMode = testMode;
    if (this.demoPassword.isBlank()) {
      throw new IllegalArgumentException(KEY_DEMO_PASSWORD + " is blank");
    }
  }

  /** Load project-root {@code .env}, then resolve catalog/password. */
  public static AppConfig fromEnvironment(Path appHome) {
    Path home = Objects.requireNonNull(appHome, "appHome").toAbsolutePath().normalize();
    DotEnv.load(home);
    Path catalog = resolvePath(home, DotEnv.getOrDefault(KEY_CATALOG_PATH, DEFAULT_CATALOG_REL));
    Path guide =
        resolvePath(home, DotEnv.getOrDefault(KEY_GUIDE_CATALOG_PATH, DEFAULT_GUIDE_CATALOG_REL));
    Path fixture = resolvePath(home, DotEnv.getOrDefault(KEY_FIXTURE_PATH, DEFAULT_FIXTURE_REL));
    Path samples = resolvePath(home, DotEnv.getOrDefault(KEY_SAMPLES_DIR, DEFAULT_SAMPLES_REL));
    String password = DotEnv.getOrDefault(KEY_DEMO_PASSWORD, DEFAULT_DEMO_PASSWORD);
    boolean test = parseTruthy(DotEnv.getOrDefault(KEY_TEST, "TRUE"));
    return new AppConfig(home, catalog, guide, fixture, samples, password, test);
  }

  /** {@code TRUE}/{@code true}/{@code 1}/{@code yes} → true. */
  static boolean parseTruthy(String raw) {
    if (raw == null) {
      return false;
    }
    String v = raw.trim();
    return v.equalsIgnoreCase("TRUE")
        || v.equalsIgnoreCase("YES")
        || v.equals("1")
        || v.equalsIgnoreCase("ON");
  }

  public Path appHome() {
    return appHome;
  }

  public Path catalogPath() {
    return catalogPath;
  }

  public Path guideCatalogPath() {
    return guideCatalogPath;
  }

  public Path fixturePath() {
    return fixturePath;
  }

  public Path samplesDir() {
    return samplesDir;
  }

  public String demoPassword() {
    return demoPassword;
  }

  /**
   * When true: offline fixture ECU + Interpreter JSON log testing. When false: live CAN path
   * (see {@link uds.io.CanSocketExchangePort}).
   */
  public boolean testMode() {
    return testMode;
  }

  private static Path resolvePath(Path home, String configured) {
    Path p = Path.of(configured);
    if (!p.isAbsolute()) {
      p = home.resolve(p);
    }
    return p.toAbsolutePath().normalize();
  }

  public void requireCatalogExists() {
    if (!Files.isRegularFile(catalogPath)) {
      throw new IllegalStateException(
          "catalog not found: " + catalogPath + " (set " + KEY_CATALOG_PATH + ")");
    }
  }

  public void requireFixtureExists() {
    if (!Files.isRegularFile(fixturePath)) {
      throw new IllegalStateException(
          "fixture not found: " + fixturePath + " (set " + KEY_FIXTURE_PATH + ")");
    }
  }
}
