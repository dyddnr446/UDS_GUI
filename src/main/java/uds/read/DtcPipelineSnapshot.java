package uds.read;

/** Guard → Exchange → Parser stage labels for the DTC tab pipeline card. */
public record DtcPipelineSnapshot(
    String guardLabel,
    String guardStyle,
    String exchangeLabel,
    String exchangeStyle,
    String parserLabel,
    String parserStyle,
    String failureReason,
    String lastAction) {

  public DtcPipelineSnapshot {
    guardLabel = nullToDash(guardLabel);
    guardStyle = nullToIdle(guardStyle);
    exchangeLabel = nullToDash(exchangeLabel);
    exchangeStyle = nullToIdle(exchangeStyle);
    parserLabel = nullToDash(parserLabel);
    parserStyle = nullToIdle(parserStyle);
    failureReason = failureReason == null ? "" : failureReason;
    lastAction = lastAction == null ? "" : lastAction;
  }

  public static DtcPipelineSnapshot empty() {
    return new DtcPipelineSnapshot("—", "idle", "—", "idle", "—", "idle", "", "");
  }

  private static String nullToDash(String s) {
    return s == null || s.isBlank() ? "—" : s;
  }

  private static String nullToIdle(String s) {
    return s == null || s.isBlank() ? "idle" : s;
  }
}
