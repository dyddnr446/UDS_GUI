package uds.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessState;
import uds.analysis.AccessEvidence;
import uds.ai.AiAdviceResult;
import uds.analysis.AnalysisSessionStore;
import uds.analysis.PersistenceError;
import uds.read.Finding;
import uds.read.FindingDetailCode;
import uds.read.ReadDataResult;
import uds.read.ReadStatus;
import uds.write.WriteResult;
import uds.write.WriteStatus;

/** Saves/restores analysis.json + analysis-session.json with atomic replace (FR-20, NFR-21). */
public final class AnalysisReportWriter {

  private final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  public void save(AnalysisSessionStore store, Path sessionDir) {
    Objects.requireNonNull(store, "store");
    Objects.requireNonNull(sessionDir, "sessionDir");
    try {
      Files.createDirectories(sessionDir);
      String commitId = "commit-" + UUID.randomUUID();

      ObjectNode analysis = mapper.createObjectNode();
      analysis.put("schemaVersion", store.schemaVersion());
      analysis.put("analysisSessionId", store.analysisSessionId());
      analysis.put("catalogVersion", store.catalogVersion());
      analysis.put("profileId", store.profileId());

      ArrayNode reads = analysis.putArray("reads");
      for (AnalysisSessionStore.ReadEntry e : store.readResults()) {
        ObjectNode n = reads.addObject();
        n.put("evidenceId", e.evidenceId());
        n.set("result", mapper.valueToTree(e.result()));
      }
      ArrayNode findings = analysis.putArray("findings");
      for (AnalysisSessionStore.FindingEntry e : store.findings()) {
        ObjectNode n = findings.addObject();
        n.put("evidenceId", e.evidenceId());
        n.put("readEvidenceId", e.readEvidenceId());
        n.set("finding", mapper.valueToTree(e.finding()));
      }
      ArrayNode writes = analysis.putArray("writes");
      for (AnalysisSessionStore.WriteEntry e : store.writeResults()) {
        ObjectNode n = writes.addObject();
        n.put("evidenceId", e.evidenceId());
        n.set("result", mapper.valueToTree(e.result()));
      }
      ArrayNode access = analysis.putArray("accessEvidences");
      for (AccessEvidence e : store.accessEvidences()) {
        access.add(mapper.valueToTree(e));
      }
      ArrayNode decodes = analysis.putArray("decodes");
      for (AnalysisSessionStore.DecodeEntry e : store.decodeResults()) {
        ObjectNode n = decodes.addObject();
        n.put("evidenceId", e.evidenceId());
        n.set("result", mapper.valueToTree(e.result().toJsonMap()));
      }
      if (store.aiAdvice().isPresent()) {
        analysis.set("aiAdvice", mapper.valueToTree(store.aiAdvice().get()));
      } else {
        analysis.putNull("aiAdvice");
      }

      ObjectNode sessionMeta = mapper.createObjectNode();
      sessionMeta.put("schemaVersion", AnalysisSessionStore.SCHEMA_VERSION);
      sessionMeta.put("analysisSessionId", store.analysisSessionId());
      sessionMeta.put("catalogVersion", store.catalogVersion());
      sessionMeta.put("profileId", store.profileId());
      sessionMeta.put("commitId", commitId);
      sessionMeta.put("analysisFile", "analysis.json");

      atomicWriteJson(sessionDir.resolve("analysis.json"), analysis);
      atomicWriteJson(sessionDir.resolve("analysis-session.json"), sessionMeta);
    } catch (PersistenceError e) {
      throw e;
    } catch (Exception e) {
      throw new PersistenceError("save failed; in-memory store retained", e);
    }
  }

  public RestoreResult restore(
      Path sessionDir, String runtimeCatalogVersion, ActiveDiagnosticContext context) {
    Objects.requireNonNull(sessionDir, "sessionDir");
    Objects.requireNonNull(runtimeCatalogVersion, "runtimeCatalogVersion");
    Objects.requireNonNull(context, "context");
    List<String> warnings = new ArrayList<>();
    try {
      Path sessionFile = sessionDir.resolve("analysis-session.json");
      Path analysisFile = sessionDir.resolve("analysis.json");
      if (!Files.exists(sessionFile)) {
        warnings.add("missing analysis-session.json");
        forceDefaultLocked(context);
        return new RestoreResult(null, warnings);
      }
      JsonNode meta = mapper.readTree(Files.readString(sessionFile, StandardCharsets.UTF_8));
      int schema = meta.path("schemaVersion").asInt(-1);
      if (schema != AnalysisSessionStore.SCHEMA_VERSION) {
        warnings.add("schemaVersion mismatch: file=" + schema);
      }
      String sessionId = text(meta, "analysisSessionId", "unknown");
      String catalogVersion = text(meta, "catalogVersion", "");
      String profileId = text(meta, "profileId", null);
      if (!runtimeCatalogVersion.equalsIgnoreCase(catalogVersion)) {
        warnings.add(
            "catalogVersion mismatch: file="
                + catalogVersion
                + " runtime="
                + runtimeCatalogVersion);
      }

      AnalysisSessionStore store = new AnalysisSessionStore(sessionId, catalogVersion);
      store.setProfileId(profileId);
      for (String w : warnings) {
        store.addWarning(w);
      }

      if (Files.exists(analysisFile)) {
        JsonNode analysis = mapper.readTree(Files.readString(analysisFile, StandardCharsets.UTF_8));
        loadAnalysisBody(store, analysis, warnings);
      } else {
        warnings.add("missing analysis.json; partial restore");
        store.addWarning("missing analysis.json; partial restore");
      }

      if (profileId != null && !profileId.isBlank()) {
        context.selectProfile(profileId, runtimeCatalogVersion);
      } else {
        forceDefaultLocked(context);
      }
      context.primeState(DiagnosticAccessState.DEFAULT_LOCKED);
      return new RestoreResult(store, List.copyOf(warnings));
    } catch (Exception e) {
      warnings.add("restore failed: " + e.getMessage());
      forceDefaultLocked(context);
      return new RestoreResult(null, warnings);
    }
  }

  private void loadAnalysisBody(
      AnalysisSessionStore store, JsonNode analysis, List<String> warnings) {
    for (JsonNode n : analysis.path("reads")) {
      try {
        store.forceAppendRead(text(n, "evidenceId", store.ids().read()), readResultFrom(n.path("result")));
      } catch (Exception e) {
        warnings.add("skip read row: " + e.getMessage());
      }
    }
    for (JsonNode n : analysis.path("findings")) {
      try {
        store.forceAppendFinding(
            text(n, "evidenceId", store.ids().finding()),
            findingFrom(n.path("finding")),
            text(n, "readEvidenceId", ""));
      } catch (Exception e) {
        warnings.add("skip finding row: " + e.getMessage());
      }
    }
    for (JsonNode n : analysis.path("writes")) {
      try {
        store.forceAppendWrite(
            text(n, "evidenceId", store.ids().write()), writeResultFrom(n.path("result")));
      } catch (Exception e) {
        warnings.add("skip write row: " + e.getMessage());
      }
    }
    for (JsonNode n : analysis.path("accessEvidences")) {
      try {
        store.appendAccess(
            new AccessEvidence(
                text(n, "evidenceId", "ACCESS:restored"),
                text(n, "analysisSessionId", store.analysisSessionId()),
                text(n, "operation", ""),
                text(n, "responseKind", ""),
                n.hasNonNull("nrc") ? n.get("nrc").asText() : null,
                text(n, "resultingState", "DEFAULT_LOCKED"),
                n.path("payloadRedacted").asBoolean(true)));
      } catch (Exception e) {
        warnings.add("skip access row: " + e.getMessage());
      }
    }
    JsonNode ai = analysis.path("aiAdvice");
    if (!ai.isMissingNode() && !ai.isNull()) {
      List<String> evid = new ArrayList<>();
      ai.path("evidenceIds").forEach(x -> evid.add(x.asText()));
      List<String> warns = new ArrayList<>();
      ai.path("warnings").forEach(x -> warns.add(x.asText()));
      List<AiAdviceResult.ChecklistItem> checklist = new ArrayList<>();
      for (JsonNode item : ai.path("checklist")) {
        List<String> itemIds = new ArrayList<>();
        item.path("evidenceIds").forEach(x -> itemIds.add(x.asText()));
        checklist.add(new AiAdviceResult.ChecklistItem(text(item, "text", ""), itemIds));
      }
      store.setAiAdvice(
          new AiAdviceResult(text(ai, "summary", ""), checklist, evid, warns));
    }
  }

  private void atomicWriteJson(Path target, ObjectNode value) throws Exception {
    Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
    Files.write(tmp, mapper.writeValueAsBytes(value));
    try {
      Files.move(
          tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void forceDefaultLocked(ActiveDiagnosticContext context) {
    context.reset();
    context.primeState(DiagnosticAccessState.DEFAULT_LOCKED);
  }

  private static String text(JsonNode n, String field, String defaultValue) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return defaultValue;
    }
    return v.asText(defaultValue);
  }

  private static ReadDataResult readResultFrom(JsonNode n) {
    Finding finding = findingFrom(n.path("finding"));
    return new ReadDataResult(
        text(n, "requestId", null),
        text(n, "responseId", null),
        text(n, "did", ""),
        text(n, "requestHex", null),
        text(n, "responseHex", null),
        text(n, "displayValue", ""),
        ReadStatus.valueOf(text(n, "status", "ACCESS_DENIED")),
        FindingDetailCode.valueOf(text(n, "detailCode", "NONE")),
        n.hasNonNull("nrcCode") ? n.get("nrcCode").asText() : null,
        finding);
  }

  private static Finding findingFrom(JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull()) {
      return new Finding("", "", ReadStatus.ACCESS_DENIED, FindingDetailCode.NONE, "RESTORE");
    }
    return new Finding(
        text(n, "did", ""),
        text(n, "displayValue", ""),
        ReadStatus.valueOf(text(n, "status", "ACCESS_DENIED")),
        FindingDetailCode.valueOf(text(n, "detailCode", "NONE")),
        text(n, "evidence", ""));
  }

  private static WriteResult writeResultFrom(JsonNode n) {
    return new WriteResult(
        text(n, "requestId", null),
        text(n, "responseId", null),
        text(n, "did", ""),
        text(n, "inputValue", ""),
        text(n, "encodedHex", ""),
        text(n, "requestHex", ""),
        text(n, "responseHex", ""),
        WriteStatus.valueOf(text(n, "status", "ACCESS_DENIED")),
        text(n, "reason", ""));
  }

  public record RestoreResult(AnalysisSessionStore store, List<String> warnings) {
    public RestoreResult {
      warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
  }
}
