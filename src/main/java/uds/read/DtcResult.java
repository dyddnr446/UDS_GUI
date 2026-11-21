package uds.read;

import java.util.List;
import java.util.Objects;

/** Read DTC (0x19) / Clear DTC (0x14) result with multi-item decode and pipeline stages. */
public record DtcResult(
    String serviceHex,
    String requestHex,
    String responseHex,
    boolean allowed,
    String statusLabel,
    String nrcHex,
    String availabilityMaskHex,
    int dtcCount,
    List<DtcItem> items,
    String detail,
    DtcPipelineSnapshot pipeline) {

  public DtcResult {
    Objects.requireNonNull(serviceHex, "serviceHex");
    requestHex = requestHex == null ? "" : requestHex;
    responseHex = responseHex == null ? "" : responseHex;
    statusLabel = statusLabel == null ? "" : statusLabel;
    nrcHex = nrcHex == null ? "" : nrcHex;
    availabilityMaskHex = availabilityMaskHex == null ? "" : availabilityMaskHex;
    items = items == null ? List.of() : List.copyOf(items);
    detail = detail == null ? "" : detail;
    pipeline = pipeline == null ? DtcPipelineSnapshot.empty() : pipeline;
  }

  public static DtcResult denied(String serviceHex, String requestHex) {
    DtcPipelineSnapshot pipe =
        new DtcPipelineSnapshot(
            "ACCESS_DENIED",
            "error",
            "—",
            "idle",
            "—",
            "idle",
            "Unlock required",
            "Last Action: denied — Unlock required");
    return new DtcResult(
        serviceHex, requestHex, "", false, "ACCESS_DENIED", "", "", 0, List.of(), "Unlock required", pipe);
  }
}
