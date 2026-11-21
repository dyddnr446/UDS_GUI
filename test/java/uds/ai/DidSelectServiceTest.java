package uds.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uds.ai.SymptomDidSuggestionService.CandidateDid;

class DidSelectServiceTest {

  @Test
  void filterToAllowed_dropsUnknownDids() {
    List<String> filtered =
        DidSelectService.filterToAllowed(
            List.of("0x1001", "0xDEAD", "0x1004"), List.of("0x1001", "0x1004", "0x1005"));
    assertEquals(List.of("0x1001", "0x1004"), filtered);
  }

  @Test
  void parseSelectionJson_readsSelectedDidsAndReason() {
    DidSelectService.Parsed p =
        DidSelectService.parseSelectionJson(
            "{\"selectedDids\":[\"0x1001\",\"0x1004\"],\"reason\":\"전압·온도 우선\"}");
    assertEquals(List.of("0x1001", "0x1004"), p.selectedDids());
    assertTrue(p.reason().contains("전압"));
  }

  @Test
  void select_intersectsAiPicksWithRuntime() {
    AiChatCompleter mock =
        (sys, user) ->
            new AiChatCompleter.CompletionResult.Ok(
                "{\"selectedDids\":[\"0x1001\",\"0xDEAD\",\"0x1004\"],\"reason\":\"ok\"}");
    DidSelectService svc = new DidSelectService(mock, AiPromptConfig.builtin());
    DidSelectService.SelectionResult result =
        svc.select(
            "급정지",
            "FLIPSKY_75100_VESC",
            List.of(
                new CandidateDid("0x1001", 1, "전압", "sudden_stop"),
                new CandidateDid("0x1004", 2, "온도", "sudden_stop")),
            List.of("0x1001", "0x1004", "0x1008"));
    assertFalse(result.degraded());
    assertEquals(List.of("0x1001", "0x1004"), result.selectedDids());
  }

  @Test
  void select_degradesWhenCompleterFails() {
    AiChatCompleter failing =
        (sys, user) ->
            new AiChatCompleter.CompletionResult.Err(
                new AiError(AiError.KEY_MISSING, "no key"));
    DidSelectService svc = new DidSelectService(failing, AiPromptConfig.builtin());
    List<String> runtime = List.of("0x1001", "0x1002", "0x1008");
    DidSelectService.SelectionResult result =
        svc.select("급정지", "FLIPSKY_75100_VESC", List.of(), runtime);
    assertTrue(result.degraded());
    assertEquals(runtime, result.selectedDids());
  }

  @Test
  void select_degradesWhenIntersectionEmpty() {
    AiChatCompleter mock =
        (sys, user) ->
            new AiChatCompleter.CompletionResult.Ok(
                "{\"selectedDids\":[\"0xDEAD\"],\"reason\":\"bad\"}");
    DidSelectService svc = new DidSelectService(mock, AiPromptConfig.builtin());
    List<String> runtime = List.of("0x1001", "0x1004");
    DidSelectService.SelectionResult result =
        svc.select("급정지", "P", List.of(), runtime);
    assertTrue(result.degraded());
    assertEquals(runtime, result.selectedDids());
  }
}
