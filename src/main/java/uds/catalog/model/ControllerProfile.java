package uds.catalog.model;

public record ControllerProfile(
    String profileId,
    String displayName,
    String family,
    String speedBasis,
    String telemetryReference,
    String didPolicy,
    String notes,
    boolean runtimeEnabled,
    int sortOrder) {}
