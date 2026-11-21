package uds.analysis;

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
import uds.analysis.AccessEvidence;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;

/**
 * JSONL under {@code output/.session/}; user-facing live CSV under {@code output/live_session.log}.
 * Never records 0x27 raw seed/key or password bytes.
 */
public final class SafeExchangeRecorder {

  public static final int SCHEMA_VERSION = 1;
  public static final String SESSION_SUBDIR = ".session";

  private final Path outputsRoot;
  private final Path sessionDir;
  private final String analysisSessionId;
  private final LiveSessionLog liveLog;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Object lock = new Object();

  /**
   * @param outputsRoot project {@code output/} (or test temp out dir)
   * @param analysisSessionId logical session id (metadata only; files go to {@code .session/})
   */
  public SafeExchangeRecorder(Path outputsRoot, String analysisSessionId) {
    this.outputsRoot = Objects.requireNonNull(outputsRoot, "outputsRoot").toAbsolutePath().normalize();
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
    this.sessionDir = this.outputsRoot.resolve(SESSION_SUBDIR);
    try {
      Files.createDirectories(this.outputsRoot);
      Files.createDirectories(this.sessionDir);
    } catch (IOException e) {
      throw new IllegalStateException("cannot create output dirs: " + this.outputsRoot, e);
    }
    this.liveLog = new LiveSessionLog(this.outputsRoot);
  }

  public Path outputsRoot() {
    return outputsRoot;
  }

  public Path sessionDir() {
    return sessionDir;
  }

  public String analysisSessionId() {
    return analysisSessionId;
  }

  public LiveSessionLog liveLog() {
    return liveLog;
  }

  public Path liveSessionLogPath() {
    return liveLog.path();
  }

  public void clearLiveSessionLog() {
    liveLog.clear();
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
      liveLog.appendPair(request.hex(), response.hex());
    }
  }

  /** Interpreter-only frame pair (masked 0x27); never written to JSONL. */
  public void recordLiveMaskedPair(String requestHex, String responseHex) {
    liveLog.appendPair(requestHex, responseHex);
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
