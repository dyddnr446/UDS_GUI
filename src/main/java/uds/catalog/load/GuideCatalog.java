package uds.catalog.load;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.ControllerProfileGuideMetadata;
import uds.catalog.model.DidGuideMetadata;
import uds.catalog.model.DtcStatusBitGuideMetadata;
import uds.catalog.model.LayoutGuideMetadata;
import uds.catalog.model.NrcGuideMetadata;
import uds.catalog.model.ServiceGuideMetadata;
import uds.catalog.model.SubFunctionGuideMetadata;
import uds.catalog.model.SymptomGuideMetadata;
import uds.catalog.model.DidDefinition;

/** Optional human/AI guide slice loaded from {@code catalog/uds_guide_catalog.xlsx}. */
public final class GuideCatalog {

  private final Map<String, ServiceGuideMetadata> servicesBySid;
  private final Map<String, SubFunctionGuideMetadata> subFunctionsByKey;
  private final Map<String, NrcGuideMetadata> nrcsByCode;
  private final Map<Integer, DtcStatusBitGuideMetadata> dtcBitsByBit;
  private final Map<String, LayoutGuideMetadata> layoutsByKey;
  private final Map<String, DidGuideMetadata> didsById;
  private final Map<String, ControllerProfileGuideMetadata> profilesById;
  private final Map<String, SymptomGuideMetadata> symptomsByKey;

  public GuideCatalog(
      Map<String, ServiceGuideMetadata> servicesBySid,
      Map<String, SubFunctionGuideMetadata> subFunctionsByKey,
      Map<String, NrcGuideMetadata> nrcsByCode,
      Map<Integer, DtcStatusBitGuideMetadata> dtcBitsByBit,
      Map<String, LayoutGuideMetadata> layoutsByKey,
      Map<String, DidGuideMetadata> didsById,
      Map<String, ControllerProfileGuideMetadata> profilesById,
      Map<String, SymptomGuideMetadata> symptomsByKey) {
    this.servicesBySid = Map.copyOf(Objects.requireNonNull(servicesBySid));
    this.subFunctionsByKey = Map.copyOf(Objects.requireNonNull(subFunctionsByKey));
    this.nrcsByCode = Map.copyOf(Objects.requireNonNull(nrcsByCode));
    this.dtcBitsByBit = Map.copyOf(Objects.requireNonNull(dtcBitsByBit));
    this.layoutsByKey = Map.copyOf(Objects.requireNonNull(layoutsByKey));
    this.didsById = Map.copyOf(Objects.requireNonNull(didsById));
    this.profilesById = Map.copyOf(Objects.requireNonNull(profilesById));
    this.symptomsByKey = Map.copyOf(Objects.requireNonNull(symptomsByKey));
  }

  public static GuideCatalog empty() {
    return new GuideCatalog(
        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
  }

  public Map<String, ServiceGuideMetadata> servicesBySid() {
    return servicesBySid;
  }

  public Map<String, DidGuideMetadata> didGuideMap() {
    return didsById;
  }

  public Optional<ServiceGuideMetadata> getServiceGuide(int sid) {
    return Optional.ofNullable(servicesBySid.get(StandardCatalog.formatSid(sid)));
  }

  public Optional<ServiceGuideMetadata> getServiceGuide(String sidHex) {
    if (sidHex == null || sidHex.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(servicesBySid.get(sidHex.trim()));
  }

  public Optional<DidGuideMetadata> getDidGuide(String didId) {
    if (didId == null || didId.isBlank()) {
      return Optional.empty();
    }
    String norm = normalizeDid(didId);
    DidGuideMetadata direct = didsById.get(didId.trim());
    if (direct != null) {
      return Optional.of(direct);
    }
    for (Map.Entry<String, DidGuideMetadata> e : didsById.entrySet()) {
      if (normalizeDid(e.getKey()).equals(norm)) {
        return Optional.of(e.getValue());
      }
    }
    return Optional.empty();
  }

  public Optional<DidGuideMetadata> getDidGuide(int did) {
    return getDidGuide(DidDefinition.formatDidHex(did));
  }

  public Optional<SymptomGuideMetadata> getSymptomGuide(String symptomKey) {
    if (symptomKey == null || symptomKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(symptomsByKey.get(symptomKey.trim()));
  }

  public Optional<NrcGuideMetadata> getNrcGuide(String code) {
    return Optional.ofNullable(nrcsByCode.get(code));
  }

  public Optional<SubFunctionGuideMetadata> getSubFunctionGuide(String parentSid, String sub) {
    return Optional.ofNullable(subFunctionsByKey.get(parentSid + "|" + sub));
  }

  public Optional<ControllerProfileGuideMetadata> getProfileGuide(String profileId) {
    return Optional.ofNullable(profilesById.get(profileId));
  }

  public Optional<DtcStatusBitGuideMetadata> getDtcBitGuide(int bit) {
    return Optional.ofNullable(dtcBitsByBit.get(bit));
  }

  public Optional<LayoutGuideMetadata> getLayoutGuide(String kind, String sidOrMarker) {
    return Optional.ofNullable(layoutsByKey.get(kind + "|" + sidOrMarker));
  }

  private static String normalizeDid(String did) {
    String s = did.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("0X")) {
      s = s.substring(2);
    }
    return s;
  }
}
