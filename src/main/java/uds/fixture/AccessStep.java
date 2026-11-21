package uds.fixture;

public record AccessStep(
    String flowId,
    int stepNo,
    String profileId,
    String action,
    String initialState,
    String requestTemplate,
    String responseKind,
    String responseTemplate,
    String expectedState,
    String expectedError) {}
