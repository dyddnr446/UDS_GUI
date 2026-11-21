package uds.fixture;

public record WriteScenario(
    String scenarioId,
    String controllerProfile,
    String label,
    String did,
    String inputValue,
    String dataHex,
    String requestHex,
    String responseKind,
    String responseHex,
    String expectedStatus) {}
