package uds.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads FR-01 CSV-like UDS logs: UTF-8, header {@code ts,direction,hex},
 * {@code #} comments and blank lines ignored; broken lines become {@link LineError}.
 */
public final class LogReader {

  private LogReader() {}

  public static LogReadResult read(Path path) throws IOException {
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    List<Frame> frames = new ArrayList<>();
    List<LineError> errors = new ArrayList<>();
    boolean headerSeen = false;

    for (int i = 0; i < lines.size(); i++) {
      int lineNo = i + 1;
      String raw = lines.get(i);
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      if (!headerSeen) {
        if (!isHeader(trimmed)) {
          errors.add(new LineError(lineNo, raw, "expected header ts,direction,hex"));
          continue;
        }
        headerSeen = true;
        continue;
      }
      String[] parts = trimmed.split(",", 3);
      if (parts.length < 3) {
        errors.add(new LineError(lineNo, raw, "expected 3 CSV columns"));
        continue;
      }
      String ts = parts[0].trim();
      String direction = parts[1].trim();
      String hexRaw = parts[2].trim();
      Optional<byte[]> bytes = HexNormalizer.normalize(hexRaw);
      if (bytes.isEmpty()) {
        errors.add(new LineError(lineNo, raw, HexNormalizer.reasonIfInvalid(hexRaw)));
        continue;
      }
      frames.add(new Frame(ts, direction, hexRaw, bytes.get()));
    }

    if (!headerSeen) {
      errors.add(new LineError(0, "", "missing header ts,direction,hex"));
    }
    return new LogReadResult(frames, errors);
  }

  private static boolean isHeader(String line) {
    String[] parts = line.split(",", -1);
    if (parts.length < 3) {
      return false;
    }
    return "ts".equalsIgnoreCase(parts[0].trim())
        && "direction".equalsIgnoreCase(parts[1].trim())
        && "hex".equalsIgnoreCase(parts[2].trim());
  }
}
