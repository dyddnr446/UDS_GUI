package uds.gui.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotEnvTest {

  private static final String KEY = "UDS_DOTENV_UNIT_TEST_KEY";

  @AfterEach
  void clearProp() {
    System.clearProperty(DotEnv.propertyName(KEY));
  }

  @Test
  void load_appliesPropertiesWithoutOverridingExistingProp(@TempDir Path dir) throws Exception {
    Files.writeString(
        dir.resolve(".env"),
        "# comment\n" + KEY + "=from-file\nUDS_AI_MODEL=test-model\n",
        StandardCharsets.UTF_8);

    System.clearProperty(DotEnv.propertyName(KEY));
    System.clearProperty(DotEnv.propertyName("UDS_AI_MODEL"));

    int n = DotEnv.load(dir);
    assertTrue(n >= 1);
    assertEquals("from-file", DotEnv.get(KEY));
    assertEquals("test-model", System.getProperty(DotEnv.propertyName("UDS_AI_MODEL")));

    // second load must not override already-set property
    Files.writeString(dir.resolve(".env"), KEY + "=second\n", StandardCharsets.UTF_8);
    DotEnv.load(dir);
    assertEquals("from-file", DotEnv.get(KEY));
  }

  @Test
  void get_returnsNullWhenUnset() {
    System.clearProperty(DotEnv.propertyName("UDS_DOTENV_MISSING_XYZ"));
    assertNull(DotEnv.get("UDS_DOTENV_MISSING_XYZ"));
  }

  @Test
  void loadNear_findsParentEnv(@TempDir Path dir) throws Exception {
    Path child = dir.resolve("nested");
    Files.createDirectories(child);
    String nearKey = "UDS_DOTENV_NEAR_KEY";
    System.clearProperty(DotEnv.propertyName(nearKey));
    Files.writeString(dir.resolve(".env"), nearKey + "=near\n", StandardCharsets.UTF_8);
    assertTrue(DotEnv.loadNear(child) >= 1);
    assertEquals("near", DotEnv.get(nearKey));
    System.clearProperty(DotEnv.propertyName(nearKey));
  }
}
