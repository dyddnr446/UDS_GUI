package uds.fixture;

import uds.access.DemoSeedKeyAlgorithm;

/** Thin delegate to {@link DemoSeedKeyAlgorithm} for fixture-layer callers. */
public final class DemoHmacKeyAlgorithm {

  private DemoHmacKeyAlgorithm() {}

  public static byte[] computeKey(String password, byte[] seed, String profileId) {
    return DemoSeedKeyAlgorithm.computeKey(password, seed, profileId);
  }
}
