package uds.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uds.catalog.load.CatalogLoader;
import uds.catalog.load.UdsCatalog;
import uds.interpreter.DecodeKind;
import uds.interpreter.DecodeResult;
import uds.interpreter.SidDecoder;
import uds.interpreter.UdsInterpreter;
import uds.io.Frame;
import uds.io.HexNormalizer;
import uds.io.LogReadResult;
import uds.io.LogReader;

class UdsInterpreterTest {

  private static Path root;
  private static UdsCatalog catalog;
  private static UdsInterpreter interpreter;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeAll
  static void setUp() throws IOException {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    if (Files.isRegularFile(cwd.resolve("기준정보/uds_system_catalog.xlsx"))) {
      root = cwd;
    } else {
      root = cwd.resolve("..").normalize();
    }
    catalog = CatalogLoader.load(root.resolve("기준정보/uds_system_catalog.xlsx"));
    interpreter = new UdsInterpreter(catalog);
  }

  @Test
  void fr04_fr05_fr07_demoSessionFullDecode() throws IOException {
    LogReadResult log = LogReader.read(root.resolve("test/fixtures/demo_session.log"));
    List<DecodeResult> results = interpreter.parse(log);
    assertEquals(log.frames().size(), results.size());
    assertEquals(35, results.size());

    DecodeResult sessionReq = find(results, "1.000/REQ");
    assertEquals(DecodeKind.REQUEST, sessionReq.kind());
    assertEquals("0x10", sessionReq.sid());
    assertEquals("DiagnosticSessionControl", sessionReq.serviceName());
    assertEquals("extendedDiagnosticSession", sessionReq.subFunctionName());

    DecodeResult sessionPos = find(results, "1.010/RES");
    assertEquals(DecodeKind.POSITIVE, sessionPos.kind());
    assertEquals("0x50", sessionPos.sid());
    assertEquals("0x0032", sessionPos.fields().get("p2ServerMax"));
    assertEquals("0x01F4", sessionPos.fields().get("p2StarServerMax"));

    DecodeResult invalidKey = find(results, "1.310/RES");
    assertEquals(DecodeKind.NEGATIVE, invalidKey.kind());
    assertEquals("0x27", invalidKey.fields().get("requestSid"));
    assertEquals("0x35", invalidKey.nrcCode());

    DecodeResult faultRead = find(results, "2.400/REQ");
    assertEquals("0x22", faultRead.sid());
    assertEquals("0x1007", faultRead.fields().get("did"));

    DecodeResult didReq = find(results, "3.000/REQ");
    assertEquals("0x22", didReq.sid());
    assertEquals("0xF190", didReq.fields().get("did"));

    DecodeResult nrc = find(results, "3.110/RES");
    assertEquals(DecodeKind.NEGATIVE, nrc.kind());
    assertEquals("0x22", nrc.fields().get("requestSid"));
    assertEquals("0x31", nrc.nrcCode());
    assertEquals("requestOutOfRange", nrc.nrcName());

    DecodeResult dtcReq = find(results, "3.200/REQ");
    assertEquals("0x19", dtcReq.sid());
    assertEquals("0xFF", dtcReq.fields().get("statusMask"));

    DecodeResult clearReq = find(results, "3.300/REQ");
    assertEquals("0x14", clearReq.sid());
    assertEquals("0xFFFFFF", clearReq.fields().get("groupOfDTC"));
  }

  @Test
  void fr06_suppressMaskOnlyForHasSubfunction() {
    Frame tester = frame("t", "REQ", "3E 80");
    DecodeResult tp = interpreter.parse(tester);
    assertEquals("zeroSubFunction", tp.subFunctionName());
    assertEquals(Boolean.TRUE, tp.suppressPosRsp());
    assertEquals("0x00", tp.fields().get("zeroSubFunction"));

    Frame did = frame("t2", "REQ", "22 80 01");
    DecodeResult dr = interpreter.parse(did);
    assertEquals(DecodeKind.REQUEST, dr.kind());
    assertEquals("0x8001", dr.fields().get("did"));
    assertFalse("0x0001".equals(dr.fields().get("did")));
  }

  @Test
  void nfr20_dod16_securityPayloadRedacted() throws Exception {
    DecodeResult seed = interpreter.parse(frame("1.610", "RES", "67 01 DE AD BE EF"));
    DecodeResult key = interpreter.parse(frame("1.620", "REQ", "27 02 11 22 33 44"));

    for (DecodeResult r : List.of(seed, key)) {
      String text = r.toString() + MAPPER.writeValueAsString(r.toJsonMap());
      assertFalse(text.contains("DE AD BE EF"));
      assertFalse(text.contains("DEADBEEF"));
      assertFalse(text.contains("11 22 33 44"));
      assertFalse(text.contains("11223344"));
      assertTrue(text.contains(SidDecoder.REDACTED));
      assertFalse(r.fields().values().stream().anyMatch(v -> v != null && v.matches("(?i).*DE\\s*AD.*")));
    }
    assertEquals(SidDecoder.REDACTED, seed.fields().get("seed"));
    assertEquals(SidDecoder.REDACTED, key.fields().get("key"));
  }

  @Test
  void nfr01_deterministicReplay() throws IOException {
    LogReadResult log = LogReader.read(root.resolve("test/fixtures/demo_session.log"));
    List<DecodeResult> a = interpreter.parse(log);
    List<DecodeResult> b = interpreter.parse(log);
    assertEquals(a, b);
  }

  private static DecodeResult find(List<DecodeResult> results, String frameRef) {
    return results.stream()
        .filter(r -> frameRef.equals(r.frameRef()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing " + frameRef));
  }

  private static Frame frame(String ts, String dir, String hex) {
    return new Frame(ts, dir, hex, HexNormalizer.normalize(hex).orElseThrow());
  }
}
