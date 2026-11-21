package uds.analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User-facing CSV live log at {@code output/live_session.log} ({@code ts,direction,hex}). Never
 * writes plaintext 0x27 seed/key (caller must pass masked hex).
 */
public final class LiveSessionLog {

  public static final String FILE_NAME = "live_session.log";

  private final Path logFile;
  private final long startNanos;
  private final Object lock = new Object();
  private final AtomicBoolean headerWritten = new AtomicBoolean(false);

  /** @param outputDir parent directory for {@link #FILE_NAME} (usually project {@code output/}). */
  public LiveSessionLog(Path outputDir) {
    Objects.requireNonNull(outputDir, "outputDir");
    this.logFile = outputDir.resolve(FILE_NAME);
    this.startNanos = System.nanoTime();
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new IllegalStateException("cannot create output dir: " + outputDir, e);
    }
    synchronized (lock) {
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

  private void ensureHeader() {
    if (headerWritten.get()) {
      return;
    }
    if (Files.isRegularFile(logFile)) {
      headerWritten.set(true);
      return;
    }
    appendLine("# live UDS session log (Interpreter)");
    appendLine("ts,direction,hex");
    headerWritten.set(true);
  }

  private String nextTs() {
    double sec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    return String.format(Locale.ROOT, "%.3f", sec);
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
