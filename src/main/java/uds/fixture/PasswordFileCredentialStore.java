package uds.fixture;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a single-line UTF-8 demo password. Only trailing CR/LF are stripped (FR-25).
 */
public final class PasswordFileCredentialStore {

  private static final int MAX_UTF8_BYTES = 128;
  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private PasswordFileCredentialStore() {}

  public static String load(Path path) {
    byte[] raw;
    try {
      raw = Files.readAllBytes(path);
    } catch (IOException e) {
      throw new CredentialConfigException("credential file missing: " + path);
    }
    if (startsWithBom(raw)) {
      throw new CredentialConfigException("UTF-8 BOM not allowed");
    }
    try {
      decodeStrictUtf8(raw);
    } catch (CharacterCodingException e) {
      throw new CredentialConfigException("invalid UTF-8");
    }
    byte[] stripped = stripTrailingNewlines(raw);
    if (stripped.length == 0) {
      throw new CredentialConfigException("empty credential");
    }
    if (stripped.length > MAX_UTF8_BYTES) {
      throw new CredentialConfigException("credential exceeds 128 UTF-8 bytes");
    }
    for (byte b : stripped) {
      if (b == '\n' || b == '\r') {
        throw new CredentialConfigException("multiple lines not allowed");
      }
    }
    return new String(stripped, StandardCharsets.UTF_8);
  }

  private static boolean startsWithBom(byte[] raw) {
    return raw.length >= 3
        && raw[0] == UTF8_BOM[0]
        && raw[1] == UTF8_BOM[1]
        && raw[2] == UTF8_BOM[2];
  }

  private static byte[] stripTrailingNewlines(byte[] raw) {
    int end = raw.length;
    while (end >= 2 && raw[end - 2] == '\r' && raw[end - 1] == '\n') {
      end -= 2;
    }
    while (end >= 1 && (raw[end - 1] == '\n' || raw[end - 1] == '\r')) {
      end -= 1;
    }
    byte[] out = new byte[end];
    System.arraycopy(raw, 0, out, 0, end);
    return out;
  }

  private static void decodeStrictUtf8(byte[] raw) throws CharacterCodingException {
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPORT);
    decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
    decoder.decode(java.nio.ByteBuffer.wrap(raw));
  }
}
