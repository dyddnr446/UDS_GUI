package uds.write;

import java.util.Locale;
import uds.read.ReadDataRequestGenerator;
import uds.write.DidValueEncoder;

/** Builds Diagnostic WriteDataByIdentifier (0x2E) request hex. */
public final class WriteDataRequestGenerator {

  private WriteDataRequestGenerator() {}

  public static String toHex2E(String did, byte[] data) {
    int value = ReadDataRequestGenerator.parseDid(did);
    int hi = (value >> 8) & 0xFF;
    int lo = value & 0xFF;
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(Locale.ROOT, "2E %02X %02X", hi, lo));
    if (data != null && data.length > 0) {
      sb.append(' ').append(DidValueEncoder.toSpacedHex(data));
    }
    return sb.toString();
  }
}
