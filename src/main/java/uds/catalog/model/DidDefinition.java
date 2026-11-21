package uds.catalog.model;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/** Runtime DID payload specification (system catalog). */
public record DidDefinition(
    String type,
    int did,
    String name,
    String relatedSid,
    String dataType,
    int byteLength,
    BigDecimal scale,
    BigDecimal offset,
    String unit,
    Optional<BigDecimal> normalMin,
    Optional<BigDecimal> normalMax,
    String access,
    Optional<BigDecimal> writeMin,
    Optional<BigDecimal> writeMax,
    String supportedProfiles,
    boolean runtimeEnabled,
    int sortOrder,
    String requiredSession,
    String requiredSecurityMechanism,
    String requiredSecurityLevel,
    String lengthPolicy) {

  public DidDefinition {
    type = nullToEmpty(type);
    name = nullToEmpty(name);
    relatedSid = nullToEmpty(relatedSid);
    dataType = nullToEmpty(dataType);
    scale = scale == null ? BigDecimal.ONE : scale;
    offset = offset == null ? BigDecimal.ZERO : offset;
    unit = nullToEmpty(unit);
    normalMin = normalMin == null ? Optional.empty() : normalMin;
    normalMax = normalMax == null ? Optional.empty() : normalMax;
    access = nullToEmpty(access);
    writeMin = writeMin == null ? Optional.empty() : writeMin;
    writeMax = writeMax == null ? Optional.empty() : writeMax;
    supportedProfiles = nullToEmpty(supportedProfiles);
    requiredSession = nullToEmpty(requiredSession);
    requiredSecurityMechanism = nullToEmpty(requiredSecurityMechanism);
    requiredSecurityLevel = nullToEmpty(requiredSecurityLevel);
    lengthPolicy = nullToEmpty(lengthPolicy);
  }

  public String didHex() {
    return formatDidHex(did);
  }

  public String id() {
    return didHex();
  }

  public static String formatDidHex(int did) {
    String h = Integer.toHexString(did).toUpperCase(Locale.ROOT);
    if ((h.length() & 1) == 1) {
      h = "0" + h;
    }
    return "0x" + h;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
