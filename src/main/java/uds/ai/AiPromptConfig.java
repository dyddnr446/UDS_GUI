package uds.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import uds.gui.app.DotEnv;

/**
 * Loads editable AI prompt text from {@code ai_advisor_prompt.txt} (or {@code
 * UDS_AI_PROMPT_PATH}). Falls back to built-in defaults when the file is missing.
 *
 * <p>Sections: {@code [system]}, {@code [user]} (advice), {@code [select_system]}, {@code
 * [select_user]} (DID selection).
 */
public final class AiPromptConfig {

  public static final String KEY_PROMPT_PATH = "UDS_AI_PROMPT_PATH";
  public static final String DEFAULT_REL = "ai_advisor_prompt.txt";

  public static final String DEFAULT_SYSTEM =
      "당신은 UDS 진단 어드바이저입니다. 반드시 한국어로만 답변하세요. "
          + "제공된 Read/Finding 사실만 근거로 설명하고 SID/NRC/값을 지어내지 마세요. "
          + "Access unlock·Write 검증은 진단에 끌어오지 마세요. "
          + "summary와 checklist text는 한국어로 작성하세요. UDS 조언 JSON만 반환하세요.";

  public static final String DEFAULT_USER_TEMPLATE =
      """
      아래 Read/Finding 사실만 근거로 한국어 진단 조언을 작성하세요. (영어 금지)
      JSON만 반환: {"summary":string,"checklist":[{"text":string,"evidenceIds":[string]}]}.
      checklist 각 항목에는 제공된 facts의 evidenceId(FINDING 우선)가 1개 이상 필요합니다.
      Access unlock·Write 검증 이야기는 하지 마세요.
      summary와 checklist text는 반드시 한국어로 작성하세요.
      profileId={{profileId}}
      symptom={{symptom}}
      facts:
      {{facts}}
      """;

  public static final String DEFAULT_SELECT_SYSTEM =
      "당신은 UDS 진단 계획 도우미입니다. 반드시 한국어로 reason을 쓰세요. "
          + "제공된 후보 DID 목록에서만 고르세요. 목록에 없는 DID를 만들지 마세요. "
          + "JSON만 반환하세요.";

  public static final String DEFAULT_SELECT_USER_TEMPLATE =
      """
      증상과 가장 관련 있는 DID만 고르세요. 최대 {{maxSelected}}개.
      JSON만 반환: {"selectedDids":["0x...."],"reason":"짧은 한국어 이유"}.
      profileId={{profileId}}
      symptom={{symptom}}
      candidates:
      {{candidates}}
      """;

  private final String systemPrompt;
  private final String userTemplate;
  private final String selectSystemPrompt;
  private final String selectUserTemplate;
  private final Path loadedFrom;

  public AiPromptConfig(
      String systemPrompt,
      String userTemplate,
      String selectSystemPrompt,
      String selectUserTemplate,
      Path loadedFrom) {
    this.systemPrompt =
        systemPrompt == null || systemPrompt.isBlank() ? DEFAULT_SYSTEM : systemPrompt.trim();
    this.userTemplate =
        userTemplate == null || userTemplate.isBlank()
            ? DEFAULT_USER_TEMPLATE
            : userTemplate.trim() + "\n";
    this.selectSystemPrompt =
        selectSystemPrompt == null || selectSystemPrompt.isBlank()
            ? DEFAULT_SELECT_SYSTEM
            : selectSystemPrompt.trim();
    this.selectUserTemplate =
        selectUserTemplate == null || selectUserTemplate.isBlank()
            ? DEFAULT_SELECT_USER_TEMPLATE
            : selectUserTemplate.trim() + "\n";
    this.loadedFrom = loadedFrom;
  }

  public AiPromptConfig(String systemPrompt, String userTemplate, Path loadedFrom) {
    this(systemPrompt, userTemplate, DEFAULT_SELECT_SYSTEM, DEFAULT_SELECT_USER_TEMPLATE, loadedFrom);
  }

  public String systemPrompt() {
    return systemPrompt;
  }

  public String userTemplate() {
    return userTemplate;
  }

  public String selectSystemPrompt() {
    return selectSystemPrompt;
  }

  public String selectUserTemplate() {
    return selectUserTemplate;
  }

  public Path loadedFrom() {
    return loadedFrom;
  }

  public static AiPromptConfig builtin() {
    return new AiPromptConfig(
        DEFAULT_SYSTEM,
        DEFAULT_USER_TEMPLATE,
        DEFAULT_SELECT_SYSTEM,
        DEFAULT_SELECT_USER_TEMPLATE,
        null);
  }

  /** Resolve path from env / walk parents for {@code ai_advisor_prompt.txt}. */
  public static AiPromptConfig loadNear(Path start) {
    Path configured = configuredPath();
    if (configured != null && Files.isRegularFile(configured)) {
      return loadFile(configured);
    }
    Path walk =
        start == null ? Path.of(".").toAbsolutePath().normalize() : start.toAbsolutePath().normalize();
    for (int i = 0; i < 8 && walk != null; i++) {
      Path candidate = walk.resolve(DEFAULT_REL);
      if (Files.isRegularFile(candidate)) {
        return loadFile(candidate);
      }
      walk = walk.getParent();
    }
    return builtin();
  }

  public static AiPromptConfig loadFile(Path file) {
    Objects.requireNonNull(file, "file");
    try {
      String raw = Files.readString(file, StandardCharsets.UTF_8);
      Parsed parsed = parse(raw);
      return new AiPromptConfig(
          parsed.system,
          parsed.user,
          parsed.selectSystem,
          parsed.selectUser,
          file.toAbsolutePath().normalize());
    } catch (IOException e) {
      return builtin();
    }
  }

  private static Path configuredPath() {
    String rel = DotEnv.get(KEY_PROMPT_PATH);
    if (rel == null || rel.isBlank()) {
      return null;
    }
    Path p = Path.of(rel);
    if (p.isAbsolute()) {
      return p.normalize();
    }
    String home = DotEnv.get("UDS_APP_HOME");
    if (home != null && !home.isBlank()) {
      return Path.of(home).resolve(rel).toAbsolutePath().normalize();
    }
    return Path.of(System.getProperty("user.dir", ".")).resolve(rel).toAbsolutePath().normalize();
  }

  static Parsed parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return new Parsed(
          DEFAULT_SYSTEM, DEFAULT_USER_TEMPLATE, DEFAULT_SELECT_SYSTEM, DEFAULT_SELECT_USER_TEMPLATE);
    }
    String system = null;
    String user = null;
    String selectSystem = null;
    String selectUser = null;
    String section = null;
    StringBuilder body = new StringBuilder();
    for (String line : raw.split("\\R", -1)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("#") && section == null) {
        continue;
      }
      String lower = trimmed.toLowerCase(Locale.ROOT);
      if (isSectionHeader(lower)) {
        if ("system".equals(section)) {
          system = body.toString().trim();
        } else if ("user".equals(section)) {
          user = body.toString().trim();
        } else if ("select_system".equals(section)) {
          selectSystem = body.toString().trim();
        } else if ("select_user".equals(section)) {
          selectUser = body.toString().trim();
        }
        body.setLength(0);
        section = sectionName(lower);
        continue;
      }
      if (section != null) {
        if (body.length() > 0) {
          body.append('\n');
        }
        body.append(line);
      }
    }
    if ("system".equals(section)) {
      system = body.toString().trim();
    } else if ("user".equals(section)) {
      user = body.toString().trim();
    } else if ("select_system".equals(section)) {
      selectSystem = body.toString().trim();
    } else if ("select_user".equals(section)) {
      selectUser = body.toString().trim();
    }
    return new Parsed(
        blankTo(system, DEFAULT_SYSTEM),
        blankTo(user, DEFAULT_USER_TEMPLATE),
        blankTo(selectSystem, DEFAULT_SELECT_SYSTEM),
        blankTo(selectUser, DEFAULT_SELECT_USER_TEMPLATE));
  }

  private static boolean isSectionHeader(String lower) {
    return lower.equals("[system]")
        || lower.equals("[user]")
        || lower.equals("[select_system]")
        || lower.equals("[select_user]");
  }

  private static String sectionName(String lower) {
    return switch (lower) {
      case "[system]" -> "system";
      case "[user]" -> "user";
      case "[select_system]" -> "select_system";
      case "[select_user]" -> "select_user";
      default -> null;
    };
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  record Parsed(String system, String user, String selectSystem, String selectUser) {}
}
