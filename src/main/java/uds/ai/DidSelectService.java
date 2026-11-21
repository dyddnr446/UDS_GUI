package uds.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import uds.ai.SymptomDidSuggestionService.CandidateDid;

/**
 * AI step-1: pick DIDs from SymptomDidMap + runtime_enabled candidates, then intersect with the
 * allowed set. Empty/failed selection degrades to all runtime candidates.
 */
public final class DidSelectService {

  public static final int MAX_SELECTED = 5;

  private final AiChatCompleter completer;
  private final AiPromptConfig prompts;

  public DidSelectService(AiChatCompleter completer, AiPromptConfig prompts) {
    this.completer = Objects.requireNonNull(completer, "completer");
    this.prompts = Objects.requireNonNull(prompts, "prompts");
  }

  public DidSelectService(AiChatCompleter completer) {
    this(completer, AiPromptConfig.loadNear(java.nio.file.Path.of(System.getProperty("user.dir", "."))));
  }

  /**
   * Ask AI to select DIDs; filter to {@code runtimeExecutable}; degrade to full runtime list on
   * failure or empty intersection.
   */
  public SelectionResult select(
      String symptomText,
      String profileId,
      List<CandidateDid> candidates,
      List<String> runtimeExecutable) {
    List<String> runtime = normalizeList(runtimeExecutable);
    if (runtime.isEmpty()) {
      return new SelectionResult(List.of(), "", true, "no runtime-executable DIDs");
    }

    String user =
        prompts
            .selectUserTemplate()
            .replace("{{profileId}}", nullToEmpty(profileId))
            .replace("{{symptom}}", nullToEmpty(symptomText))
            .replace("{{candidates}}", formatCandidates(candidates, runtime))
            .replace("{{maxSelected}}", String.valueOf(MAX_SELECTED));

    AiChatCompleter.CompletionResult raw = completer.complete(prompts.selectSystemPrompt(), user);
    if (raw instanceof AiChatCompleter.CompletionResult.Err err) {
      return new SelectionResult(
          runtime, "", true, "select degrade: " + err.error().code() + " — using all runtime DIDs");
    }
    String content = ((AiChatCompleter.CompletionResult.Ok) raw).content();
    Parsed parsed = parseSelectionJson(content);
    List<String> filtered = intersectPreserveOrder(parsed.selectedDids(), Set.copyOf(runtime));
    if (filtered.isEmpty()) {
      return new SelectionResult(
          runtime,
          parsed.reason(),
          true,
          "select degrade: empty after filter — using all runtime DIDs");
    }
    if (filtered.size() > MAX_SELECTED) {
      filtered = List.copyOf(filtered.subList(0, MAX_SELECTED));
    }
    return new SelectionResult(filtered, parsed.reason(), false, null);
  }

  /** Pure filter helper for tests: intersect AI picks with allowed runtime set. */
  public static List<String> filterToAllowed(List<String> selected, List<String> runtimeExecutable) {
    return intersectPreserveOrder(normalizeList(selected), Set.copyOf(normalizeList(runtimeExecutable)));
  }

  static Parsed parseSelectionJson(String content) {
    if (content == null || content.isBlank()) {
      return new Parsed(List.of(), "");
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(stripFences(content));
      List<String> dids = new ArrayList<>();
      for (JsonNode n : root.path("selectedDids")) {
        String d = normalizeDid(n.asText(""));
        if (!d.isEmpty()) {
          dids.add(d);
        }
      }
      String reason = root.path("reason").asText("");
      return new Parsed(List.copyOf(dids), reason);
    } catch (Exception e) {
      return new Parsed(List.of(), "");
    }
  }

  private static String formatCandidates(List<CandidateDid> candidates, List<String> runtime) {
    Set<String> runtimeUpper = new LinkedHashSet<>();
    for (String r : runtime) {
      runtimeUpper.add(r.toUpperCase(Locale.ROOT));
    }
    StringBuilder sb = new StringBuilder();
    if (candidates != null) {
      for (CandidateDid c : candidates) {
        String did = normalizeDid(c.didId());
        if (!runtimeUpper.contains(did.toUpperCase(Locale.ROOT))) {
          continue;
        }
        sb.append("- did=")
            .append(did)
            .append(" priority=")
            .append(c.priority())
            .append(" symptomKey=")
            .append(nullToEmpty(c.symptomKey()))
            .append(" reason=")
            .append(nullToEmpty(c.reasonKo()))
            .append('\n');
      }
    }
    if (sb.isEmpty()) {
      for (String did : runtime) {
        sb.append("- did=").append(did).append(" (runtime)\n");
      }
    }
    return sb.toString();
  }

  private static List<String> intersectPreserveOrder(List<String> selected, Set<String> allowed) {
    Set<String> allowedUpper = new LinkedHashSet<>();
    for (String a : allowed) {
      allowedUpper.add(a.toUpperCase(Locale.ROOT));
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String s : selected) {
      String n = normalizeDid(s);
      if (allowedUpper.contains(n.toUpperCase(Locale.ROOT))) {
        out.add(n);
      }
    }
    return List.copyOf(out);
  }

  private static List<String> normalizeList(List<String> in) {
    if (in == null || in.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String s : in) {
      String n = normalizeDid(s);
      if (!n.isEmpty()) {
        out.add(n);
      }
    }
    return List.copyOf(out);
  }

  private static String normalizeDid(String did) {
    if (did == null) {
      return "";
    }
    String t = did.trim();
    if (t.isEmpty()) {
      return "";
    }
    if (t.regionMatches(true, 0, "0x", 0, 2)) {
      return "0x" + t.substring(2).toUpperCase(Locale.ROOT);
    }
    return t.toUpperCase(Locale.ROOT);
  }

  private static String stripFences(String content) {
    String t = content.trim();
    if (t.startsWith("```")) {
      int firstNl = t.indexOf('\n');
      if (firstNl > 0) {
        t = t.substring(firstNl + 1);
      }
      int fence = t.lastIndexOf("```");
      if (fence >= 0) {
        t = t.substring(0, fence);
      }
    }
    return t.trim();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  record Parsed(List<String> selectedDids, String reason) {}

  public record SelectionResult(
      List<String> selectedDids, String reason, boolean degraded, String degradeMessage) {
    public SelectionResult {
      selectedDids = List.copyOf(selectedDids == null ? List.of() : selectedDids);
      reason = reason == null ? "" : reason;
    }
  }
}
