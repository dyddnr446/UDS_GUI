package uds.catalog.model;

public record NrcDefinition(
    String code,
    String name,
    String meaningKo,
    String typicalServices,
    String whenAppearsKo,
    String whatToCheckKo,
    String notes) {}
