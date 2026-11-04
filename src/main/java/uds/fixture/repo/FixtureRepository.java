package uds.fixture.repo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Loads access/read/write CSV fixtures (NFR-13). */
public final class FixtureRepository {

  private final List<AccessStep> accessSteps;
  private final List<ReadScenario> readScenarios;
  private final List<WriteScenario> writeScenarios;

  public FixtureRepository(Path accessCsv, Path readCsv, Path writeCsv) {
    this.accessSteps = List.copyOf(loadAccess(accessCsv));
    this.readScenarios = List.copyOf(loadRead(readCsv));
    this.writeScenarios = List.copyOf(loadWrite(writeCsv));
  }

  public List<AccessStep> accessSteps() {
    return accessSteps;
  }

  public List<ReadScenario> readScenarios() {
    return readScenarios;
  }

  public List<WriteScenario> writeScenarios() {
    return writeScenarios;
  }

  public List<AccessStep> accessStepsForFlow(String flowId) {
    List<AccessStep> out = new ArrayList<>();
    for (AccessStep step : accessSteps) {
      if (step.flowId().equals(flowId)) {
        out.add(step);
      }
    }
    out.sort((a, b) -> Integer.compare(a.stepNo(), b.stepNo()));
    return Collections.unmodifiableList(out);
  }

  public ReadScenario findRead(String scenarioId, String profileId) {
    for (ReadScenario s : readScenarios) {
      if (s.scenarioId().equals(scenarioId) && s.controllerProfile().equals(profileId)) {
        return s;
      }
    }
    return null;
  }

  public WriteScenario findWrite(String scenarioId, String profileId) {
    for (WriteScenario s : writeScenarios) {
      if (s.scenarioId().equals(scenarioId) && s.controllerProfile().equals(profileId)) {
        return s;
      }
    }
    return null;
  }

  private static List<AccessStep> loadAccess(Path path) {
    List<String[]> rows = readCsvRows(path);
    List<AccessStep> out = new ArrayList<>();
    for (String[] c : rows) {
      out.add(
          new AccessStep(
              c[0],
              Integer.parseInt(c[1].trim()),
              c[2],
              c[3],
              c[4],
              c[5],
              c[6],
              c[7],
              c[8],
              c.length > 9 ? c[9] : ""));
    }
    return out;
  }

  private static List<ReadScenario> loadRead(Path path) {
    List<String[]> rows = readCsvRows(path);
    List<ReadScenario> out = new ArrayList<>();
    for (String[] c : rows) {
      out.add(new ReadScenario(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]));
    }
    return out;
  }

  private static List<WriteScenario> loadWrite(Path path) {
    List<String[]> rows = readCsvRows(path);
    List<WriteScenario> out = new ArrayList<>();
    for (String[] c : rows) {
      out.add(new WriteScenario(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9]));
    }
    return out;
  }

  private static List<String[]> readCsvRows(Path path) {
    Objects.requireNonNull(path, "path");
    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      if (header == null) {
        throw new IllegalArgumentException("empty csv: " + path);
      }
      List<String[]> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        rows.add(splitCsv(line));
      }
      return rows;
    } catch (IOException e) {
      throw new IllegalArgumentException("cannot read csv: " + path, e);
    }
  }

  private static String[] splitCsv(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (ch == ',' && !inQuotes) {
        cells.add(cur.toString().trim());
        cur.setLength(0);
        continue;
      }
      cur.append(ch);
    }
    cells.add(cur.toString().trim());
    return cells.toArray(String[]::new);
  }

  public static String normalizeHex(String hex) {
    if (hex == null) {
      return "";
    }
    return hex.replace(" ", "").replace("_", "").toUpperCase(Locale.ROOT);
  }
}
