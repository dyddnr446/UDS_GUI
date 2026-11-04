package uds.analysis.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uds.ai.advisor.AiAdviceResult;
import uds.analysis.evidence.AccessEvidence;
import uds.interpreter.model.DecodeResult;
import uds.read.finding.Finding;
import uds.read.finding.ReadDataResult;
import uds.write.parse.WriteResult;

/** In-memory Analysis Session snapshot (FR-20). schemaVersion fixed at 1. */
public final class AnalysisSessionStore {

  public static final int SCHEMA_VERSION = 1;

  private final String analysisSessionId;
  private String catalogVersion;
  private String profileId;
  private final List<DecodeEntry> decodeResults = new ArrayList<>();
  private final List<ReadEntry> readResults = new ArrayList<>();
  private final List<FindingEntry> findings = new ArrayList<>();
  private final List<WriteEntry> writeResults = new ArrayList<>();
  private final List<AccessEvidence> accessEvidences = new ArrayList<>();
  private Optional<AiAdviceResult> aiAdvice = Optional.empty();
  private final List<String> warnings = new ArrayList<>();
  private final EvidenceIdGenerator ids = new EvidenceIdGenerator();

  public AnalysisSessionStore(String analysisSessionId, String catalogVersion) {
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    this.catalogVersion = Objects.requireNonNull(catalogVersion, "catalogVersion");
  }

  public int schemaVersion() {
    return SCHEMA_VERSION;
  }

  public String analysisSessionId() {
    return analysisSessionId;
  }

  public String catalogVersion() {
    return catalogVersion;
  }

  public void setCatalogVersion(String catalogVersion) {
    this.catalogVersion = Objects.requireNonNull(catalogVersion, "catalogVersion");
  }

  public String profileId() {
    return profileId;
  }

  public void setProfileId(String profileId) {
    this.profileId = profileId;
  }

  public EvidenceIdGenerator ids() {
    return ids;
  }

  public List<DecodeEntry> decodeResults() {
    return List.copyOf(decodeResults);
  }

  public List<ReadEntry> readResults() {
    return List.copyOf(readResults);
  }

  public List<FindingEntry> findings() {
    return List.copyOf(findings);
  }

  public List<WriteEntry> writeResults() {
    return List.copyOf(writeResults);
  }

  public List<AccessEvidence> accessEvidences() {
    return List.copyOf(accessEvidences);
  }

  public Optional<AiAdviceResult> aiAdvice() {
    return aiAdvice;
  }

  public void setAiAdvice(AiAdviceResult advice) {
    this.aiAdvice = Optional.ofNullable(advice);
  }

  public List<String> warnings() {
    return List.copyOf(warnings);
  }

  public void addWarning(String warning) {
    if (warning != null && !warning.isBlank()) {
      warnings.add(warning);
    }
  }

  public void clearWarnings() {
    warnings.clear();
  }

  public String appendDecode(DecodeResult result) {
    String id = ids.log();
    decodeResults.add(new DecodeEntry(id, result));
    return id;
  }

  public String appendRead(ReadDataResult result) {
    String id = ids.read();
    readResults.add(new ReadEntry(id, result));
    String findingId = ids.finding();
    findings.add(new FindingEntry(findingId, result.finding(), id));
    return id;
  }

  public String appendWrite(WriteResult result) {
    String id = ids.write();
    writeResults.add(new WriteEntry(id, result));
    return id;
  }

  public void appendAccess(AccessEvidence evidence) {
    accessEvidences.add(evidence);
  }

  /** Restore path: keep persisted evidenceId. */
  public void forceAppendRead(String evidenceId, ReadDataResult result) {
    readResults.add(new ReadEntry(evidenceId, result));
  }

  public void forceAppendFinding(String evidenceId, Finding finding, String readEvidenceId) {
    findings.add(new FindingEntry(evidenceId, finding, readEvidenceId));
  }

  public void forceAppendWrite(String evidenceId, WriteResult result) {
    writeResults.add(new WriteEntry(evidenceId, result));
  }

  public record DecodeEntry(String evidenceId, DecodeResult result) {}

  public record ReadEntry(String evidenceId, ReadDataResult result) {}

  public record FindingEntry(String evidenceId, Finding finding, String readEvidenceId) {}

  public record WriteEntry(String evidenceId, WriteResult result) {}
}
