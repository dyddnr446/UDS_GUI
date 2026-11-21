package uds.access;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Appendix C demo key: HMAC-SHA256(password, seed || 0x00 || UTF-8(profileId)) first 4 bytes.
 * Demo seed is fixed AA BB CC DD (Recording Port never returns raw seed).
 */
public final class DemoSeedKeyAlgorithm {

  public static final byte[] DEMO_SEED = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};

  private DemoSeedKeyAlgorithm() {}

  public static byte[] computeKey(char[] password, byte[] seed, String profileId) {
    if (password == null || password.length == 0) {
      throw new IllegalArgumentException("password must not be empty");
    }
    if (seed == null || seed.length != 4) {
      throw new IllegalArgumentException("seed must be 4 bytes");
    }
    byte[] passwordBytes = new String(password).getBytes(StandardCharsets.UTF_8);
    try {
      return computeKeyBytes(passwordBytes, seed, profileId);
    } finally {
      Arrays.fill(passwordBytes, (byte) 0);
    }
  }

  public static byte[] computeKey(String password, byte[] seed, String profileId) {
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("password must not be empty");
    }
    return computeKeyBytes(password.getBytes(StandardCharsets.UTF_8), seed, profileId);
  }

  private static byte[] computeKeyBytes(byte[] passwordBytes, byte[] seed, String profileId) {
    if (passwordBytes == null || passwordBytes.length == 0) {
      throw new IllegalArgumentException("password must not be empty");
    }
    if (profileId == null || profileId.isBlank()) {
      throw new IllegalArgumentException("profileId must not be blank");
    }
    byte[] profileBytes = profileId.getBytes(StandardCharsets.UTF_8);
    byte[] message = new byte[seed.length + 1 + profileBytes.length];
    System.arraycopy(seed, 0, message, 0, seed.length);
    message[seed.length] = 0x00;
    System.arraycopy(profileBytes, 0, message, seed.length + 1, profileBytes.length);
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));
      byte[] full = mac.doFinal(message);
      return Arrays.copyOf(full, 4);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }
}
