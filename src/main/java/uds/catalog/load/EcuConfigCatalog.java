package uds.catalog.load;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.ControllerProfile;
import uds.catalog.model.DidProfileSupport;

/** Controller / ECU capability slice of the catalog. */
public final class EcuConfigCatalog {

  private final List<ControllerProfile> controllerProfiles;
  private final List<DidProfileSupport> didProfileSupports;

  public EcuConfigCatalog(
      List<ControllerProfile> controllerProfiles, List<DidProfileSupport> didProfileSupports) {
    this.controllerProfiles =
        List.copyOf(Objects.requireNonNull(controllerProfiles, "controllerProfiles"));
    this.didProfileSupports =
        List.copyOf(Objects.requireNonNull(didProfileSupports, "didProfileSupports"));
  }

  public List<ControllerProfile> controllerProfiles() {
    return controllerProfiles;
  }

  public List<DidProfileSupport> didProfileSupports() {
    return didProfileSupports;
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

  /** Catalog session names declared on the profile ({@code supported_sessions}). */
  public List<String> supportedSessions(String profileId) {
    return findProfile(profileId).map(ControllerProfile::supportedSessions).orElse(List.of());
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
