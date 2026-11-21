package uds.read;

import java.math.BigDecimal;
import java.util.Objects;
import uds.catalog.model.DidDefinition;
import uds.read.ParsedDidValue;

/** Maps ParsedDidValue + catalog range → Finding (CSV status 1:1). */
public final class FindingAnalyzer {

  public Finding analyze(ParsedDidValue parsed, DidDefinition def) {
    Objects.requireNonNull(parsed, "parsed");
    Objects.requireNonNull(def, "def");

    if (parsed.parseStatus() != null) {
      return new Finding(
          parsed.did(),
          parsed.displayValue() == null ? "" : parsed.displayValue(),
          parsed.parseStatus(),
          FindingDetailCode.NONE,
          "PARSE");
    }

    boolean hasMin = def.normalMin().isPresent();
    boolean hasMax = def.normalMax().isPresent();
    if (!hasMin || !hasMax) {
      return new Finding(
          parsed.did(),
          parsed.displayValue(),
          ReadStatus.NORMAL,
          FindingDetailCode.RANGE_NOT_APPLICABLE,
          "NO_RANGE");
    }

    BigDecimal value =
        parsed
            .physicalValue()
            .orElseThrow(() -> new IllegalStateException("physical value required for range check"));
    BigDecimal min = def.normalMin().get();
    BigDecimal max = def.normalMax().get();
    boolean inRange = value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    return new Finding(
        parsed.did(),
        parsed.displayValue(),
        inRange ? ReadStatus.NORMAL : ReadStatus.ABNORMAL,
        FindingDetailCode.NONE,
        "RANGE");
  }
}
