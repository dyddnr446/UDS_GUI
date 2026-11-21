package uds.catalog.load;

import java.util.Objects;

/**
 * Immutable catalog snapshot from DID/DTC + SID masters. POI types never appear here (FR-03,
 * FR-22).
 */
public final class UdsCatalog {

  private final String catalogVersion;
  private final StandardCatalog standard;
  private final PayloadCatalog payload;
  private final EcuConfigCatalog ecuConfig;
  private final ScenarioCatalog scenario;

  public UdsCatalog(
      String catalogVersion,
      StandardCatalog standard,
      PayloadCatalog payload,
      EcuConfigCatalog ecuConfig,
      ScenarioCatalog scenario) {
    this.catalogVersion = Objects.requireNonNull(catalogVersion, "catalogVersion");
    this.standard = Objects.requireNonNull(standard, "standard");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.ecuConfig = Objects.requireNonNull(ecuConfig, "ecuConfig");
    this.scenario = Objects.requireNonNull(scenario, "scenario");
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
}
