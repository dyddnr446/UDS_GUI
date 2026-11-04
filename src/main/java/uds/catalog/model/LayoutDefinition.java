package uds.catalog.model;

/** Request or response layout row from catalog. */
public record LayoutDefinition(
    String kind,
    String sidOrMarker,
    String layout,
    String exampleHex,
    String whenYouNeedThisKo,
    String idType,
    int idOffset,
    Integer dataOffset,
    Boolean variableLength,
    Integer minLengthBytes,
    String notes) {}
