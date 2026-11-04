package uds.write.parse;

import java.util.Objects;

/** Immutable WriteData outcome. */
public record WriteResult(
    String requestId,
    String responseId,
    String did,
    String inputValue,
    String encodedHex,
    String requestHex,
    String responseHex,
    WriteStatus status,
    String reason) {

  public WriteResult {
    Objects.requireNonNull(did, "did");
    Objects.requireNonNull(status, "status");
    if (inputValue == null) {
      inputValue = "";
    }
    if (encodedHex == null) {
      encodedHex = "";
    }
    if (requestHex == null) {
      requestHex = "";
    }
    if (responseHex == null) {
      responseHex = "";
    }
  }
}
