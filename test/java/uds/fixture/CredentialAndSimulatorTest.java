package uds.fixture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uds.catalog.load.CatalogLoader;
import uds.fixture.FixtureRunConfig;
import uds.fixture.SystemClock;
import uds.fixture.FixtureRepository;
import uds.fixture.DemoHmacKeyAlgorithm;
import uds.fixture.StatefulFixtureEcuSimulator;
import uds.gui.app.AppConfig;

class CredentialAndSimulatorTest {

  private static final Path ROOT =
      Path.of("").toAbsolutePath().normalize();
  private static final Path CATALOG = ROOT.resolve("기준정보").resolve("uds_system_catalog.xlsx");
  private static final Path FIXTURE = ROOT.resolve("test").resolve("fixtures").resolve("uds_fixture_scenarios.xlsx");

  private FixtureRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = FixtureRepository.load(FIXTURE);
  }

  @Test
  void fr25_demoPasswordDefaultIsThirteenBytes() {
    String password = AppConfig.DEFAULT_DEMO_PASSWORD;
    assertEquals("uds-demo-2026", password);
    assertEquals(13, password.getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  void fr25_bomRejected(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("bom.txt");
    byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] body = "uds-demo-2026".getBytes(StandardCharsets.UTF_8);
    byte[] all = new byte[bom.length + body.length];
    System.arraycopy(bom, 0, all, 0, bom.length);
    System.arraycopy(body, 0, all, bom.length, body.length);
    Files.write(file, all);
    assertThrows(CredentialConfigException.class, () -> PasswordFileCredentialStore.load(file));
  }

  @Test
  void fr25_emptyRejected(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("empty.txt");
    Files.writeString(file, "\n", StandardCharsets.UTF_8);
    assertThrows(CredentialConfigException.class, () -> PasswordFileCredentialStore.load(file));
  }

  @Test
  void fr25_twoLinesRejected(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("two.txt");
    Files.writeString(file, "line1\nline2\n", StandardCharsets.UTF_8);
    assertThrows(CredentialConfigException.class, () -> PasswordFileCredentialStore.load(file));
  }

  @Test
  void fr25_over128BytesRejected(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("long.txt");
    Files.write(file, "a".repeat(129).getBytes(StandardCharsets.UTF_8));
    assertThrows(CredentialConfigException.class, () -> PasswordFileCredentialStore.load(file));
  }

  @Test
  void nfr13_catalogScenarioRowCounts() {
    assertEquals(14, repository.accessSteps().size());
    assertEquals(20, repository.readScenarios().size());
    assertEquals(16, repository.writeScenarios().size());
  }

  @Test
  void nfr13_accessNdSuccessSequence() {
    StatefulFixtureEcuSimulator sim =
        new StatefulFixtureEcuSimulator(
            repository, AppConfig.DEFAULT_DEMO_PASSWORD, new SystemClock());
    sim.setRunConfig(new FixtureRunConfig("ACCESS-ND-SUCCESS"));
    String profile = "FARDRIVER_ND72530";

    byte[] sessionRsp = sim.exchange(profile, hex("10 03"));
    assertArrayEquals(hex("50 03 00 32 01 F4"), sessionRsp);

    byte[] seedRsp = sim.exchange(profile, hex("27 01"));
    assertArrayEquals(hex("67 01 AA BB CC DD"), seedRsp);

    byte[] key = DemoHmacKeyAlgorithm.computeKey("uds-demo-2026", StatefulFixtureEcuSimulator.DEMO_SEED, profile);
    assertArrayEquals(hex("D3 AC 3F 47"), key);

    byte[] keyReq = new byte[6];
    keyReq[0] = 0x27;
    keyReq[1] = 0x02;
    System.arraycopy(key, 0, keyReq, 2, 4);
    byte[] keyRsp = sim.exchange(profile, keyReq);
    assertArrayEquals(hex("67 02"), keyRsp);
  }

  private static byte[] hex(String spaced) {
    return HexFormat.of().parseHex(spaced.replace(" ", ""));
  }
}
