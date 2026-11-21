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
import java.util.OptionalInt;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import uds.catalog.model.ControllerProfile;
import uds.catalog.model.DidProfileSupport;
import uds.catalog.model.DidDefinition;
import uds.catalog.model.SymptomDidMap;
import uds.catalog.model.DtcStatusBitDefinition;
import uds.catalog.model.NrcDefinition;
import uds.catalog.model.ServiceDefinition;
import uds.catalog.model.SubFunctionDefinition;
import uds.fixture.AccessStep;
import uds.fixture.FixtureRepository;
import uds.fixture.ReadScenario;
import uds.fixture.WriteScenario;

/**
 * Loads DID/DTC + SID master workbooks. Apache POI types never leave this class (FR-22).
 */
public final class CatalogLoader {

  public static final String DEFAULT_DID_DTC_FILE = "uds_did_dtc_catalog.xlsx";
  public static final String DEFAULT_SID_FILE = "uds_sid_catalog.xlsx";

  private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

  private CatalogLoader() {}

  /** Load both masters from a catalog directory. */
  public static UdsCatalog loadFromCatalogDir(Path catalogDir) throws IOException {
    Path dir = catalogDir.toAbsolutePath().normalize();
    return load(dir.resolve(DEFAULT_DID_DTC_FILE), dir.resolve(DEFAULT_SID_FILE));
  }

  /** Load DID/DTC + SID masters. */
  public static UdsCatalog load(Path didDtcXlsxPath, Path sidXlsxPath) throws IOException {
    byte[] didBytes = Files.readAllBytes(didDtcXlsxPath);
    byte[] sidBytes = Files.readAllBytes(sidXlsxPath);
    byte[] combined = new byte[didBytes.length + sidBytes.length];
    System.arraycopy(didBytes, 0, combined, 0, didBytes.length);
    System.arraycopy(sidBytes, 0, combined, didBytes.length, sidBytes.length);
    String catalogVersion = sha256Hex(combined);

    List<ControllerProfile> profiles;
    List<DidDefinition> dids;
    List<DidProfileSupport> supports;
    List<SymptomDidMap> symptoms;
    List<DtcStatusBitDefinition> dtcBits;
    try (InputStream in = Files.newInputStream(didDtcXlsxPath);
        Workbook workbook = new XSSFWorkbook(in)) {
      profiles = readControllerProfiles(requireSheet(workbook, "ControllerProfiles"));
      dids = readOemExtensions(requireSheet(workbook, "OemExtensions"));
      supports = readDidProfileSupport(requireSheet(workbook, "DidProfileSupport"));
      symptoms = readSymptomDidMap(requireSheet(workbook, "SymptomDidMap"));
      dtcBits = readDtcStatusBits(requireSheet(workbook, "DTCStatusBits"));
    }

    Map<String, ServiceDefinition> services;
    List<SubFunctionDefinition> subs;
    Map<String, NrcDefinition> nrcs;
    try (InputStream in = Files.newInputStream(sidXlsxPath);
        Workbook workbook = new XSSFWorkbook(in)) {
      services = readServices(requireSheet(workbook, "Services"));
      subs = readSubFunctions(requireSheet(workbook, "SubFunctions"));
      nrcs = readNrcs(requireSheet(workbook, "NRC"));
    }

    StandardCatalog standard = new StandardCatalog(services, subs, nrcs, dtcBits);
    PayloadCatalog payload = new PayloadCatalog(dids);
    EcuConfigCatalog ecuConfig = new EcuConfigCatalog(profiles, supports);
    ScenarioCatalog scenario = new ScenarioCatalog(symptoms);
    return new UdsCatalog(catalogVersion, standard, payload, ecuConfig, scenario);
  }

  /**
   * Load demo/regression fixture scenarios (Access/Read/Write sheets). POI stays inside this class
   * (FR-22).
   */
  public static FixtureRepository loadFixtures(Path fixtureXlsxPath) throws IOException {
    try (InputStream in = Files.newInputStream(fixtureXlsxPath);
        Workbook workbook = new XSSFWorkbook(in)) {
      List<AccessStep> accessSteps =
          readAccessScenarios(requireSheet(workbook, "AccessScenarios"));
      List<ReadScenario> readScenarios =
          readReadScenarios(requireSheet(workbook, "ReadScenarios"));
      List<WriteScenario> writeScenarios =
          readWriteScenarios(requireSheet(workbook, "WriteScenarios"));
      return new FixtureRepository(accessSteps, readScenarios, writeScenarios);
    }
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
              cell(row, h, "did_policy"),
              ControllerProfile.parseSessionList(cell(row, h, "supported_sessions")),
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
              parseHexInt(cell(row, h, "id")),
              cell(row, h, "name"),
              cell(row, h, "related_sid"),
              cell(row, h, "data_type"),
              intVal(cell(row, h, "byte_length"), 0),
              decimal(cell(row, h, "scale")).orElse(BigDecimal.ONE),
              decimal(cell(row, h, "offset")).orElse(BigDecimal.ZERO),
              cell(row, h, "unit"),
              decimal(cell(row, h, "normal_min")),
              decimal(cell(row, h, "normal_max")),
              cell(row, h, "access"),
              decimal(cell(row, h, "write_min")),
              decimal(cell(row, h, "write_max")),
              cell(row, h, "supported_profiles"),
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
              parseHexInt(cell(row, h, "did_id")),
              intVal(cell(row, h, "priority"), 0),
              cell(row, h, "reason_ko")));
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
              parseHexInt(cell(row, h, "sid")),
              cell(row, h, "name"),
              parseHexIntOptional(cell(row, h, "positive_response_sid")),
              cell(row, h, "category"),
              yes(cell(row, h, "has_subfunction")),
              intVal(cell(row, h, "sub_byte_offset"), -1));
      out.put(def.sidHex(), def);
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
              yes(cell(row, h, "suppress_bit_applicable"))));
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
      NrcDefinition def = new NrcDefinition(cell(row, h, "code"), cell(row, h, "name"));
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
              cell(row, h, "name_en")));
    }
    return out;
  }

  private static List<AccessStep> readAccessScenarios(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<AccessStep> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "flow_id"))) {
        continue;
      }
      out.add(
          new AccessStep(
              cell(row, h, "flow_id"),
              intVal(cell(row, h, "step_no"), 0),
              cell(row, h, "profile_id"),
              cell(row, h, "action"),
              cell(row, h, "initial_state"),
              cell(row, h, "request_template"),
              cell(row, h, "response_kind"),
              cell(row, h, "response_template"),
              cell(row, h, "expected_state"),
              cell(row, h, "expected_error")));
    }
    return out;
  }

  private static List<ReadScenario> readReadScenarios(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<ReadScenario> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "scenario_id"))) {
        continue;
      }
      out.add(
          new ReadScenario(
              cell(row, h, "scenario_id"),
              cell(row, h, "controller_profile"),
              cell(row, h, "label"),
              cell(row, h, "did"),
              cell(row, h, "request_hex"),
              cell(row, h, "response_kind"),
              cell(row, h, "response_hex"),
              cell(row, h, "expected_value"),
              cell(row, h, "expected_status")));
    }
    return out;
  }

  private static List<WriteScenario> readWriteScenarios(Sheet sheet) {
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    List<WriteScenario> out = new ArrayList<>();
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "scenario_id"))) {
        continue;
      }
      out.add(
          new WriteScenario(
              cell(row, h, "scenario_id"),
              cell(row, h, "controller_profile"),
              cell(row, h, "label"),
              cell(row, h, "did"),
              cell(row, h, "input_value"),
              cell(row, h, "data_hex"),
              cell(row, h, "request_hex"),
              cell(row, h, "response_kind"),
              cell(row, h, "response_hex"),
              cell(row, h, "expected_status")));
    }
    return out;
  }

  private static String normalizeSidHex(String raw) {
    OptionalInt v = parseHexIntOptional(raw);
    if (v.isEmpty()) {
      return raw == null ? "" : raw.trim();
    }
    return StandardCatalog.formatSid(v.getAsInt());
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
    if (blank(s) || "-".equals(s.trim())) {
      return Optional.empty();
    }
    try {
      return Optional.of(new BigDecimal(s.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static int parseHexInt(String raw) {
    OptionalInt parsed = parseHexIntOptional(raw);
    if (parsed.isEmpty()) {
      throw new IllegalArgumentException("invalid hex int: " + raw);
    }
    return parsed.getAsInt();
  }

  private static OptionalInt parseHexIntOptional(String raw) {
    if (blank(raw) || "-".equals(raw.trim())) {
      return OptionalInt.empty();
    }
    String s = raw.trim();
    try {
      if (s.regionMatches(true, 0, "0x", 0, 2)) {
        return OptionalInt.of(Integer.decode(s));
      }
      return OptionalInt.of(Integer.parseInt(s, 16));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
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
