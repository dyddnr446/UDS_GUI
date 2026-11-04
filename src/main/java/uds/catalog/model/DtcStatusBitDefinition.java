package uds.catalog.model;

public record DtcStatusBitDefinition(
    int bit, String mask, String nameEn, String meaningKo, String checkPointKo) {}
