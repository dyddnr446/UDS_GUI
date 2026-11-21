package uds.gui.app;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiBootstrapSmokeTest {
  @Test
  void openSessionLikeRunBat() throws Exception {
    Path root = UdsGuiApplication.resolveAppRoot();
    AppConfig c = AppConfig.fromEnvironment(root);
    assertTrue(Files.isRegularFile(c.catalogPath()), c.catalogPath().toString());
    assertTrue(Files.isRegularFile(c.fixturePath()), c.fixturePath().toString());
    assertTrue(Files.isRegularFile(c.samplesDir().resolve("demo_session.log")));
    AppSession s = AppSession.open(c, root.resolve("output"), "smoke-gui");
    assertNotNull(s.catalog());
    assertFalse(s.fixtures().readScenarios().isEmpty());
  }
}