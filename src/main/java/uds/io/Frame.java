package uds.io;

import java.util.Arrays;
import java.util.Objects;

/** Immutable UDS log frame (FR-01). */
public record Frame(String ts, String direction, String hexRaw, byte[] bytes) {

  public Frame {
    Objects.requireNonNull(ts, "ts");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(hexRaw, "hexRaw");
    bytes = bytes == null ? new byte[0] : bytes.clone();
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Frame other)) {
      return false;
    }
    return ts.equals(other.ts)
        && direction.equals(other.direction)
        && hexRaw.equals(other.hexRaw)
        && Arrays.equals(bytes, other.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ts, direction, hexRaw, Arrays.hashCode(bytes));
  }
}
