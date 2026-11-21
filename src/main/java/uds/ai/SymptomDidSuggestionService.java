package uds.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.DidProfileSupport;
import uds.catalog.model.SymptomDidMap;

/**
 * FR-13: map symptom text → candidate DIDs; runtime_enabled=Y only for execution; 0xF186 is static
 * log reference only.
 */
public final class SymptomDidSuggestionService {

  public static final String STATIC_LOG_DID = "0xF186";

  public SymptomSuggestion suggest(UdsCatalog catalog, String profileId, String symptomText) {
    Objects.requireNonNull(catalog, "catalog");
    Objects.requireNonNull(profileId, "profileId");
    String text = symptomText == null ? "" : symptomText.trim();
    if (text.isEmpty()) {
      return new SymptomSuggestion("현상을 입력하세요", List.of(), List.of(), List.of());
    }

    String lower = text.toLowerCase(Locale.ROOT);
    List<CandidateDid> candidates = new ArrayList<>();
    for (SymptomDidMap row : catalog.getScenario().symptomDidMaps()) {
      if (matches(row, lower)) {
        String reason =
            catalog
                .getSymptomGuide(row.symptomKey())
                .map(g -> g.reasonKo() == null ? "" : g.reasonKo())
                .orElse("");
        candidates.add(
            new CandidateDid(
                normalizeDid(row.didIdHex()), row.priority(), reason, row.symptomKey()));
      }
    }
    candidates.sort(
        (a, b) -> {
          int p = Integer.compare(a.priority(), b.priority());
          return p != 0 ? p : a.didId().compareToIgnoreCase(b.didId());
        });

    Set<String> runtimeEnabled = runtimeEnabledDidSet(catalog, profileId);
    List<String> staticRefs = new ArrayList<>();
    List<String> executable = new ArrayList<>();
    LinkedHashSet<String> seenExec = new LinkedHashSet<>();
    LinkedHashSet<String> seenStatic = new LinkedHashSet<>();
    for (CandidateDid c : candidates) {
      if (STATIC_LOG_DID.equalsIgnoreCase(c.didId())) {
        if (seenStatic.add(STATIC_LOG_DID)) {
          staticRefs.add(STATIC_LOG_DID);
        }
        continue;
      }
      if (runtimeEnabled.contains(c.didId().toUpperCase(Locale.ROOT)) && seenExec.add(c.didId())) {
        executable.add(c.didId());
      }
    }
    return new SymptomSuggestion(null, List.copyOf(candidates), List.copyOf(executable), List.copyOf(staticRefs));
  }

  private static boolean matches(SymptomDidMap row, String lowerSymptom) {
    String keywords = row.keywordsKo();
    if (keywords != null && !keywords.isBlank()) {
      for (String token : keywords.split("\\|")) {
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (!t.isEmpty() && lowerSymptom.contains(t)) {
          return true;
        }
      }
    }
    String key = row.symptomKey();
    return key != null && !key.isBlank() && lowerSymptom.contains(key.toLowerCase(Locale.ROOT));
  }

  private static Set<String> runtimeEnabledDidSet(UdsCatalog catalog, String profileId) {
    Set<String> out = new LinkedHashSet<>();
    for (DidProfileSupport s : catalog.getEcuConfig().didProfileSupports()) {
      if (s.profileId().equals(profileId) && s.runtimeEnabled() && s.readEnabled()) {
        out.add(normalizeDid(s.didId()).toUpperCase(Locale.ROOT));
      }
    }
    return out;
  }

  private static String normalizeDid(String did) {
    if (did == null) {
      return "";
    }
    String t = did.trim();
    if (t.regionMatches(true, 0, "0x", 0, 2)) {
      return "0x" + t.substring(2).toUpperCase(Locale.ROOT);
    }
    return t.toUpperCase(Locale.ROOT);
  }

  public record CandidateDid(String didId, int priority, String reasonKo, String symptomKey) {}

  public record SymptomSuggestion(
      String guidance,
      List<CandidateDid> candidates,
      List<String> runtimeExecutableDids,
      List<String> staticLogReferenceDids) {}
}
