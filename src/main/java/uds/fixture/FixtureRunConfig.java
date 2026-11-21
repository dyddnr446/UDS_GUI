package uds.fixture;

import java.util.Objects;

/** File Port only run configuration (scenario_id or access flow_id). */
public record FixtureRunConfig(String scenarioOrFlowId) {
  public FixtureRunConfig {
    Objects.requireNonNull(scenarioOrFlowId, "scenarioOrFlowId");
    if (scenarioOrFlowId.isBlank()) {
      throw new IllegalArgumentException("scenarioOrFlowId blank");
    }
  }
}
