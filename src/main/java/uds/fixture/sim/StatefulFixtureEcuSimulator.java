package uds.fixture.sim;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import uds.fixture.config.Clock;
import uds.fixture.config.FixtureRunConfig;
import uds.fixture.credential.PasswordFileCredentialStore;
import uds.fixture.repo.AccessStep;
import uds.fixture.repo.FixtureRepository;
import uds.fixture.repo.ReadScenario;
import uds.fixture.repo.WriteScenario;

/**
 * Session-scoped CSV-backed ECU simulator. Single instance per File Port session.
 *
 * <p>Seed placeholder expands to fixed AA BB CC DD. Positive SEND_KEY verifies HMAC via {@link
 * MessageDigest#isEqual}; negative CSV rows return the template as-is.
 */
public final class StatefulFixtureEcuSimulator {

  public static final byte[] DEMO_SEED = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};

  private final FixtureRepository repository;
  private final String password;
  private final Clock clock;

  private FixtureRunConfig runConfig;

  public StatefulFixtureEcuSimulator(
      FixtureRepository repository, Path passwordFile, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.password = PasswordFileCredentialStore.load(passwordFile);
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
        // Wrong key with positive CSV row: still no free-form invent; return empty so caller sees miss.
        // ACCESS-ND-SUCCESS positive path uses correct key in tests.
        return new byte[0];
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
