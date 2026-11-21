package uds.fixture;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;
import uds.fixture.FixtureRunConfig;
import uds.fixture.StatefulFixtureEcuSimulator;

/**
 * Analysis-session-scoped File Port. Scenario changes only via {@link #setFixtureRunConfig}.
 * Holds a single {@link StatefulFixtureEcuSimulator} for the session lifetime.
 */
public final class FileDiagnosticExchangePort implements DiagnosticExchangePort {

  private final StatefulFixtureEcuSimulator simulator;

  public FileDiagnosticExchangePort(StatefulFixtureEcuSimulator simulator) {
    this.simulator = Objects.requireNonNull(simulator, "simulator");
  }

  public void setFixtureRunConfig(FixtureRunConfig config) {
    simulator.setRunConfig(config);
  }

  public StatefulFixtureEcuSimulator simulator() {
    return simulator;
  }

  @Override
  public ExchangeResponse exchange(ExchangeRequest request) {
    Objects.requireNonNull(request, "request");
    byte[] reqBytes = parseHex(request.hex());
    byte[] rspBytes = simulator.exchange(request.profile(), reqBytes);
    String fixtureId =
        simulator.runConfig() == null ? null : simulator.runConfig().scenarioOrFlowId();
    String kind = classifyKind(rspBytes);
    String hex = formatHex(rspBytes);
    return new ExchangeResponse(
        "rsp-" + UUID.randomUUID(), request.requestId(), kind, hex, fixtureId, false);
  }

  private static String classifyKind(byte[] rsp) {
    if (rsp == null || rsp.length == 0) {
      return "EMPTY";
    }
    if (rsp[0] == 0x7F) {
      return "NEGATIVE";
    }
    return "POSITIVE";
  }

  static byte[] parseHex(String hex) {
    String compact = hex == null ? "" : hex.replace(" ", "").replace("_", "").toUpperCase(Locale.ROOT);
    if (compact.isEmpty()) {
      return new byte[0];
    }
    return HexFormat.of().parseHex(compact);
  }

  static String formatHex(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(bytes.length * 3);
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
    }
    return sb.toString();
  }
}
