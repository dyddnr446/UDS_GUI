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
import uds.catalog.model.ControllerProfileGuideMetadata;
import uds.catalog.model.DidGuideMetadata;
import uds.catalog.model.DtcStatusBitGuideMetadata;
import uds.catalog.model.LayoutGuideMetadata;
import uds.catalog.model.NrcGuideMetadata;
import uds.catalog.model.ServiceGuideMetadata;
import uds.catalog.model.SubFunctionGuideMetadata;
import uds.catalog.model.SymptomGuideMetadata;
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
 * Loads {@code uds_system_catalog.xlsx} (required) and optionally joins {@code
 * uds_guide_catalog.xlsx}. Apache POI types never leave this class (FR-22).
 */
public final class CatalogLoader {

  public static final String DEFAULT_SYSTEM_FILE = "uds_system_catalog.xlsx";
  public static final String DEFAULT_GUIDE_FILE = "uds_guide_catalog.xlsx";

  private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

  private CatalogLoader() {}

  /**
   * Load system workbook; guide path is auto-resolved (catalog sibling, then legacy {@code
   * doc/guide/}) with soft-fail if missing.
   */
  public static UdsCatalog load(Path systemXlsxPath) throws IOException {
    return load(systemXlsxPath, resolveGuidePath(systemXlsxPath));
  }

  public static UdsCatalog load(Path systemXlsxPath, Path guideXlsxPath) throws IOException {
    String catalogVersion = sha256Hex(Files.readAllBytes(systemXlsxPath));
    StandardCatalog standard;
    PayloadCatalog payload;
    EcuConfigCatalog ecuConfig;
    ScenarioCatalog scenario;
    try (InputStream in = Files.newInputStream(systemXlsxPath);
        Workbook workbook = new XSSFWorkbook(in)) {
      List<ControllerProfile> profiles =
          readControllerProfiles(requireSheet(workbook, "ControllerProfiles"));
      List<DidDefinition> dids = readOemExtensions(requireSheet(workbook, "OemExtensions"));
      List<DidProfileSupport> supports =
          readDidProfileSupport(requireSheet(workbook, "DidProfileSupport"));
      List<SymptomDidMap> symptoms = readSymptomDidMap(requireSheet(workbook, "SymptomDidMap"));
      Map<String, ServiceDefinition> services = readServices(requireSheet(workbook, "Services"));
      List<SubFunctionDefinition> subs = readSubFunctions(requireSheet(workbook, "SubFunctions"));
      Map<String, NrcDefinition> nrcs = readNrcs(requireSheet(workbook, "NRC"));
      List<DtcStatusBitDefinition> dtcBits =
          readDtcStatusBits(requireSheet(workbook, "DTCStatusBits"));
      standard = new StandardCatalog(services, subs, nrcs, dtcBits);
      payload = new PayloadCatalog(dids);
      ecuConfig = new EcuConfigCatalog(profiles, supports);
      scenario = new ScenarioCatalog(symptoms);
    }
    GuideCatalog guide = loadGuideSoft(guideXlsxPath);
    return new UdsCatalog(catalogVersion, standard, payload, ecuConfig, scenario, guide);
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

  /**
   * Prefer sibling of the system file ({@code catalog/uds_guide_catalog.xlsx}), then legacy
   * {@code doc/guide/} for local process trees.
   */
  static Path resolveGuidePath(Path systemXlsxPath) {
    Path abs = systemXlsxPath.toAbsolutePath().normalize();
    Path parent = abs.getParent();
    if (parent != null) {
      Path sibling = parent.resolve(DEFAULT_GUIDE_FILE);
      if (Files.isRegularFile(sibling)) {
        return sibling;
      }
    }
    Path walk = parent;
    for (int i = 0; i < 8 && walk != null; i++) {
      Path underDocGuide = walk.resolve("doc").resolve("guide").resolve(DEFAULT_GUIDE_FILE);
      if (Files.isRegularFile(underDocGuide)) {
        return underDocGuide;
      }
      walk = walk.getParent();
    }
    if (parent != null) {
      return parent.resolve(DEFAULT_GUIDE_FILE);
    }
    return Path.of(DEFAULT_GUIDE_FILE);
  }

  /** Soft-fail: missing/unreadable guide → empty maps (core catalog still works). */
  private static GuideCatalog loadGuideSoft(Path guideXlsxPath) {
    if (guideXlsxPath == null || !Files.isRegularFile(guideXlsxPath)) {
      return GuideCatalog.empty();
    }
    try (InputStream in = Files.newInputStream(guideXlsxPath);
        Workbook workbook = new XSSFWorkbook(in)) {
      return readGuide(workbook);
    } catch (Exception ignored) {
      return GuideCatalog.empty();
    }
  }

  private static GuideCatalog readGuide(Workbook workbook) {
    Map<String, ServiceGuideMetadata> services = readServiceGuides(optionalSheet(workbook, "Services"));
    Map<String, SubFunctionGuideMetadata> subs =
        readSubFunctionGuides(optionalSheet(workbook, "SubFunctions"));
    Map<String, NrcGuideMetadata> nrcs = readNrcGuides(optionalSheet(workbook, "NRC"));
    Map<Integer, DtcStatusBitGuideMetadata> dtc =
        readDtcGuides(optionalSheet(workbook, "DTCStatusBits"));
    Map<String, LayoutGuideMetadata> layouts = new HashMap<>();
    layouts.putAll(readLayoutGuides(optionalSheet(workbook, "RequestLayout"), "REQUEST"));
    layouts.putAll(readLayoutGuides(optionalSheet(workbook, "ResponseLayout"), null));
    Map<String, DidGuideMetadata> dids = readDidGuides(optionalSheet(workbook, "OemExtensions"));
    Map<String, ControllerProfileGuideMetadata> profiles =
        readProfileGuides(optionalSheet(workbook, "ControllerProfiles"));
    Map<String, SymptomGuideMetadata> symptoms =
        readSymptomGuides(optionalSheet(workbook, "SymptomDidMap"));
    return new GuideCatalog(services, subs, nrcs, dtc, layouts, dids, profiles, symptoms);
  }

  private static Sheet requireSheet(Workbook workbook, String name) {
    Sheet sheet = workbook.getSheet(name);
    if (sheet == null) {
      throw new IllegalArgumentException("missing sheet: " + name);
    }
    return sheet;
  }

  private static Sheet optionalSheet(Workbook workbook, String name) {
    return workbook.getSheet(name);
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
              intVal(cell(row, h, "priority"), 0)));
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

  private static Map<String, ServiceGuideMetadata> readServiceGuides(Sheet sheet) {
    Map<String, ServiceGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "sid"))) {
        continue;
      }
      String sid = normalizeSidHex(cell(row, h, "sid"));
      out.put(
          sid,
          new ServiceGuideMetadata(
              sid,
              cell(row, h, "purpose_summary_ko"),
              cell(row, h, "when_to_use_ko"),
              cell(row, h, "why_use_ko"),
              cell(row, h, "typical_flow_ko"),
              cell(row, h, "session_note"),
              cell(row, h, "security_note"),
              cell(row, h, "notes"),
              cell(row, h, "example_req_hex"),
              cell(row, h, "example_res_hex")));
    }
    return out;
  }

  private static Map<String, SubFunctionGuideMetadata> readSubFunctionGuides(Sheet sheet) {
    Map<String, SubFunctionGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "parent_sid"))) {
        continue;
      }
      SubFunctionGuideMetadata g =
          new SubFunctionGuideMetadata(
              cell(row, h, "parent_sid"),
              cell(row, h, "sub_function"),
              cell(row, h, "response_needed_practice"),
              cell(row, h, "params_summary"),
              cell(row, h, "when_to_use_ko"),
              cell(row, h, "why_use_ko"),
              cell(row, h, "notes"));
      out.put(g.joinKey(), g);
    }
    return out;
  }

  private static Map<String, NrcGuideMetadata> readNrcGuides(Sheet sheet) {
    Map<String, NrcGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "code"))) {
        continue;
      }
      String code = cell(row, h, "code");
      out.put(
          code,
          new NrcGuideMetadata(
              code,
              cell(row, h, "meaning_ko"),
              cell(row, h, "typical_services"),
              cell(row, h, "when_appears_ko"),
              cell(row, h, "what_to_check_ko"),
              cell(row, h, "notes")));
    }
    return out;
  }

  private static Map<Integer, DtcStatusBitGuideMetadata> readDtcGuides(Sheet sheet) {
    Map<Integer, DtcStatusBitGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "bit"))) {
        continue;
      }
      int bit = intVal(cell(row, h, "bit"), 0);
      out.put(
          bit,
          new DtcStatusBitGuideMetadata(
              bit, cell(row, h, "meaning_ko"), cell(row, h, "check_point_ko")));
    }
    return out;
  }

  private static Map<String, LayoutGuideMetadata> readLayoutGuides(Sheet sheet, String forceKind) {
    Map<String, LayoutGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      String marker =
          blank(cell(row, h, "sid")) ? cell(row, h, "sid_or_marker") : cell(row, h, "sid");
      if (row == null || blank(marker)) {
        continue;
      }
      String kind = forceKind != null ? forceKind : cell(row, h, "kind");
      LayoutGuideMetadata g =
          new LayoutGuideMetadata(
              kind,
              marker,
              cell(row, h, "example_hex"),
              cell(row, h, "when_you_need_this_ko"),
              cell(row, h, "notes"));
      out.put(g.joinKey(), g);
    }
    return out;
  }

  private static Map<String, DidGuideMetadata> readDidGuides(Sheet sheet) {
    Map<String, DidGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "id"))) {
        continue;
      }
      String id = cell(row, h, "id");
      out.put(
          id,
          new DidGuideMetadata(
              id,
              cell(row, h, "speed_basis"),
              cell(row, h, "source_ref"),
              cell(row, h, "example"),
              cell(row, h, "when_to_use_ko"),
              cell(row, h, "why_use_ko"),
              cell(row, h, "notes")));
    }
    return out;
  }

  private static Map<String, ControllerProfileGuideMetadata> readProfileGuides(Sheet sheet) {
    Map<String, ControllerProfileGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "profile_id"))) {
        continue;
      }
      String id = cell(row, h, "profile_id");
      out.put(
          id,
          new ControllerProfileGuideMetadata(
              id,
              cell(row, h, "speed_basis"),
              cell(row, h, "telemetry_reference"),
              cell(row, h, "notes")));
    }
    return out;
  }

  private static Map<String, SymptomGuideMetadata> readSymptomGuides(Sheet sheet) {
    Map<String, SymptomGuideMetadata> out = new HashMap<>();
    if (sheet == null) {
      return out;
    }
    HeaderMap h = HeaderMap.from(sheet.getRow(0));
    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null || blank(cell(row, h, "symptom_key"))) {
        continue;
      }
      String key = cell(row, h, "symptom_key");
      out.put(
          key,
          new SymptomGuideMetadata(key, cell(row, h, "reason_ko"), cell(row, h, "notes")));
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
