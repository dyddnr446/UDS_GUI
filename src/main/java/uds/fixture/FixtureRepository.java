package uds.fixture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import uds.catalog.load.CatalogLoader;

/** Access/read/write fixture scenarios from {@code fixtures/*.xlsx} (NFR-13). */
public final class FixtureRepository {

  private final List<AccessStep> accessSteps;
  private final List<ReadScenario> readScenarios;
  private final List<WriteScenario> writeScenarios;

  public FixtureRepository(
      List<AccessStep> accessSteps,
      List<ReadScenario> readScenarios,
      List<WriteScenario> writeScenarios) {
    this.accessSteps = List.copyOf(Objects.requireNonNull(accessSteps, "accessSteps"));
    this.readScenarios = List.copyOf(Objects.requireNonNull(readScenarios, "readScenarios"));
    this.writeScenarios = List.copyOf(Objects.requireNonNull(writeScenarios, "writeScenarios"));
  }

  public static FixtureRepository load(Path fixtureXlsx) throws IOException {
    Objects.requireNonNull(fixtureXlsx, "fixtureXlsx");
    return CatalogLoader.loadFixtures(fixtureXlsx);
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

  public static String normalizeHex(String hex) {
    if (hex == null) {
      return "";
    }
    return hex.replace(" ", "").replace("_", "").toUpperCase(Locale.ROOT);
  }
}
