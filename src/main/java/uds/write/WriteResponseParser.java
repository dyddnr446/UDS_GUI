package uds.write;

import java.util.Locale;
import uds.read.ReadDataRequestGenerator;

/** Parses 0x6E / 7F 2E responses into WriteStatus. */
public final class WriteResponseParser {

  public WriteStatus parse(String responseHex, String expectedDid) {
    byte[] bytes = parseHex(responseHex);
    if (bytes.length == 0) {
      return WriteStatus.PARSE_ERROR_LENGTH;
    }
    int sid = bytes[0] & 0xFF;
    if (sid == 0x7F) {
      return WriteStatus.REJECTED;
    }
    if (sid != 0x6E) {
      return WriteStatus.PARSE_ERROR_LENGTH;
    }
    if (bytes.length < 3) {
      return WriteStatus.PARSE_ERROR_LENGTH;
    }
    int echoDid = ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
    int expected = ReadDataRequestGenerator.parseDid(expectedDid);
    if (echoDid != expected) {
      return WriteStatus.PARSE_ERROR_DID_MISMATCH;
    }
    return WriteStatus.ACCEPTED;
  }

  private static byte[] parseHex(String hex) {
    if (hex == null || hex.isBlank()) {
      return new byte[0];
    }
    String compact = hex.replace(" ", "").replace("_", "").toUpperCase(Locale.ROOT);
    if (compact.isEmpty() || compact.length() % 2 != 0) {
      return new byte[0];
    }
    byte[] out = new byte[compact.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
