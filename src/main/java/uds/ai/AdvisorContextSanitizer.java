package uds.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import uds.analysis.AnalysisSessionStore;
import uds.read.Finding;
import uds.read.ReadDataResult;

/**
 * Builds diagnosis-focused AdvisorContext from Read/Finding and strips password/seed/key/0x27
 * payload material (FR-09, NFR-18/20).
 *
 * <p>LOG (Decode), WRITE, ACCESS are kept in the session store / UI fact zone but are not sent to
 * AI — diagnosis is based on DID Read values and Findings.
 */
public final class AdvisorContextSanitizer {

  private static final Pattern SENSITIVE_HEX =
      Pattern.compile(
          "(?i)(?:AA\\s*BB\\s*CC\\s*DD|D3\\s*AC\\s*3F\\s*47|AABBCCDD|D3AC3F47)");
  private static final Pattern PASSWORD_FIELD =
      Pattern.compile("(?i).*(password|passwd|seed|key|securitypayload|apikey|api_key).*");

  public AdvisorContext sanitize(String symptomText, String profileId, AnalysisSessionStore store) {
    Objects.requireNonNull(store, "store");
    String symptom = scrub(symptomText == null ? "" : symptomText);
    String profile = profileId == null ? "" : profileId;
    List<AdvisorContext.FactRow> facts = new ArrayList<>();

    List<AnalysisSessionStore.FindingEntry> findings = store.findings();
    if (!findings.isEmpty()) {
      Set<String> linkedReadIds = new LinkedHashSet<>();
      for (AnalysisSessionStore.FindingEntry e : findings) {
        facts.add(fact(e.evidenceId(), "FINDING", findingFields(e.finding(), e.readEvidenceId())));
        if (e.readEvidenceId() != null && !e.readEvidenceId().isBlank()) {
          linkedReadIds.add(e.readEvidenceId());
        }
      }
      for (AnalysisSessionStore.ReadEntry e : store.readResults()) {
        if (linkedReadIds.contains(e.evidenceId())) {
          facts.add(fact(e.evidenceId(), "READ", readFields(e.result())));
        }
      }
    } else {
      // Fallback: no Finding yet — still allow AI to see Read results if any.
      for (AnalysisSessionStore.ReadEntry e : store.readResults()) {
        facts.add(fact(e.evidenceId(), "READ", readFields(e.result())));
      }
    }

    return new AdvisorContext(symptom, profile, facts);
  }

  private static AdvisorContext.FactRow fact(String id, String kind, Map<String, String> fields) {
    Map<String, String> cleaned = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : fields.entrySet()) {
      String key = e.getKey();
      String value = e.getValue();
      if (key == null) {
        continue;
      }
      if (PASSWORD_FIELD.matcher(key).matches()) {
        cleaned.put(key, "<REDACTED>");
        continue;
      }
      cleaned.put(key, scrub(value));
    }
    return new AdvisorContext.FactRow(id, kind, cleaned);
  }

  private static Map<String, String> readFields(ReadDataResult r) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("did", nullToEmpty(r.did()));
    m.put("displayValue", nullToEmpty(r.displayValue()));
    m.put("status", r.status() == null ? "" : r.status().name());
    m.put("detailCode", r.detailCode() == null ? "" : r.detailCode().name());
    // Omit raw request/response HEX from AI context (NFR-20 belt-and-suspenders).
    return m;
  }

  private static Map<String, String> findingFields(Finding f, String readEvidenceId) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("did", nullToEmpty(f.did()));
    m.put("displayValue", nullToEmpty(f.displayValue()));
    m.put("status", f.status() == null ? "" : f.status().name());
    m.put("detailCode", f.detailCode() == null ? "" : f.detailCode().name());
    m.put("evidence", scrub(nullToEmpty(f.evidence())));
    m.put("readEvidenceId", nullToEmpty(readEvidenceId));
    return m;
  }

  static String scrub(String raw) {
    if (raw == null || raw.isEmpty()) {
      return raw == null ? "" : raw;
    }
    String out = SENSITIVE_HEX.matcher(raw).replaceAll("<REDACTED>");
    // Never forward obvious secret assignments in free text.
    out =
        out.replaceAll("(?i)(password\\s*[=:]\\s*)\\S+", "$1<REDACTED>")
            .replaceAll("(?i)(seed\\s*[=:]\\s*)\\S+", "$1<REDACTED>")
            .replaceAll("(?i)(key\\s*[=:]\\s*)\\S+", "$1<REDACTED>")
            .replaceAll("(?i)(api[_-]?key\\s*[=:]\\s*)\\S+", "$1<REDACTED>");
    return out;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
