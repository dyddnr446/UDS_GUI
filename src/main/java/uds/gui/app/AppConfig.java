package uds.gui.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import uds.catalog.load.CatalogLoader;

/**
 * Runtime settings from OS env / {@code .env} (paths, demo password). AI keys stay on {@link
 * DotEnv#get(String)}.
 */
public final class AppConfig {

  public static final String KEY_CATALOG_DIR = "UDS_CATALOG_DIR";
  public static final String KEY_DID_DTC_CATALOG_PATH = "UDS_DID_DTC_CATALOG_PATH";
  public static final String KEY_SID_CATALOG_PATH = "UDS_SID_CATALOG_PATH";
  /** @deprecated Prefer {@link #KEY_DID_DTC_CATALOG_PATH}; still honored as DID/DTC override. */
  public static final String KEY_CATALOG_PATH = "UDS_CATALOG_PATH";
  public static final String KEY_FIXTURE_PATH = "UDS_FIXTURE_PATH";
  public static final String KEY_DEMO_PASSWORD = "UDS_DEMO_PASSWORD";
  public static final String KEY_SAMPLES_DIR = "UDS_SAMPLES_DIR";
  public static final String KEY_APP_HOME = "UDS_APP_HOME";
  /** {@code TRUE} → offline log/fixture test mode; {@code FALSE} → live CAN (future). */
  public static final String KEY_TEST = "TEST";

  public static final String DEFAULT_CATALOG_DIR_REL = "catalog";
  public static final String DEFAULT_DID_DTC_CATALOG_REL =
      DEFAULT_CATALOG_DIR_REL + "/" + CatalogLoader.DEFAULT_DID_DTC_FILE;
  public static final String DEFAULT_SID_CATALOG_REL =
      DEFAULT_CATALOG_DIR_REL + "/" + CatalogLoader.DEFAULT_SID_FILE;
  public static final String DEFAULT_FIXTURE_REL = "fixtures/uds_fixture_scenarios.xlsx";
  /** Directory that holds demo_session.log (fixtures/). */
  public static final String DEFAULT_SAMPLES_REL = "fixtures";
  public static final String DEFAULT_DEMO_PASSWORD = "uds-demo-2026";

  private final Path appHome;
  private final Path catalogDir;
  private final Path didDtcCatalogPath;
  private final Path sidCatalogPath;
  private final Path fixturePath;
  private final Path samplesDir;
  private final String demoPassword;
  private final boolean testMode;

  public AppConfig(
      Path appHome,
      Path catalogDir,
      Path didDtcCatalogPath,
      Path sidCatalogPath,
      Path fixturePath,
      Path samplesDir,
      String demoPassword,
      boolean testMode) {
    this.appHome = Objects.requireNonNull(appHome, "appHome").toAbsolutePath().normalize();
    this.catalogDir =
        Objects.requireNonNull(catalogDir, "catalogDir").toAbsolutePath().normalize();
    this.didDtcCatalogPath =
        Objects.requireNonNull(didDtcCatalogPath, "didDtcCatalogPath").toAbsolutePath().normalize();
    this.sidCatalogPath =
        Objects.requireNonNull(sidCatalogPath, "sidCatalogPath").toAbsolutePath().normalize();
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
    Path catalogDir = resolvePath(home, DotEnv.getOrDefault(KEY_CATALOG_DIR, DEFAULT_CATALOG_DIR_REL));
    String didOverride = firstNonBlank(DotEnv.get(KEY_DID_DTC_CATALOG_PATH), DotEnv.get(KEY_CATALOG_PATH));
    Path didDtc =
        didOverride != null
            ? resolvePath(home, didOverride)
            : catalogDir.resolve(CatalogLoader.DEFAULT_DID_DTC_FILE);
    String sidOverride = DotEnv.get(KEY_SID_CATALOG_PATH);
    Path sid =
        sidOverride != null && !sidOverride.isBlank()
            ? resolvePath(home, sidOverride)
            : catalogDir.resolve(CatalogLoader.DEFAULT_SID_FILE);
    Path fixture = resolvePath(home, DotEnv.getOrDefault(KEY_FIXTURE_PATH, DEFAULT_FIXTURE_REL));
    Path samples = resolvePath(home, DotEnv.getOrDefault(KEY_SAMPLES_DIR, DEFAULT_SAMPLES_REL));
    String password = DotEnv.getOrDefault(KEY_DEMO_PASSWORD, DEFAULT_DEMO_PASSWORD);
    boolean test = parseTruthy(DotEnv.getOrDefault(KEY_TEST, "TRUE"));
    return new AppConfig(home, catalogDir, didDtc, sid, fixture, samples, password, test);
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    return null;
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

  public Path catalogDir() {
    return catalogDir;
  }

  public Path didDtcCatalogPath() {
    return didDtcCatalogPath;
  }

  public Path sidCatalogPath() {
    return sidCatalogPath;
  }

  /** DID/DTC master path (legacy alias). */
  public Path catalogPath() {
    return didDtcCatalogPath;
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
    if (!Files.isRegularFile(didDtcCatalogPath)) {
      throw new IllegalStateException(
          "DID/DTC catalog not found: "
              + didDtcCatalogPath
              + " (set "
              + KEY_DID_DTC_CATALOG_PATH
              + " or "
              + KEY_CATALOG_DIR
              + ")");
    }
    if (!Files.isRegularFile(sidCatalogPath)) {
      throw new IllegalStateException(
          "SID catalog not found: "
              + sidCatalogPath
              + " (set "
              + KEY_SID_CATALOG_PATH
              + " or "
              + KEY_CATALOG_DIR
              + ")");
    }
  }

  public void requireFixtureExists() {
    if (!Files.isRegularFile(fixturePath)) {
      throw new IllegalStateException(
          "fixture not found: " + fixturePath + " (set " + KEY_FIXTURE_PATH + ")");
    }
  }
}
