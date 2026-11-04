package uds.catalog.model;

public record SymptomDidMap(
    String symptomKey,
    String keywordsKo,
    String didId,
    int priority,
    String reasonKo,
    String notes) {}
