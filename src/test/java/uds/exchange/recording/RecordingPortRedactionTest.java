package uds.exchange.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.analysis.evidence.AccessEvidence;
import uds.analysis.record.SafeExchangeRecorder;
import uds.fixture.sim.DemoHmacKeyAlgorithm;
import uds.fixture.port.FileDiagnosticExchangePort;
import uds.fixture.repo.FixtureRepository;
import uds.fixture.config.FixtureRunConfig;
import uds.fixture.sim.StatefulFixtureEcuSimulator;
import uds.fixture.config.SystemClock;
import uds.exchange.api.ExchangeRequest;
import uds.exchange.api.ExchangeResponse;
import uds.exchange.policy.ExchangeCoordinator;
import uds.exchange.policy.ExchangePolicy;

class RecordingPortRedactionTest {

  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Path SAMPLES = ROOT.resolve("samples");
  private static final Path CONFIG = ROOT.resolve("config");
  private static final String PROFILE = "FARDRIVER_ND72530";
  private static final String SESSION_ID = "test-session-2b";

  @TempDir Path tempDir;

  private FileDiagnosticExchangePort filePort;
  private SafeExchangeRecorder recorder;
  private ExchangeCoordinator coordinator;
  private Path sessionDir;

  @BeforeEach
  void setUp() {
    FixtureRepository repository =
        new FixtureRepository(
            SAMPLES.resolve("access_scenarios.csv"),
            SAMPLES.resolve("read_data_scenarios.csv"),
            SAMPLES.resolve("write_data_scenarios.csv"));
    StatefulFixtureEcuSimulator simulator =
        new StatefulFixtureEcuSimulator(
            repository, CONFIG.resolve("demo_password.txt"), new SystemClock());
    filePort = new FileDiagnosticExchangePort(simulator);
    sessionDir = tempDir.resolve("outputs").resolve(SESSION_ID);
    recorder = new SafeExchangeRecorder(sessionDir, SESSION_ID);
    RecordingDiagnosticExchangePort recording =
        new RecordingDiagnosticExchangePort(filePort, recorder);
    coordinator = new ExchangeCoordinator(recording, new ExchangePolicy());
  }

  @Test
  void fr28_nfr20_dod16_accessThenReadLeavesNoSecretsInJsonl() throws Exception {
    filePort.setFixtureRunConfig(new FixtureRunConfig("ACCESS-ND-SUCCESS"));

    ExchangeResponse sessionRsp =
        coordinator.exchange(req("req-10", "0x10", null, "10 03"));
    assertEquals("POSITIVE", sessionRsp.kind());
    assertTrue(sessionRsp.hex().toUpperCase(Locale.ROOT).contains("50 03"));

    ExchangeResponse seedRsp =
        coordinator.exchange(req("req-27-01", "0x27", null, "27 01"));
    assertTrue(seedRsp.payloadRedacted());
    assertTrue(seedRsp.hex().contains("<REDACTED>"));
    assertFalse(seedRsp.hex().toUpperCase(Locale.ROOT).contains("AABBCCDD"));
    assertFalse(seedRsp.hex().toUpperCase(Locale.ROOT).contains("AA BB CC DD"));

    byte[] key =
        DemoHmacKeyAlgorithm.computeKey(
            "uds-demo-2026", StatefulFixtureEcuSimulator.DEMO_SEED, PROFILE);
    assertEquals("D3AC3F47", HexFormat.of().formatHex(key).toUpperCase(Locale.ROOT));
    String keyHex =
        "27 02 "
            + String.format(
                Locale.ROOT,
                "%02X %02X %02X %02X",
                key[0] & 0xFF,
                key[1] & 0xFF,
                key[2] & 0xFF,
                key[3] & 0xFF);
    ExchangeResponse keyRsp = coordinator.exchange(req("req-27-02", "0x27", null, keyHex));
    assertTrue(keyRsp.payloadRedacted());
    assertEquals("67 02", keyRsp.hex());

    filePort.setFixtureRunConfig(new FixtureRunConfig("RD-ND-001"));
    ExchangeResponse readRsp =
        coordinator.exchange(req("req-22", "0x22", "0x1001", "22 10 01"));
    assertEquals("POSITIVE", readRsp.kind());
    assertTrue(readRsp.hex().toUpperCase(Locale.ROOT).startsWith("62 10 01"));

    String allJsonl = readAllSessionText();
    String compact = allJsonl.replace(" ", "").toUpperCase(Locale.ROOT);

    // FR-28: session + read raw recorded
    assertTrue(compact.contains("1003") || allJsonl.contains("10 03"));
    assertTrue(compact.contains("5003") || allJsonl.contains("50 03"));
    assertTrue(compact.contains("221001") || allJsonl.contains("22 10 01"));
    assertTrue(compact.contains("621001") || allJsonl.contains("62 10 01"));

    // NFR-20 / DOD-16: secrets absent
    assertFalse(compact.contains("AABBCCDD"), "seed must not appear in JSONL");
    assertFalse(compact.contains("D3AC3F47"), "key must not appear in JSONL");
    assertFalse(allJsonl.contains("uds-demo-2026"), "password must not appear in JSONL");
  }

  @Test
  void recordAccessEvidence_persistsResultingStateAndRedactedFlag() throws Exception {
    AccessEvidence evidence =
        new AccessEvidence(
            "ACCESS:nd-success-1",
            SESSION_ID,
            "SEND_KEY",
            "POSITIVE",
            null,
            "EXTENDED_UNLOCKED",
            true);
    recorder.recordAccessEvidence(evidence);

    Path evidenceFile = sessionDir.resolve("access-evidence.jsonl");
    assertTrue(Files.exists(evidenceFile));
    String line = Files.readString(evidenceFile, StandardCharsets.UTF_8);
    assertTrue(line.contains("\"payloadRedacted\":true") || line.contains("\"payloadRedacted\": true"));
    assertTrue(line.contains("EXTENDED_UNLOCKED"));
    assertTrue(line.contains("ACCESS:nd-success-1"));
    assertFalse(line.contains("AA BB CC DD"));
    assertFalse(line.contains("D3 AC 3F 47"));
    assertFalse(line.contains("uds-demo-2026"));
  }

  private ExchangeRequest req(String id, String service, String did, String hex) {
    return new ExchangeRequest(SESSION_ID, id, PROFILE, service, did, hex, 1L);
  }

  private String readAllSessionText() throws Exception {
    StringBuilder sb = new StringBuilder();
    try (Stream<Path> paths = Files.list(sessionDir)) {
      for (Path p : paths.toList()) {
        if (Files.isRegularFile(p)) {
          sb.append(Files.readString(p, StandardCharsets.UTF_8));
        }
      }
    }
    return sb.toString();
  }
}
