package uds.write.validate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import uds.catalog.model.DidDefinition;
import uds.write.parse.WriteStatus;

/** Type/range gate before Port (FR-21). */
public final class WriteValueValidator {

  public record ValidationResult(Optional<WriteStatus> error, Optional<BigDecimal> value) {
    public static ValidationResult ok(BigDecimal value) {
      return new ValidationResult(Optional.empty(), Optional.of(value));
    }

    public static ValidationResult fail(WriteStatus status) {
      return new ValidationResult(Optional.of(status), Optional.empty());
    }

    public boolean failed() {
      return error.isPresent();
    }
  }

  public ValidationResult validate(DidDefinition def, String inputText) {
    Objects.requireNonNull(def, "def");
    if (inputText == null || inputText.isBlank()) {
      return ValidationResult.fail(WriteStatus.VALIDATION_ERROR_TYPE);
    }
    BigDecimal value;
    try {
      value = new BigDecimal(inputText.trim());
    } catch (NumberFormatException e) {
      return ValidationResult.fail(WriteStatus.VALIDATION_ERROR_TYPE);
    }
    if (def.writeMin().isPresent() && value.compareTo(def.writeMin().get()) < 0) {
      return ValidationResult.fail(WriteStatus.VALIDATION_ERROR_RANGE);
    }
    if (def.writeMax().isPresent() && value.compareTo(def.writeMax().get()) > 0) {
      return ValidationResult.fail(WriteStatus.VALIDATION_ERROR_RANGE);
    }
    // Also reject non-integer logical values when scale=1 and UINT/INT expect whole numbers for UINT8 flags?
    // CSV uses integers / decimals that divide cleanly — leave as-is.
    return ValidationResult.ok(value);
  }
}
