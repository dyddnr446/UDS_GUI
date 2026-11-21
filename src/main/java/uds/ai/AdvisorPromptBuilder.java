package uds.ai;

import java.nio.file.Path;
import java.util.Objects;
import java.util.StringJoiner;

/** Builds a structured prompt from sanitized AdvisorContext (no secrets). */
public final class AdvisorPromptBuilder {

  private final String userTemplate;

  public AdvisorPromptBuilder() {
    this(AiPromptConfig.loadNear(Path.of(System.getProperty("user.dir", "."))).userTemplate());
  }

  public AdvisorPromptBuilder(AiPromptConfig config) {
    this(Objects.requireNonNull(config, "config").userTemplate());
  }

  public AdvisorPromptBuilder(String userTemplate) {
    this.userTemplate =
        userTemplate == null || userTemplate.isBlank()
            ? AiPromptConfig.DEFAULT_USER_TEMPLATE
            : userTemplate;
  }

  public String build(AdvisorContext context) {
    Objects.requireNonNull(context, "context");
    StringBuilder facts = new StringBuilder();
    for (AdvisorContext.FactRow fact : context.facts()) {
      facts
          .append("- evidenceId=")
          .append(fact.evidenceId())
          .append(" kind=")
          .append(fact.kind())
          .append(" {");
      StringJoiner joiner = new StringJoiner(", ");
      fact.fields().forEach((k, v) -> joiner.add(k + '=' + v));
      facts.append(joiner).append("}\n");
    }
    return userTemplate
        .replace("{{profileId}}", nullToEmpty(context.profileId()))
        .replace("{{symptom}}", nullToEmpty(context.symptomText()))
        .replace("{{facts}}", facts.toString());
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
