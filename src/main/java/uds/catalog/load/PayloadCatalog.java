package uds.catalog.load;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.DidDefinition;

/** Diagnostic payload / DID specification slice of the catalog. */
public final class PayloadCatalog {

  private final List<DidDefinition> didDefinitions;

  public PayloadCatalog(List<DidDefinition> didDefinitions) {
    this.didDefinitions = List.copyOf(Objects.requireNonNull(didDefinitions, "didDefinitions"));
  }

  public List<DidDefinition> didDefinitions() {
    return didDefinitions;
  }

  public Optional<DidDefinition> getDid(String didId) {
    if (didId == null || didId.isBlank()) {
      return Optional.empty();
    }
    String norm = normalizeDid(didId);
    return didDefinitions.stream().filter(d -> normalizeDid(d.id()).equals(norm)).findFirst();
  }

  private static String normalizeDid(String did) {
    String s = did.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("0X")) {
      s = s.substring(2);
    }
    return s;
  }
}
