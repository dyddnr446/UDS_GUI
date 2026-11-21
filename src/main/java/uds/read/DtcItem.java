package uds.read;

import java.util.List;
import java.util.Objects;

/** One decoded DTC record from a 0x19 positive response. */
public record DtcItem(
    String codeHex, String name, String statusByteHex, List<String> flags) {

  public DtcItem {
    Objects.requireNonNull(codeHex, "codeHex");
    name = name == null ? "" : name;
    statusByteHex = statusByteHex == null ? "" : statusByteHex;
    flags = flags == null ? List.of() : List.copyOf(flags);
  }

  public String flagsText() {
    return flags.isEmpty() ? "—" : String.join(", ", flags);
  }
}
