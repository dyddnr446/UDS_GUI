package uds.fixture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Offline demo DTC dictionary (code → Korean description). Shared by simulator seeds and UI
 * decode so names always resolve without depending on session wiring.
 */
public final class FixtureDtcCatalog {

  private FixtureDtcCatalog() {}

  public static Map<String, List<FixtureDtcEntry>> seedByProfile() {
    Map<String, List<FixtureDtcEntry>> seeds = new LinkedHashMap<>();
    seeds.put("FARDRIVER_ND72530", List.copyOf(fardriverSeeds()));
    seeds.put("FLIPSKY_75100_VESC", List.copyOf(vescSeeds()));
    return seeds;
  }

  public static Optional<String> nameOf(int code) {
    return nameOf(formatCode(code));
  }

  public static Optional<String> nameOf(String codeHex) {
    if (codeHex == null || codeHex.isBlank()) {
      return Optional.empty();
    }
    String key = normalize(codeHex);
    for (List<FixtureDtcEntry> list : seedByProfile().values()) {
      for (FixtureDtcEntry e : list) {
        if (normalize(e.codeHex()).equals(key)) {
          return Optional.of(e.nameKo());
        }
      }
    }
    return Optional.empty();
  }

  public static Map<String, String> allNameHints() {
    Map<String, String> out = new LinkedHashMap<>();
    for (List<FixtureDtcEntry> list : seedByProfile().values()) {
      for (FixtureDtcEntry e : list) {
        out.put(e.codeHex(), e.nameKo());
      }
    }
    return Map.copyOf(out);
  }

  private static List<FixtureDtcEntry> fardriverSeeds() {
    List<FixtureDtcEntry> list = new ArrayList<>();
    list.add(FixtureDtcEntry.of(0x010101, 0x09, "배터리 저전압 (UVLO)"));
    list.add(FixtureDtcEntry.of(0x010203, 0x01, "팩 전압 급락"));
    list.add(FixtureDtcEntry.of(0x020110, 0x04, "모터 과열 (pending)"));
    list.add(FixtureDtcEntry.of(0x020211, 0x0D, "컨트롤러 MOSFET 과온"));
    list.add(FixtureDtcEntry.of(0x030301, 0x01, "상전류 과전류"));
    list.add(FixtureDtcEntry.of(0x040401, 0x08, "스로틀 신호 이상 (confirmed)"));
    list.add(FixtureDtcEntry.of(0x050501, 0x82, "홀센서 누락 / 경고등"));
    return list;
  }

  private static List<FixtureDtcEntry> vescSeeds() {
    List<FixtureDtcEntry> list = new ArrayList<>();
    list.add(FixtureDtcEntry.of(0x0A0B0C, 0x01, "게이트 드라이버 이상"));
    list.add(FixtureDtcEntry.of(0x0A1112, 0x09, "DC버스 저전압"));
    list.add(FixtureDtcEntry.of(0x0B0102, 0x0C, "모터 온도 경고 (pending+confirmed)"));
    list.add(FixtureDtcEntry.of(0x0B0304, 0x04, "FET 온도 pending"));
    list.add(FixtureDtcEntry.of(0x0C0101, 0x08, "CAN/UART 통신 타임아웃"));
    list.add(FixtureDtcEntry.of(0x0D0203, 0x03, "엔코더/ERPM 불일치"));
    list.add(FixtureDtcEntry.of(0x0E0E0E, 0x80, "경고등 요청 (WIR)만"));
    return list;
  }

  private static String formatCode(int code) {
    return String.format(Locale.ROOT, "0x%06X", code & 0xFFFFFF);
  }

  private static String normalize(String codeHex) {
    String s = codeHex.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("0X")) {
      s = s.substring(2);
    }
    return "0x" + s;
  }
}
