package uds.ai.context;

import java.util.Objects;
import java.util.StringJoiner;

/** Builds a structured prompt from sanitized AdvisorContext (no secrets). */
public final class AdvisorPromptBuilder {

  public String build(AdvisorContext context) {
    Objects.requireNonNull(context, "context");
    StringBuilder sb = new StringBuilder();
    sb.append("You are a UDS diagnostic advisor. Explain facts only; do not invent SIDs/NRCs/values.\n");
    sb.append("Return JSON: {\"summary\":string,\"checklist\":[{\"text\":string,\"evidenceIds\":[string]}]}.\n");
    sb.append("Every checklist item MUST include >=1 evidenceId from the provided facts.\n");
    sb.append("profileId=").append(context.profileId()).append('\n');
    sb.append("symptom=").append(context.symptomText()).append('\n');
    sb.append("facts:\n");
    for (AdvisorContext.FactRow fact : context.facts()) {
      sb.append("- evidenceId=")
          .append(fact.evidenceId())
          .append(" kind=")
          .append(fact.kind())
          .append(" {");
      StringJoiner joiner = new StringJoiner(", ");
      fact.fields().forEach((k, v) -> joiner.add(k + '=' + v));
      sb.append(joiner).append("}\n");
    }
    return sb.toString();
  }
}
