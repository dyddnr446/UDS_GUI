package uds.analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * User-facing CSV live log at {@code output/live_session.log} ({@code ts,direction,hex}).
 * {@code ts} is wall-clock {@code HH:mm:ss.SSS} so Interpreter rows are easy to order visually.
 * Never writes plaintext 0x27 seed/key (caller must pass masked hex).
 */
public final class LiveSessionLog {

  public static final String FILE_NAME = "live_session.log";

  private static final DateTimeFormatter WALL_TS =
      DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT);

  /** Legacy session-elapsed seconds written by older builds ({@code 104.128}). */
  private static final Pattern LEGACY_RELATIVE_TS = Pattern.compile("\\d+(?:\\.\\d+)?");

  /** Current wall-clock format. */
  private static final Pattern WALL_CLOCK_TS = Pattern.compile("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");

  private final Path logFile;
  private final Object lock = new Object();
  private final AtomicBoolean headerWritten = new AtomicBoolean(false);

  /** @param outputDir parent directory for {@link #FILE_NAME} (usually project {@code output/}). */
  public LiveSessionLog(Path outputDir) {
    Objects.requireNonNull(outputDir, "outputDir");
    this.logFile = outputDir.resolve(FILE_NAME);
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new IllegalStateException("cannot create output dir: " + outputDir, e);
    }
    synchronized (lock) {
      migrateLegacyRelativeLog();
      ensureHeader();
    }
  }

  public Path path() {
    return logFile;
  }

  /** Wipe CSV contents and rewrite header (Interpreter Clear log). */
  public void clear() {
    synchronized (lock) {
      try {
        Files.deleteIfExists(logFile);
      } catch (IOException e) {
        throw new IllegalStateException("live log clear failed: " + logFile, e);
      }
      headerWritten.set(false);
      ensureHeader();
    }
  }

  public void appendPair(String requestHex, String responseHex) {
    append("REQ", requestHex);
    append("RES", responseHex == null ? "" : responseHex);
  }

  public void append(String direction, String hex) {
    Objects.requireNonNull(direction, "direction");
    String h = hex == null ? "" : hex.trim();
    String dir = direction.trim().toUpperCase(Locale.ROOT);
    synchronized (lock) {
      ensureHeader();
      appendLine(nextTs() + "," + dir + "," + h);
    }
  }

  /**
   * Older builds stored elapsed seconds since process start. Drop that file once so the next
   * session starts clean with wall-clock timestamps.
   */
  private void migrateLegacyRelativeLog() {
    if (!Files.isRegularFile(logFile)) {
      return;
    }
    try {
      if (!looksLikeLegacyRelativeLog(logFile)) {
        return;
      }
      Files.deleteIfExists(logFile);
      headerWritten.set(false);
    } catch (IOException e) {
      throw new IllegalStateException("live log migrate failed: " + logFile, e);
    }
  }

  static boolean looksLikeLegacyRelativeLog(Path path) throws IOException {
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    for (String raw : lines) {
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      if (trimmed.regionMatches(true, 0, "ts,", 0, 3)) {
        continue;
      }
      String ts = trimmed.split(",", 2)[0].trim();
      if (WALL_CLOCK_TS.matcher(ts).matches()) {
        return false;
      }
      return LEGACY_RELATIVE_TS.matcher(ts).matches();
    }
    return false;
  }

  private void ensureHeader() {
    if (headerWritten.get()) {
      return;
    }
    if (Files.isRegularFile(logFile)) {
      headerWritten.set(true);
      return;
    }
    appendLine("# live UDS session log (Interpreter) — ts = local HH:mm:ss.SSS");
    appendLine("ts,direction,hex");
    headerWritten.set(true);
  }

  /** Wall-clock time of day for human-readable Interpreter ordering. */
  private String nextTs() {
    return LocalTime.now().format(WALL_TS);
  }

  private void appendLine(String line) {
    try (BufferedWriter writer =
        Files.newBufferedWriter(
            logFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND)) {
      writer.write(line);
      writer.newLine();
      writer.flush();
    } catch (IOException e) {
      throw new IllegalStateException("live log append failed: " + logFile, e);
    }
  }
}
