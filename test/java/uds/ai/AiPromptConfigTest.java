package uds.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiPromptConfigTest {

  @Test
  void loadNear_readsProjectPromptFile() {
    AiPromptConfig cfg = AiPromptConfig.loadNear(Path.of("").toAbsolutePath().normalize());
    assertTrue(cfg.systemPrompt().toLowerCase().contains("uds"));
    assertTrue(cfg.userTemplate().contains("{{profileId}}"));
    assertTrue(cfg.userTemplate().contains("{{symptom}}"));
    assertTrue(cfg.userTemplate().contains("{{facts}}"));
    assertFalse(cfg.systemPrompt().isBlank());
  }

  @Test
  void parse_sections() {
    AiPromptConfig.Parsed p =
        AiPromptConfig.parse(
            """
            # comment
            [select_system]
            SELECT SYS
            [select_user]
            pick {{candidates}} max {{maxSelected}}
            [system]
            SYS LINE
            [user]
            hello {{symptom}}
            {{facts}}
            """);
    assertTrue(p.system().contains("SYS LINE"));
    assertTrue(p.user().contains("{{symptom}}"));
    assertTrue(p.selectSystem().contains("SELECT SYS"));
    assertTrue(p.selectUser().contains("{{candidates}}"));
    assertTrue(p.selectUser().contains("{{maxSelected}}"));
  }

  @Test
  void loadNear_includesSelectTemplates() {
    AiPromptConfig cfg = AiPromptConfig.loadNear(Path.of("").toAbsolutePath().normalize());
    assertTrue(cfg.selectUserTemplate().contains("{{candidates}}"));
    assertTrue(cfg.selectSystemPrompt().toLowerCase().contains("did")
        || cfg.selectSystemPrompt().contains("후보")
        || !cfg.selectSystemPrompt().isBlank());
  }
}
