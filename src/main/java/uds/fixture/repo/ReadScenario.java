package uds.fixture.repo;

public record ReadScenario(
    String scenarioId,
    String controllerProfile,
    String label,
    String did,
    String requestHex,
    String responseKind,
    String responseHex,
    String expectedValue,
    String expectedStatus) {}
