package uds.catalog.model;

public record ServiceDefinition(
    String sid,
    String name,
    String positiveResponseSid,
    String category,
    String purposeSummaryKo,
    String whenToUseKo,
    String whyUseKo,
    String typicalFlowKo,
    String sessionNote,
    String securityNote,
    boolean hasSubfunction,
    String notes,
    String exampleReqHex,
    String exampleResHex,
    int subByteOffset) {}
