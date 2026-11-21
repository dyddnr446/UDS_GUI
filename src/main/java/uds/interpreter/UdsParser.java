package uds.interpreter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import uds.catalog.model.NrcDefinition;
import uds.catalog.model.ServiceDefinition;
import uds.catalog.model.SubFunctionDefinition;
import uds.catalog.load.UdsCatalog;
import uds.io.Frame;
import uds.interpreter.DecodeKind;
import uds.interpreter.DecodeResult;

/** Classifies UDS frames and applies FR-06 suppress/subfunction rules. */
public final class UdsParser {

  private UdsParser() {}

  public static DecodeResult parse(Frame frame, UdsCatalog catalog) {
    String frameRef = frame.ts() + "/" + frame.direction();
    byte[] bytes = frame.bytes();
    if (bytes.length == 0) {
      return new DecodeResult(
          frameRef, DecodeKind.UNKNOWN, null, null, null, null, null, Map.of(), null);
    }

    int first = bytes[0] & 0xFF;
    if (first == 0x7F) {
      return parseNegative(frameRef, bytes, catalog);
    }

    Optional<ServiceDefinition> byPositive = findByPositiveSid(catalog, first);
    if (byPositive.isPresent()) {
      return parsePositive(frameRef, bytes, byPositive.get(), catalog);
    }

    Optional<ServiceDefinition> byRequest = findByRequestSid(catalog, first);
    if (byRequest.isPresent()) {
      return parseRequest(frameRef, bytes, byRequest.get(), catalog);
    }

    return new DecodeResult(
        frameRef,
        DecodeKind.UNKNOWN,
        formatSid(first),
        null,
        null,
        null,
        null,
        Map.of("rawSid", formatSid(first)),
        null);
  }

  private static DecodeResult parseNegative(String frameRef, byte[] bytes, UdsCatalog catalog) {
    String reqSid = bytes.length >= 2 ? formatSid(bytes[1] & 0xFF) : null;
    String nrcCode = bytes.length >= 3 ? formatSid(bytes[2] & 0xFF) : null;
    String serviceName = null;
    if (reqSid != null) {
      ServiceDefinition svc = catalog.getStandard().servicesBySid().get(reqSid);
      if (svc != null) {
        serviceName = svc.name();
      }
    }
    String nrcName = null;
    if (nrcCode != null) {
      NrcDefinition nrc = catalog.getStandard().nrcsByCode().get(nrcCode);
      if (nrc != null) {
        nrcName = nrc.name();
      }
    }
    Map<String, String> fields = new LinkedHashMap<>();
    if (reqSid != null) {
      fields.put("requestSid", reqSid);
    }
    if (serviceName != null) {
      fields.put("requestServiceName", serviceName);
    }
    if (nrcCode != null) {
      fields.put("nrc", nrcCode);
    }
    return new DecodeResult(
        frameRef,
        DecodeKind.NEGATIVE,
        "0x7F",
        "NegativeResponse",
        null,
        nrcCode,
        nrcName,
        fields,
        null);
  }

  private static DecodeResult parseRequest(
      String frameRef, byte[] bytes, ServiceDefinition svc, UdsCatalog catalog) {
    SubInfo sub = resolveSubfunction(bytes, svc, catalog);
    Map<String, String> fields =
        new LinkedHashMap<>(
            SidDecoder.decodeFields(bytes[0] & 0xFF, bytes, DecodeKind.REQUEST, catalog));
    return new DecodeResult(
        frameRef,
        DecodeKind.REQUEST,
        svc.sidHex(),
        svc.name(),
        sub.name(),
        null,
        null,
        fields,
        sub.suppress());
  }

  private static DecodeResult parsePositive(
      String frameRef, byte[] bytes, ServiceDefinition svc, UdsCatalog catalog) {
    SubInfo sub = resolveSubfunction(bytes, svc, catalog);
    Map<String, String> fields =
        new LinkedHashMap<>(
            SidDecoder.decodeFields(bytes[0] & 0xFF, bytes, DecodeKind.POSITIVE, catalog));
    return new DecodeResult(
        frameRef,
        DecodeKind.POSITIVE,
        formatSid(bytes[0] & 0xFF),
        svc.name(),
        sub.name(),
        null,
        null,
        fields,
        sub.suppress());
  }

  /**
   * FR-06: mask bit7 only when Services.has_subfunction=Y. DID services (0x22/0x2E/…) do not mask.
   */
  static SubInfo resolveSubfunction(byte[] bytes, ServiceDefinition svc, UdsCatalog catalog) {
    if (!svc.hasSubfunction() || bytes.length < 2 || svc.subByteOffset() < 0) {
      return new SubInfo(null, null);
    }
    int offset = svc.subByteOffset();
    if (offset >= bytes.length) {
      return new SubInfo(null, null);
    }
    int raw = bytes[offset] & 0xFF;
    boolean suppress = (raw & 0x80) != 0;
    String key = formatSid(raw & 0x7F);
    String name = null;
    for (SubFunctionDefinition sub : catalog.getStandard().subFunctions()) {
      if (sub.parentSid().equalsIgnoreCase(svc.sidHex())
          && sub.subFunction().equalsIgnoreCase(key)) {
        name = sub.name();
        break;
      }
    }
    if (name == null) {
      name = key;
    }
    return new SubInfo(name, suppress);
  }

  private static Optional<ServiceDefinition> findByRequestSid(UdsCatalog catalog, int sid) {
    return catalog.getStandard().getService(sid);
  }

  private static Optional<ServiceDefinition> findByPositiveSid(UdsCatalog catalog, int sid) {
    String positive = formatSid(sid);
    for (ServiceDefinition svc : catalog.getStandard().servicesBySid().values()) {
      if (positive.equalsIgnoreCase(svc.positiveResponseSidHex())) {
        return Optional.of(svc);
      }
    }
    if (sid >= 0x40) {
      ServiceDefinition svc = catalog.getStandard().servicesBySid().get(formatSid(sid - 0x40));
      if (svc != null) {
        return Optional.of(svc);
      }
    }
    return Optional.empty();
  }

  static String formatSid(int sid) {
    return String.format(Locale.ROOT, "0x%02X", sid & 0xFF);
  }

  record SubInfo(String name, Boolean suppress) {}
}
