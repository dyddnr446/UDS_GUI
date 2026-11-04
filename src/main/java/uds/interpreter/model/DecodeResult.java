package uds.interpreter.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Deterministic UDS decode fact row (FR-04, FR-05). Never stores 0x27/0x67 raw seed/key. */
public record DecodeResult(
    String frameRef,
    DecodeKind kind,
    String sid,
    String serviceName,
    String subFunctionName,
    String nrcCode,
    String nrcName,
    Map<String, String> fields,
    Boolean suppressPosRsp) {

  public DecodeResult {
    Objects.requireNonNull(frameRef, "frameRef");
    Objects.requireNonNull(kind, "kind");
    fields = Map.copyOf(fields == null ? Map.of() : fields);
  }

  /** Stable text for demos/tests; excludes raw frame HEX. */
  @Override
  public String toString() {
    return "DecodeResult{frameRef="
        + frameRef
        + ", kind="
        + kind
        + ", sid="
        + sid
        + ", serviceName="
        + serviceName
        + ", subFunctionName="
        + subFunctionName
        + ", nrcCode="
        + nrcCode
        + ", nrcName="
        + nrcName
        + ", fields="
        + fields
        + ", suppressPosRsp="
        + suppressPosRsp
        + "}";
  }

  /** Jackson-friendly map without any request/response raw HEX payload. */
  public Map<String, Object> toJsonMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("frameRef", frameRef);
    m.put("kind", kind.name());
    m.put("sid", sid);
    m.put("serviceName", serviceName);
    m.put("subFunctionName", subFunctionName);
    m.put("nrcCode", nrcCode);
    m.put("nrcName", nrcName);
    m.put("fields", fields);
    m.put("suppressPosRsp", suppressPosRsp);
    return m;
  }
}
