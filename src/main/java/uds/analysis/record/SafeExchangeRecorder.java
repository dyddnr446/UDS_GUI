package uds.analysis.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uds.exchange.api.ExchangeRequest;
import uds.exchange.api.ExchangeResponse;
import uds.analysis.evidence.AccessEvidence;

/**
 * Appends exchange and access-evidence JSONL under an analysis session directory. Never records
 * 0x27 raw seed/key or password bytes.
 */
public final class SafeExchangeRecorder {

  public static final int SCHEMA_VERSION = 1;

  private final Path sessionDir;
  private final String analysisSessionId;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Object lock = new Object();

  public SafeExchangeRecorder(Path sessionDir, String analysisSessionId) {
    this.sessionDir = Objects.requireNonNull(sessionDir, "sessionDir");
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    try {
      Files.createDirectories(sessionDir);
    } catch (IOException e) {
      throw new IllegalStateException("cannot create session dir: " + sessionDir, e);
    }
  }

  public Path sessionDir() {
    return sessionDir;
  }

  public String analysisSessionId() {
    return analysisSessionId;
  }

  public void recordExchange(ExchangeRequest request, ExchangeResponse response) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(response, "response");
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("schemaVersion", SCHEMA_VERSION);
    req.put("analysisSessionId", analysisSessionId);
    req.put("requestId", request.requestId());
    req.put("profile", request.profile());
    req.put("service", request.service());
    req.put("did", request.did());
    req.put("hex", request.hex());
    req.put("generation", request.generation());

    Map<String, Object> rsp = new LinkedHashMap<>();
    rsp.put("schemaVersion", SCHEMA_VERSION);
    rsp.put("analysisSessionId", analysisSessionId);
    rsp.put("responseId", response.responseId());
    rsp.put("requestId", response.requestId());
    rsp.put("kind", response.kind());
    rsp.put("hex", response.hex());
    rsp.put("fixtureId", response.fixtureId());
    rsp.put("payloadRedacted", response.payloadRedacted());

    synchronized (lock) {
      appendJsonLine(sessionDir.resolve("requests.jsonl"), req);
      appendJsonLine(sessionDir.resolve("responses.jsonl"), rsp);
    }
  }

  public void recordAccessEvidence(AccessEvidence evidence) {
    Objects.requireNonNull(evidence, "evidence");
    if (!evidence.payloadRedacted()) {
      throw new IllegalArgumentException("AccessEvidence.payloadRedacted must be true");
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("schemaVersion", SCHEMA_VERSION);
    row.put("analysisSessionId", analysisSessionId);
    row.put("evidenceId", evidence.evidenceId());
    row.put("operation", evidence.operation());
    row.put("responseKind", evidence.responseKind());
    row.put("nrc", evidence.nrc());
    row.put("resultingState", evidence.resultingState());
    row.put("payloadRedacted", true);
    synchronized (lock) {
      appendJsonLine(sessionDir.resolve("access-evidence.jsonl"), row);
    }
  }

  private void appendJsonLine(Path file, Map<String, Object> row) {
    try {
      ObjectNode node = mapper.valueToTree(row);
      String line = mapper.writeValueAsString(node);
      try (BufferedWriter writer =
          Files.newBufferedWriter(
              file,
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              StandardOpenOption.APPEND)) {
        writer.write(line);
        writer.newLine();
        writer.flush();
      }
    } catch (IOException e) {
      throw new IllegalStateException("JSONL append failed: " + file, e);
    }
  }
}
