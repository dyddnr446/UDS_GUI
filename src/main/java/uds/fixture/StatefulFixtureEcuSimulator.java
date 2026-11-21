package uds.fixture;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import uds.fixture.Clock;
import uds.fixture.FixtureRunConfig;
import uds.fixture.AccessStep;
import uds.fixture.FixtureRepository;
import uds.fixture.ReadScenario;
import uds.fixture.WriteScenario;

/**
 * Session-scoped catalog-backed ECU simulator. Single instance per File Port session.
 *
 * <p>Seed placeholder expands to fixed AA BB CC DD. Positive SEND_KEY verifies HMAC via {@link
 * MessageDigest#isEqual}; negative scenario rows return the template as-is.
 */
public final class StatefulFixtureEcuSimulator {

  public static final byte[] DEMO_SEED = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};

  private final FixtureRepository repository;
  private final String password;
  private final Clock clock;

  private FixtureRunConfig runConfig;

  public StatefulFixtureEcuSimulator(
      FixtureRepository repository, String demoPassword, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.password = Objects.requireNonNull(demoPassword, "demoPassword");
    if (this.password.isBlank()) {
      throw new IllegalArgumentException("demoPassword blank");
    }
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public void setRunConfig(FixtureRunConfig config) {
    this.runConfig = Objects.requireNonNull(config, "config");
  }

  public FixtureRunConfig runConfig() {
    return runConfig;
  }

  public long nowMillis() {
    return clock.millis();
  }

  /**
   * Exchanges one request for the active scenario/flow and profile.
   *
   * @return response bytes, or empty array when no matching fixture row
   */
  public byte[] exchange(String profileId, byte[] request) {
    Objects.requireNonNull(profileId, "profileId");
    Objects.requireNonNull(request, "request");
    // TesterPresent is session keep-alive — not scenario-bound (suppress → empty).
    if (request.length >= 2 && (request[0] & 0xFF) == 0x3E) {
      if ((request[1] & 0x80) != 0) {
        return new byte[0];
      }
      return new byte[] {(byte) 0x7E, (byte) (request[1] & 0x7F)};
    }
    // Default session return (10 01) — always available; Extended (10 03) stays fixture-driven.
    if (request.length >= 2
        && (request[0] & 0xFF) == 0x10
        && (request[1] & 0x7F) == 0x01) {
      return HexFormat.of().parseHex("5001003201F4");
    }
    if (runConfig == null) {
      throw new IllegalStateException("FixtureRunConfig not set");
    }
    String id = runConfig.scenarioOrFlowId();

    ReadScenario read = repository.findRead(id, profileId);
    if (read != null) {
      return hexToBytes(read.responseHex());
    }
    WriteScenario write = repository.findWrite(id, profileId);
    if (write != null) {
      return hexToBytes(write.responseHex());
    }

    List<AccessStep> steps = repository.accessStepsForFlow(id);
    if (steps.isEmpty()) {
      return new byte[0];
    }
    AccessStep matched = findMatchingAccessStep(steps, profileId, request);
    if (matched == null) {
      return new byte[0];
    }
    return renderAccessResponse(matched, profileId, request);
  }

  private AccessStep findMatchingAccessStep(
      List<AccessStep> steps, String profileId, byte[] request) {
    for (AccessStep step : steps) {
      if (!step.profileId().equals(profileId)) {
        continue;
      }
      if (requestMatchesTemplate(request, step.requestTemplate())) {
        return step;
      }
    }
    return null;
  }

  private boolean requestMatchesTemplate(byte[] request, String template) {
    String t = template.trim();
    if (t.contains("<KEY>")) {
      // 27 02 <KEY> → SID 0x27, sub 0x02, then 4 key bytes
      if (request.length != 6) {
        return false;
      }
      return request[0] == 0x27 && request[1] == 0x02;
    }
    byte[] expected = hexToBytes(t);
    return Arrays.equals(request, expected);
  }

  private byte[] renderAccessResponse(AccessStep step, String profileId, byte[] request) {
    String kind = step.responseKind().toUpperCase(Locale.ROOT);
    String template = step.responseTemplate();

    if ("NEGATIVE".equals(kind)) {
      return hexToBytes(template.replace("<SEED>", "AA BB CC DD").replace("<KEY>", ""));
    }

    // POSITIVE
    if (template.contains("<SEED>")) {
      String rendered = template.replace("<SEED>", "AA BB CC DD");
      return hexToBytes(rendered);
    }
    if (step.requestTemplate().contains("<KEY>")) {
      byte[] expectedKey = DemoHmacKeyAlgorithm.computeKey(password, DEMO_SEED, profileId);
      byte[] providedKey = Arrays.copyOfRange(request, 2, 6);
      if (!MessageDigest.isEqual(expectedKey, providedKey)) {
        // Wrong key → NRC 0x35 InvalidKey (same as ACCESS-INVALID-KEY fixture).
        return new byte[] {0x7F, 0x27, 0x35};
      }
      return hexToBytes(template);
    }
    return hexToBytes(template);
  }

  private static byte[] hexToBytes(String hex) {
    String compact = FixtureRepository.normalizeHex(hex);
    if (compact.isEmpty()) {
      return new byte[0];
    }
    return HexFormat.of().parseHex(compact);
  }
}
