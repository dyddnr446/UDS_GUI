package uds.catalog.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uds.catalog.load.CatalogLoader;
import uds.catalog.model.ControllerProfile;
import uds.catalog.load.UdsCatalog;
import uds.io.HexNormalizer;
import uds.io.LineError;
import uds.io.LogReadResult;
import uds.io.LogReader;
import uds.catalog.model.DidDefinition;
import uds.catalog.model.DidProfileSupport;
import uds.catalog.model.DtcStatusBitDefinition;
import uds.catalog.model.LayoutDefinition;
import uds.catalog.model.NrcDefinition;
import uds.catalog.model.ServiceDefinition;
import uds.catalog.model.SubFunctionDefinition;
import uds.catalog.model.SymptomDidMap;

class IoAndCatalogLoaderTest {

  private static Path root;

  @BeforeAll
  static void resolveRoot() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    if (Files.isRegularFile(cwd.resolve("docs/uds_catalog.xlsx"))) {
      root = cwd;
    } else if (Files.isRegularFile(cwd.resolve("../docs/uds_catalog.xlsx"))) {
      root = cwd.resolve("..").normalize();
    } else {
      throw new IllegalStateException("cannot find project root from " + cwd);
    }
  }

  @Test
  void fr01_fr02_nfr02_demoSessionLoadsAndBrokenLinesIsolated() throws IOException {
    Path demo = root.resolve("samples/demo_session.log");
    LogReadResult ok = LogReader.read(demo);
    assertEquals(0, ok.errors().size(), "demo_session.log should have 0 line errors");
    assertEquals(15, ok.frames().size(), "demo_session.log frame count");

    Path broken = Files.createTempFile("uds-broken-", ".log");
    String content =
        Files.readString(demo, StandardCharsets.UTF_8)
            + "1.700,REQ,22 GG 00\n"
            + "1.710,REQ,22 F\n"
            + "1.720,REQ,22 F1 90\n";
    Files.writeString(broken, content, StandardCharsets.UTF_8);

    LogReadResult mixed = LogReader.read(broken);
    assertTrue(mixed.errors().size() >= 2, "broken hex lines become LineError");
    assertTrue(
        mixed.errors().stream().anyMatch(e -> e.reason().contains("non-hex")),
        "non-hex reason present");
    assertTrue(
        mixed.errors().stream().anyMatch(e -> e.reason().contains("odd")),
        "odd length reason present");
    assertTrue(
        mixed.frames().stream().anyMatch(f -> "22 F1 90".equals(f.hexRaw())),
        "valid line after errors still parsed");
    assertEquals(
        ok.frames().size() + 1,
        mixed.frames().size(),
        "all good frames retained plus trailing valid 22 F1 90");
  }

  @Test
  void fr02_hexNormalizerRejectsOddAndNonHex() {
    assertTrue(HexNormalizer.normalize("0A ff").isPresent());
    assertTrue(HexNormalizer.normalize("22 GG 00").isEmpty());
    assertTrue(HexNormalizer.normalize("22 F").isEmpty());
  }

  @Test
  void fr03_fr22_catalogVersionAndProfiles() throws IOException {
    UdsCatalog catalog = CatalogLoader.load(root.resolve("docs/uds_catalog.xlsx"));
    assertEquals(64, catalog.catalogVersion().length());
    assertTrue(catalog.catalogVersion().matches("[0-9a-f]{64}"));

    List<ControllerProfile> runtime = catalog.runtimeEnabledProfiles();
    assertEquals(2, runtime.size());
    assertTrue(runtime.stream().anyMatch(p -> "FARDRIVER_ND72530".equals(p.profileId())));
    assertTrue(runtime.stream().anyMatch(p -> "FLIPSKY_75100_VESC".equals(p.profileId())));
  }

  @Test
  void fr22_runtimeDidListsMatchSupportMatrix() throws IOException {
    UdsCatalog catalog = CatalogLoader.load(root.resolve("docs/uds_catalog.xlsx"));
    assertEquals(
        List.of("0x1001", "0x1002", "0x1007", "0x1101"),
        catalog.runtimeReadDids("FARDRIVER_ND72530"));
    assertEquals(
        List.of("0x2001", "0x2002", "0x2003"),
        catalog.runtimeWriteDids("FARDRIVER_ND72530"));
    assertEquals(
        List.of("0x1001", "0x1008", "0x1201", "0x1205"),
        catalog.runtimeReadDids("FLIPSKY_75100_VESC"));
    assertEquals(
        List.of("0x2002", "0x2004", "0x2005", "0x2006"),
        catalog.runtimeWriteDids("FLIPSKY_75100_VESC"));
  }

  @Test
  void architecture_catalogPublicApiHasNoPoiTypes() {
    for (Class<?> type :
        List.of(
            uds.catalog.load.UdsCatalog.class,
            uds.catalog.load.CatalogLoader.class,
            uds.catalog.model.ControllerProfile.class,
            uds.catalog.model.DidDefinition.class,
            uds.catalog.model.DidProfileSupport.class,
            uds.catalog.model.SymptomDidMap.class,
            uds.catalog.model.ServiceDefinition.class,
            uds.catalog.model.SubFunctionDefinition.class,
            uds.catalog.model.NrcDefinition.class,
            uds.catalog.model.DtcStatusBitDefinition.class,
            uds.catalog.model.LayoutDefinition.class)) {
      for (Method method : type.getMethods()) {
        if (!method.getDeclaringClass().getName().startsWith("uds.catalog")) {
          continue;
        }
        assertFalse(
            isPoi(method.getReturnType()),
            () -> type.getName() + "#" + method.getName() + " returns POI type");
        for (Parameter p : method.getParameters()) {
          assertFalse(
              isPoi(p.getType()),
              () -> type.getName() + "#" + method.getName() + " param POI type");
        }
      }
    }
  }

  private static boolean isPoi(Class<?> type) {
    if (type.isArray()) {
      return isPoi(type.getComponentType());
    }
    Package pkg = type.getPackage();
    return pkg != null && pkg.getName().startsWith("org.apache.poi");
  }
}
