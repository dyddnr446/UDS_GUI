package uds.gui.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppRootResolveTest {

  @Test
  void resolveAppRoot_findsCatalogUnderDocs() {
    Path root = UdsGuiApplication.resolveAppRoot();
    assertTrue(
        Files.isRegularFile(root.resolve("docs").resolve("uds_catalog.xlsx")),
        "expected docs/uds_catalog.xlsx under " + root);
  }
}
