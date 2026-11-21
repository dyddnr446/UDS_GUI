package uds.catalog.load;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.DtcStatusBitDefinition;
import uds.catalog.model.NrcDefinition;
import uds.catalog.model.ServiceDefinition;
import uds.catalog.model.SubFunctionDefinition;

/** ISO 14229 / protocol standard slice of the catalog. */
public final class StandardCatalog {

  private final Map<String, ServiceDefinition> servicesBySid;
  private final List<SubFunctionDefinition> subFunctions;
  private final Map<String, NrcDefinition> nrcsByCode;
  private final List<DtcStatusBitDefinition> dtcStatusBits;

  public StandardCatalog(
      Map<String, ServiceDefinition> servicesBySid,
      List<SubFunctionDefinition> subFunctions,
      Map<String, NrcDefinition> nrcsByCode,
      List<DtcStatusBitDefinition> dtcStatusBits) {
    this.servicesBySid = Map.copyOf(Objects.requireNonNull(servicesBySid, "servicesBySid"));
    this.subFunctions = List.copyOf(Objects.requireNonNull(subFunctions, "subFunctions"));
    this.nrcsByCode = Map.copyOf(Objects.requireNonNull(nrcsByCode, "nrcsByCode"));
    this.dtcStatusBits = List.copyOf(Objects.requireNonNull(dtcStatusBits, "dtcStatusBits"));
  }

  public Map<String, ServiceDefinition> servicesBySid() {
    return servicesBySid;
  }

  public List<SubFunctionDefinition> subFunctions() {
    return subFunctions;
  }

  /** DiagnosticSessionControl (0x10) sub-functions from SubFunctions sheet, sorted by hex. */
  public List<SubFunctionDefinition> diagnosticSessions() {
    return subFunctions.stream()
        .filter(s -> "0x10".equalsIgnoreCase(s.parentSid()))
        .sorted(
            (a, b) ->
                Integer.compare(
                    parseSubByte(a.subFunction()), parseSubByte(b.subFunction())))
        .toList();
  }

  private static int parseSubByte(String hex) {
    if (hex == null || hex.isBlank()) {
      return Integer.MAX_VALUE;
    }
    String compact = hex.trim().toUpperCase(Locale.ROOT).replace("0X", "");
    try {
      return Integer.parseInt(compact, 16);
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  public Map<String, NrcDefinition> nrcsByCode() {
    return nrcsByCode;
  }

  public List<DtcStatusBitDefinition> dtcStatusBits() {
    return dtcStatusBits;
  }

  /** Lookup by SID byte; key format matches catalog {@code 0x%02X}. */
  public Optional<ServiceDefinition> getService(int sid) {
    return Optional.ofNullable(servicesBySid.get(formatSid(sid)));
  }

  public Optional<NrcDefinition> getNrc(String code) {
    return Optional.ofNullable(nrcsByCode.get(code));
  }

  public static String formatSid(int sid) {
    return String.format(Locale.ROOT, "0x%02X", sid & 0xFF);
  }
}
