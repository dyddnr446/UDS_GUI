package uds.catalog.load;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import uds.catalog.model.ControllerProfile;
import uds.catalog.model.DidDefinition;
import uds.catalog.model.DidProfileSupport;
import uds.catalog.model.DtcStatusBitDefinition;
import uds.catalog.model.LayoutDefinition;
import uds.catalog.model.NrcDefinition;
import uds.catalog.model.ServiceDefinition;
import uds.catalog.model.SubFunctionDefinition;
import uds.catalog.model.SymptomDidMap;

/** Immutable catalog snapshot. POI types never appear here (FR-03, FR-22). */
public final class UdsCatalog {

  private final String catalogVersion;
  private final List<ControllerProfile> controllerProfiles;
  private final List<DidDefinition> didDefinitions;
  private final List<DidProfileSupport> didProfileSupports;
  private final List<SymptomDidMap> symptomDidMaps;
  private final Map<String, ServiceDefinition> servicesBySid;
  private final List<SubFunctionDefinition> subFunctions;
  private final Map<String, NrcDefinition> nrcsByCode;
  private final List<DtcStatusBitDefinition> dtcStatusBits;
  private final List<LayoutDefinition> requestLayouts;
  private final List<LayoutDefinition> responseLayouts;

  public UdsCatalog(
      String catalogVersion,
      List<ControllerProfile> controllerProfiles,
      List<DidDefinition> didDefinitions,
      List<DidProfileSupport> didProfileSupports,
      List<SymptomDidMap> symptomDidMaps,
      Map<String, ServiceDefinition> servicesBySid,
      List<SubFunctionDefinition> subFunctions,
      Map<String, NrcDefinition> nrcsByCode,
      List<DtcStatusBitDefinition> dtcStatusBits,
      List<LayoutDefinition> requestLayouts,
      List<LayoutDefinition> responseLayouts) {
    this.catalogVersion = catalogVersion;
    this.controllerProfiles = List.copyOf(controllerProfiles);
    this.didDefinitions = List.copyOf(didDefinitions);
    this.didProfileSupports = List.copyOf(didProfileSupports);
    this.symptomDidMaps = List.copyOf(symptomDidMaps);
    this.servicesBySid = Map.copyOf(servicesBySid);
    this.subFunctions = List.copyOf(subFunctions);
    this.nrcsByCode = Map.copyOf(nrcsByCode);
    this.dtcStatusBits = List.copyOf(dtcStatusBits);
    this.requestLayouts = List.copyOf(requestLayouts);
    this.responseLayouts = List.copyOf(responseLayouts);
  }

  public String catalogVersion() {
    return catalogVersion;
  }

  public List<ControllerProfile> controllerProfiles() {
    return controllerProfiles;
  }

  public List<DidDefinition> didDefinitions() {
    return didDefinitions;
  }

  public List<DidProfileSupport> didProfileSupports() {
    return didProfileSupports;
  }

  public List<SymptomDidMap> symptomDidMaps() {
    return symptomDidMaps;
  }

  public Map<String, ServiceDefinition> servicesBySid() {
    return servicesBySid;
  }

  public List<SubFunctionDefinition> subFunctions() {
    return subFunctions;
  }

  public Map<String, NrcDefinition> nrcsByCode() {
    return nrcsByCode;
  }

  public List<DtcStatusBitDefinition> dtcStatusBits() {
    return dtcStatusBits;
  }

  public List<LayoutDefinition> requestLayouts() {
    return requestLayouts;
  }

  public List<LayoutDefinition> responseLayouts() {
    return responseLayouts;
  }

  public Optional<ControllerProfile> findProfile(String profileId) {
    return controllerProfiles.stream().filter(p -> p.profileId().equals(profileId)).findFirst();
  }

  public List<ControllerProfile> runtimeEnabledProfiles() {
    return controllerProfiles.stream()
        .filter(ControllerProfile::runtimeEnabled)
        .sorted(Comparator.comparingInt(ControllerProfile::sortOrder))
        .toList();
  }

  public List<String> runtimeReadDids(String profileId) {
    return supportDids(profileId, true, false);
  }

  public List<String> runtimeWriteDids(String profileId) {
    return supportDids(profileId, false, true);
  }

  private List<String> supportDids(String profileId, boolean read, boolean write) {
    List<DidProfileSupport> matched = new ArrayList<>();
    for (DidProfileSupport s : didProfileSupports) {
      if (!s.profileId().equals(profileId) || !s.runtimeEnabled()) {
        continue;
      }
      if (read && !s.readEnabled()) {
        continue;
      }
      if (write && !s.writeEnabled()) {
        continue;
      }
      matched.add(s);
    }
    matched.sort(Comparator.comparingInt(DidProfileSupport::sortOrder));
    return matched.stream().map(DidProfileSupport::didId).toList();
  }
}
