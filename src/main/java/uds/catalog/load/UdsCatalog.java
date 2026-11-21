package uds.catalog.load;

import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.DidGuideMetadata;
import uds.catalog.model.ServiceGuideMetadata;
import uds.catalog.model.SymptomGuideMetadata;

/**
 * Immutable catalog snapshot: system slices + optional guide metadata. POI types never appear here
 * (FR-03, FR-22).
 */
public final class UdsCatalog {

  private final String catalogVersion;
  private final StandardCatalog standard;
  private final PayloadCatalog payload;
  private final EcuConfigCatalog ecuConfig;
  private final ScenarioCatalog scenario;
  private final GuideCatalog guide;

  public UdsCatalog(
      String catalogVersion,
      StandardCatalog standard,
      PayloadCatalog payload,
      EcuConfigCatalog ecuConfig,
      ScenarioCatalog scenario,
      GuideCatalog guide) {
    this.catalogVersion = Objects.requireNonNull(catalogVersion, "catalogVersion");
    this.standard = Objects.requireNonNull(standard, "standard");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.ecuConfig = Objects.requireNonNull(ecuConfig, "ecuConfig");
    this.scenario = Objects.requireNonNull(scenario, "scenario");
    this.guide = Objects.requireNonNull(guide, "guide");
  }

  public String catalogVersion() {
    return catalogVersion;
  }

  public StandardCatalog getStandard() {
    return standard;
  }

  public PayloadCatalog getPayload() {
    return payload;
  }

  public EcuConfigCatalog getEcuConfig() {
    return ecuConfig;
  }

  public ScenarioCatalog getScenario() {
    return scenario;
  }

  public GuideCatalog getGuide() {
    return guide;
  }

  public Optional<DidGuideMetadata> getDidGuide(String didId) {
    return guide.getDidGuide(didId);
  }

  public Optional<ServiceGuideMetadata> getServiceGuide(int sid) {
    return guide.getServiceGuide(sid);
  }

  public Optional<SymptomGuideMetadata> getSymptomGuide(String symptomKey) {
    return guide.getSymptomGuide(symptomKey);
  }
}
