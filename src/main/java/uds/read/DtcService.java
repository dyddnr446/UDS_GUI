package uds.read;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import uds.access.AccessSnapshot;
import uds.access.ActiveDiagnosticContext;
import uds.access.DiagnosticAccessGuard;
import uds.access.GuardDecision;
import uds.catalog.load.UdsCatalog;
import uds.catalog.model.DtcStatusBitDefinition;
import uds.exchange.DiagnosticExchangePort;
import uds.exchange.ExchangeRequest;
import uds.exchange.ExchangeResponse;
import uds.fixture.FixtureDtcCatalog;

/** ReadDTC (0x19) / ClearDTC (0x14) with multi-item decode (FR-30). */
public final class DtcService {

  public static final int SUB_REPORT_NUMBER = 0x01;
  public static final int SUB_REPORT_BY_STATUS_MASK = 0x02;
  public static final int MASK_ALL = 0xFF;
  public static final int GROUP_ALL = 0xFFFFFF;

  private final ActiveDiagnosticContext context;
  private final DiagnosticAccessGuard guard;
  private final DiagnosticExchangePort port;
  private final UdsCatalog catalog;
  private final String analysisSessionId;

  /** Optional name lookup from fixture echo (codeHex → name); filled by parser from response only if embedded. */
  private final Map<String, String> nameHints = new HashMap<>();

  public DtcService(
      ActiveDiagnosticContext context,
      DiagnosticAccessGuard guard,
      DiagnosticExchangePort port,
      UdsCatalog catalog,
      String analysisSessionId) {
    this.context = Objects.requireNonNull(context, "context");
    this.guard = Objects.requireNonNull(guard, "guard");
    this.port = Objects.requireNonNull(port, "port");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.analysisSessionId = Objects.requireNonNull(analysisSessionId, "analysisSessionId");
  }

  /** Register display names for known fixture DTC codes (call from AppSession / tests). */
  public void putNameHint(String codeHex, String name) {
    if (codeHex != null && !codeHex.isBlank() && name != null) {
      nameHints.put(normalizeCode(codeHex), name);
    }
  }

  public void clearNameHints() {
    nameHints.clear();
  }

  public static String buildReadRequestHex(int subFunction, int statusMask) {
    return String.format(
        Locale.ROOT, "19 %02X %02X", subFunction & 0xFF, statusMask & 0xFF);
  }

  public static String buildClearRequestHex(int groupOfDtc) {
    int g = groupOfDtc & 0xFFFFFF;
    return String.format(
        Locale.ROOT, "14 %02X %02X %02X", (g >> 16) & 0xFF, (g >> 8) & 0xFF, g & 0xFF);
  }

  public DtcResult readDtc(int subFunction, int statusMask) {
    String requestHex = buildReadRequestHex(subFunction, statusMask);
    return exchange("0x19", requestHex, subFunction, statusMask);
  }

  public DtcResult clearDtc(int groupOfDtc) {
    String requestHex = buildClearRequestHex(groupOfDtc);
    return exchange("0x14", requestHex, -1, -1);
  }

  /** Default classroom read: reportDTCByStatusMask + All. */
  public DtcResult readDtc() {
    return readDtc(SUB_REPORT_BY_STATUS_MASK, MASK_ALL);
  }

  public DtcResult clearDtc() {
    return clearDtc(GROUP_ALL);
  }

  private DtcResult exchange(String serviceHex, String requestHex, int subFn, int mask) {
    if (guard.checkUnlocked() != GuardDecision.ALLOW) {
      return DtcResult.denied(serviceHex, requestHex);
    }
    AccessSnapshot snap = context.snapshot();
    String requestId = "dtc-" + UUID.randomUUID();
    ExchangeRequest request =
        new ExchangeRequest(
            analysisSessionId,
            requestId,
            snap.profileId(),
            serviceHex,
            "",
            requestHex,
            snap.generation());
    ExchangeResponse response = port.exchange(request);
    String rsp = response.hex() == null ? "" : response.hex().trim();
    return decode(serviceHex, requestHex, rsp, subFn);
  }

  DtcResult decode(String serviceHex, String requestHex, String responseHex, int requestSubFn) {
    String compact = compactHex(responseHex);
    if (compact.isEmpty()) {
      DtcPipelineSnapshot pipe =
          pipeOk("ALLOW", "EMPTY", "no response", "Last Action: empty response");
      return new DtcResult(
          serviceHex, requestHex, responseHex, true, "EMPTY", "", "", 0, List.of(), "no response", pipe);
    }
    if (compact.startsWith("7F")) {
      String nrc = compact.length() >= 6 ? "0x" + compact.substring(4, 6) : "";
      DtcPipelineSnapshot pipe =
          new DtcPipelineSnapshot(
              "ALLOW",
              "ok",
              "NEGATIVE " + nrc,
              "error",
              "—",
              "idle",
              "NRC " + nrc,
              "Last Action: NEGATIVE " + nrc);
      return new DtcResult(
          serviceHex, requestHex, responseHex, true, "NEGATIVE", nrc, "", 0, List.of(), responseHex, pipe);
    }
    if ("0x14".equalsIgnoreCase(serviceHex) && compact.startsWith("54")) {
      DtcPipelineSnapshot pipe =
          pipeOk("ALLOW", "POSITIVE 0x54", "cleared", "Last Action: Clear 0x14 — POSITIVE (0x54)");
      return new DtcResult(
          serviceHex,
          requestHex,
          responseHex,
          true,
          "POSITIVE",
          "",
          "",
          0,
          List.of(),
          "DTC group cleared",
          pipe);
    }
    if ("0x19".equalsIgnoreCase(serviceHex) && compact.startsWith("59") && compact.length() >= 4) {
      int reportType = Integer.parseInt(compact.substring(2, 4), 16);
      if (reportType == SUB_REPORT_NUMBER && compact.length() >= 10) {
        String avail = "0x" + compact.substring(4, 6);
        int count = Integer.parseInt(compact.substring(6, 10), 16);
        DtcPipelineSnapshot pipe =
            pipeOk(
                "ALLOW",
                "POSITIVE 0x59",
                "count=" + count,
                "Last Action: Read 0x19 — count=" + count);
        return new DtcResult(
            serviceHex, requestHex, responseHex, true, "POSITIVE", "", avail, count, List.of(), "", pipe);
      }
      if (reportType == SUB_REPORT_BY_STATUS_MASK && compact.length() >= 6) {
        String avail = "0x" + compact.substring(4, 6);
        List<DtcItem> items = parseDtcList(compact.substring(6));
        int count = items.size();
        String parserLabel = count + " DTCs";
        String last =
            "Last Action: Read 0x19 — " + count + (count == 1 ? " DTC found" : " DTCs found");
        DtcPipelineSnapshot pipe = pipeOk("ALLOW", "POSITIVE 0x59", parserLabel, last);
        return new DtcResult(
            serviceHex, requestHex, responseHex, true, "POSITIVE", "", avail, count, items, "", pipe);
      }
    }
    DtcPipelineSnapshot pipe =
        new DtcPipelineSnapshot(
            "ALLOW", "ok", "POSITIVE", "ok", "unparsed", "warn", "", "Last Action: unparsed response");
    return new DtcResult(
        serviceHex, requestHex, responseHex, true, "POSITIVE", "", "", 0, List.of(), responseHex, pipe);
  }

  /** Package-visible for tests. */
  DtcResult decode(String serviceHex, String requestHex, String responseHex) {
    return decode(serviceHex, requestHex, responseHex, -1);
  }

  private List<DtcItem> parseDtcList(String bodyCompact) {
    List<DtcItem> out = new ArrayList<>();
    // each record: 3 bytes DTC + 1 byte status = 8 hex chars
    for (int i = 0; i + 8 <= bodyCompact.length(); i += 8) {
      String codePart = bodyCompact.substring(i, i + 6);
      int statusByte = Integer.parseInt(bodyCompact.substring(i + 6, i + 8), 16);
      String codeHex = "0x" + codePart.toUpperCase(Locale.ROOT);
      String key = normalizeCode(codeHex);
      String name = nameHints.getOrDefault(key, "");
      if (name.isBlank()) {
        name = FixtureDtcCatalog.nameOf(key).orElse("");
      }
      if (name.isBlank()) {
        name = "(교육용 더미 DTC — 설명 미등록)";
      }
      out.add(
          new DtcItem(
              codeHex,
              name,
              String.format(Locale.ROOT, "0x%02X", statusByte),
              decodeStatusFlags(statusByte)));
    }
    return List.copyOf(out);
  }

  private List<String> decodeStatusFlags(int statusByte) {
    List<String> out = new ArrayList<>();
    for (DtcStatusBitDefinition bit : catalog.getStandard().dtcStatusBits()) {
      int mask = parseMask(bit.mask(), bit.bit());
      if (mask != 0 && (statusByte & mask) != 0) {
        String name = bit.nameEn();
        out.add(name == null || name.isBlank() ? ("bit" + bit.bit()) : name);
      }
    }
    return List.copyOf(out);
  }

  private static DtcPipelineSnapshot pipeOk(
      String guard, String exchange, String parser, String lastAction) {
    return new DtcPipelineSnapshot(guard, "ok", exchange, "ok", parser, "ok", "", lastAction);
  }

  private static int parseMask(String maskHex, int bitIndex) {
    if (maskHex != null && !maskHex.isBlank()) {
      try {
        String s = maskHex.trim();
        if (s.regionMatches(true, 0, "0x", 0, 2)) {
          return Integer.decode(s);
        }
        return Integer.parseInt(s, 16);
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    if (bitIndex >= 0 && bitIndex < 8) {
      return 1 << bitIndex;
    }
    return 0;
  }

  private static String compactHex(String hex) {
    if (hex == null) {
      return "";
    }
    return hex.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
  }

  private static String normalizeCode(String codeHex) {
    String s = codeHex.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("0X")) {
      s = s.substring(2);
    }
    return "0x" + s;
  }
}
