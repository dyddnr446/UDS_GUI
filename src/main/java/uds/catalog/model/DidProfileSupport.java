package uds.catalog.model;

public record DidProfileSupport(
    String profileId,
    String didId,
    boolean readEnabled,
    boolean writeEnabled,
    boolean runtimeEnabled,
    int sortOrder) {}
