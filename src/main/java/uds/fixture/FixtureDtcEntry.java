package uds.fixture;

import java.util.Locale;
import java.util.Objects;

/** Seed / live DTC held by the offline ECU simulator. */
public record FixtureDtcEntry(int code, int statusByte, String nameKo) {

  public FixtureDtcEntry {
    nameKo = nameKo == null ? "" : nameKo;
  }

  public String codeHex() {
    return String.format(Locale.ROOT, "0x%06X", code & 0xFFFFFF);
  }

  public String statusByteHex() {
    return String.format(Locale.ROOT, "0x%02X", statusByte & 0xFF);
  }

  public FixtureDtcEntry copy() {
    return new FixtureDtcEntry(code, statusByte, nameKo);
  }

  public static FixtureDtcEntry of(int code, int statusByte, String nameKo) {
    Objects.requireNonNull(nameKo, "nameKo");
    return new FixtureDtcEntry(code, statusByte, nameKo);
  }
}
