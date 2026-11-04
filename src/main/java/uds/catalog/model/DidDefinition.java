package uds.catalog.model;

import java.math.BigDecimal;
import java.util.Optional;

public record DidDefinition(
    String type,
    String id,
    String name,
    String relatedSid,
    String dataType,
    int byteLength,
    BigDecimal scale,
    String unit,
    Optional<BigDecimal> normalMin,
    Optional<BigDecimal> normalMax,
    String access,
    Optional<BigDecimal> writeMin,
    Optional<BigDecimal> writeMax,
    String supportedProfiles,
    String speedBasis,
    String sourceRef,
    String example,
    String whenToUseKo,
    String whyUseKo,
    String notes,
    boolean runtimeEnabled,
    int sortOrder,
    String requiredSession,
    String requiredSecurityMechanism,
    String requiredSecurityLevel,
    String lengthPolicy) {}
