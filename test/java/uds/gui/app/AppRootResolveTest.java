package uds.gui.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppRootResolveTest {

  @Test
  void resolveAppRoot_findsCatalogUnderMasterData() {
    Path root = UdsGuiApplication.resolveAppRoot();
    assertTrue(
        Files.isRegularFile(root.resolve("기준정보").resolve("uds_system_catalog.xlsx")),
        "expected 기준정보/uds_system_catalog.xlsx under " + root);
  }
}
