package uds.catalog.model;

public record SubFunctionDefinition(
    String parentSid,
    String subFunction,
    String name,
    boolean suppressBitApplicable,
    String responseNeededPractice,
    String paramsSummary,
    String whenToUseKo,
    String whyUseKo,
    String notes) {}
