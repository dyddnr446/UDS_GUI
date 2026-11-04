package uds.catalog.load;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import uds.catalog.model.ControllerProfile;
import uds.catalog.model.DidDefinition;
import uds.catalog.model.DidProfileSupport;
import uds.catalog.model.DtcStatusBitDefinition;
import uds.catalog.model.LayoutDefinition;
import uds.catalog.model.NrcDefinition;
import uds.catalog.model.ServiceDefinition;
import uds.catalog.model.SubFunctionDefinition;
import uds.catalog.model.SymptomDidMap;

/**
 * Loads {@code uds_catalog.xlsx} into an immutable {@link UdsCatalog}.
 * Apache POI types never leave this class (FR-22).
 */
public final class CatalogLoader {

  private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

  private CatalogLoader() {}

  public static UdsCatalog load(Path xlsxPath) throws IOException {
    String catalogVersion = sha256Hex(Files.readAllBytes(xlsxPath));
    try (InputStream in = Files.newInputStream(xlsxPath);
        Workbook workbook = new XSSFWorkbook(in)) {
      return build(workbook, catalogVersion);
    }
  }

  private static UdsCatalog build(Workbook workbook, String catalogVersion) {
    List<ControllerProfile> profiles = readControllerProfiles(requireSheet(workbook, "ControllerProfiles"));
    List<DidDefinition> dids = readOemExtensions(requireSheet(workbook, "OemExtensions"));
    List<DidProfileSupport> supports = readDidProfileSupport(requireSheet(workbook, "DidProfileSupport"));
    List<SymptomDidMap> symptoms = readSymptomDidMap(requireSheet(workbook, "SymptomDidMap"));
    Map<String, ServiceDefinition> services = readServices(requireSheet(workbook, "Services"));
    List<SubFunctionDefinition> subs = readSubFunctions(requireSheet(workbook, "SubFunctions"));
    Map<String, NrcDefinition> nrcs = readNrcs(requireSheet(workbook, "NRC"));
    List<DtcStatusBitDefinition> dtcBits = readDtcStatusBits(requireSheet(workbook, "DTCStatusBits"));
    List<LayoutDefinition> reqLayouts = readRequestLayouts(requireSheet(workbook, "RequestLayout"));
    List<LayoutDefinition> resLayouts = readResponseLayouts(requireSheet(workbook, "ResponseLayout"));
    return new UdsCatalog(
        catalogVersion,
        profiles,
        dids,
        supports,
        symptoms,
        services,
        subs,
        nrcs,
        dtcBits,
        reqLayouts,
        resLayouts);
  }

  private static Sheet requireSheet(Workbook workbook, String name) {
    Sheet sheet = workbook.getSheet(name);
    if (sheet == null) {
      throw new IllegalArgumentException("missing sheet: " + name);
    }
    return sheet;
  }

  private static List<ControllerProfile> readControllerProfiles(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<ControllerProfile> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "profile_id"))) {
        continue;
      }
      out.add(
          new ControllerProfile(
              cell(row, h, "profile_id"),
              cell(row, h, "display_name"),
              cell(row, h, "family"),
              cell(row, h, "speed_basis"),
              cell(row, h, "telemetry_reference"),
              cell(row, h, "did_policy"),
              cell(row, h, "notes"),
              yes(cell(row, h, "runtime_enabled")),
              intVal(cell(row, h, "sort_order"), 0)));
    }
    return out;
  }

  private static List<DidDefinition> readOemExtensions(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<DidDefinition> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "id"))) {
        continue;
      }
      out.add(
          new DidDefinition(
              cell(row, h, "type"),
              cell(row, h, "id"),
              cell(row, h, "name"),
              cell(row, h, "related_sid"),
              cell(row, h, "data_type"),
              intVal(cell(row, h, "byte_length"), 0),
              decimal(cell(row, h, "scale")).orElse(BigDecimal.ONE),
              cell(row, h, "unit"),
              decimal(cell(row, h, "normal_min")),
              decimal(cell(row, h, "normal_max")),
              cell(row, h, "access"),
              decimal(cell(row, h, "write_min")),
              decimal(cell(row, h, "write_max")),
              cell(row, h, "supported_profiles"),
              cell(row, h, "speed_basis"),
              cell(row, h, "source_ref"),
              cell(row, h, "example"),
              cell(row, h, "when_to_use_ko"),
              cell(row, h, "why_use_ko"),
              cell(row, h, "notes"),
              yes(cell(row, h, "runtime_enabled")),
              intVal(cell(row, h, "sort_order"), 0),
              cell(row, h, "required_session"),
              cell(row, h, "required_security_mechanism"),
              cell(row, h, "required_security_level"),
              cell(row, h, "length_policy")));
    }
    return out;
  }

  private static List<DidProfileSupport> readDidProfileSupport(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<DidProfileSupport> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "profile_id"))) {
        continue;
      }
      out.add(
          new DidProfileSupport(
              cell(row, h, "profile_id"),
              cell(row, h, "did_id"),
              yes(cell(row, h, "read_enabled")),
              yes(cell(row, h, "write_enabled")),
              yes(cell(row, h, "runtime_enabled")),
              intVal(cell(row, h, "sort_order"), 0)));
    }
    return out;
  }

  private static List<SymptomDidMap> readSymptomDidMap(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<SymptomDidMap> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "symptom_key"))) {
        continue;
      }
      out.add(
          new SymptomDidMap(
              cell(row, h, "symptom_key"),
              cell(row, h, "keywords_ko"),
              cell(row, h, "did_id"),
              intVal(cell(row, h, "priority"), 0),
              cell(row, h, "reason_ko"),
              cell(row, h, "notes")));
    }
    return out;
  }

  private static Map<String, ServiceDefinition> readServices(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    Map<String, ServiceDefinition> out = new HashMap<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "sid"))) {
        continue;
      }
      ServiceDefinition def =
          new ServiceDefinition(
              cell(row, h, "sid"),
              cell(row, h, "name"),
              cell(row, h, "positive_response_sid"),
              cell(row, h, "category"),
              cell(row, h, "purpose_summary_ko"),
              cell(row, h, "when_to_use_ko"),
              cell(row, h, "why_use_ko"),
              cell(row, h, "typical_flow_ko"),
              cell(row, h, "session_note"),
              cell(row, h, "security_note"),
              yes(cell(row, h, "has_subfunction")),
              cell(row, h, "notes"),
              cell(row, h, "example_req_hex"),
              cell(row, h, "example_res_hex"),
              intVal(cell(row, h, "sub_byte_offset"), -1));
      out.put(def.sid(), def);
    }
    return out;
  }

  private static List<SubFunctionDefinition> readSubFunctions(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<SubFunctionDefinition> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "parent_sid"))) {
        continue;
      }
      out.add(
          new SubFunctionDefinition(
              cell(row, h, "parent_sid"),
              cell(row, h, "sub_function"),
              cell(row, h, "name"),
              yes(cell(row, h, "suppress_bit_applicable")),
              cell(row, h, "response_needed_practice"),
              cell(row, h, "params_summary"),
              cell(row, h, "when_to_use_ko"),
              cell(row, h, "why_use_ko"),
              cell(row, h, "notes")));
    }
    return out;
  }

  private static Map<String, NrcDefinition> readNrcs(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    Map<String, NrcDefinition> out = new HashMap<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "code"))) {
        continue;
      }
      NrcDefinition def =
          new NrcDefinition(
              cell(row, h, "code"),
              cell(row, h, "name"),
              cell(row, h, "meaning_ko"),
              cell(row, h, "typical_services"),
              cell(row, h, "when_appears_ko"),
              cell(row, h, "what_to_check_ko"),
              cell(row, h, "notes"));
      out.put(def.code(), def);
    }
    return out;
  }

  private static List<DtcStatusBitDefinition> readDtcStatusBits(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<DtcStatusBitDefinition> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "bit"))) {
        continue;
      }
      out.add(
          new DtcStatusBitDefinition(
              intVal(cell(row, h, "bit"), 0),
              cell(row, h, "mask"),
              cell(row, h, "name_en"),
              cell(row, h, "meaning_ko"),
              cell(row, h, "check_point_ko")));
    }
    return out;
  }

  private static List<LayoutDefinition> readRequestLayouts(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<LayoutDefinition> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "sid"))) {
        continue;
      }
      out.add(
          new LayoutDefinition(
              "REQUEST",
              cell(row, h, "sid"),
              cell(row, h, "layout"),
              cell(row, h, "example_hex"),
              cell(row, h, "when_you_need_this_ko"),
              cell(row, h, "id_type"),
              intVal(cell(row, h, "id_offset"), -1),
              null,
              yes(cell(row, h, "is_variable_length")),
              intVal(cell(row, h, "min_length_bytes"), 0),
              ""));
    }
    return out;
  }

  private static List<LayoutDefinition> readResponseLayouts(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<LayoutDefinition> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "sid_or_marker"))) {
        continue;
      }
      out.add(
          new LayoutDefinition(
              cell(row, h, "kind"),
              cell(row, h, "sid_or_marker"),
              cell(row, h, "layout"),
              cell(row, h, "example_hex"),
              cell(row, h, "when_you_need_this_ko"),
              cell(row, h, "id_type"),
              intVal(cell(row, h, "id_offset"), -1),
              intVal(cell(row, h, "data_offset"), -1),
              null,
              null,
              cell(row, h, "notes")));
    }
    return out;
  }

  private static String cell(Row row, HeaderMap h, String name) {
    Integer idx = h.index(name);
    if (idx == null) {
      return "";
    }
    Cell c = row.getCell(idx);
    if (c == null) {
      return "";
    }
    return FORMATTER.formatCellValue(c).trim();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static boolean yes(String s) {
    return "Y".equalsIgnoreCase(s) || "TRUE".equalsIgnoreCase(s) || "1".equals(s);
  }

  private static int intVal(String s, int defaultValue) {
    if (blank(s)) {
      return defaultValue;
    }
    try {
      if (s.contains(".")) {
        return (int) Double.parseDouble(s);
      }
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static Optional<BigDecimal> decimal(String s) {
    if (blank(s)) {
      return Optional.empty();
    }
    try {
      return Optional.of(new BigDecimal(s));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(data);
      StringBuilder sb = new StringBuilder(dig.length * 2);
      for (byte b : dig) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class HeaderMap {
    private final Map<String, Integer> index;

    private HeaderMap(Map<String, Integer> index) {
      this.index = index;
    }

    static HeaderMap from(Row header) {
      Map<String, Integer> map = new HashMap<>();
      if (header == null) {
        return new HeaderMap(map);
      }
      for (Cell cell : header) {
        String name = FORMATTER.formatCellValue(cell).trim();
        if (!name.isEmpty()) {
          map.put(name, cell.getColumnIndex());
        }
      }
      return new HeaderMap(map);
    }

    Integer index(String name) {
      return index.get(name);
    }
  }
}
