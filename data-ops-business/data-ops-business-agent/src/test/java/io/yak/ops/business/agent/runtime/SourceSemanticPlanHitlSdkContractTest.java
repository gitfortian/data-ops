package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

/**
 * F-039 / #494: Exercise the actual pinned HarnessAgent -> ReActAgent permission HITL event path.
 * Scripted model, isolated filesystem and file StateStore; no provider, source rows or business writes.
 * This is not an adapter to the existing production AgentTurnExecutor.
 */
class SourceSemanticPlanHitlSdkContractTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final String USER = "owner";
  private static final String SESSION = "source-task-a";

  @Test
  void approvalResumesExactPendingToolAndExitsPlan(@TempDir Path root) throws Exception {
    Path workspace = Files.createDirectory(root.resolve("workspace"));
    var store = new JsonFileAgentStateStore(root.resolve("state"));
    var context = context();
    try (HarnessAgent agent = create(workspace, store, new ExitThenAnswerModel())) {
      agent.enterPlanMode(context);
      RequireUserConfirmEvent pending = pauseAtPlanExit(agent, context);
      assertTrue(agent.isPlanModeActive(context));
      assertEquals("plan_exit", pending.getToolCalls().get(0).getName());
      assertFalse(pending.getReplyId().isBlank());

      List<AgentEvent> resumed = events(agent, context,
          confirmation(pending.getToolCalls().get(0), true));
      assertFalse(resumed.stream().anyMatch(RequireUserConfirmEvent.class::isInstance));
      assertFalse(agent.isPlanModeActive(context), "only approved plan_exit switches to BUILD");
    }
  }

  @Test
  void rejectionNeverExitsPlan(@TempDir Path root) throws Exception {
    Path workspace = Files.createDirectory(root.resolve("workspace"));
    var store = new JsonFileAgentStateStore(root.resolve("state"));
    var context = context();
    try (HarnessAgent agent = create(workspace, store, new ExitThenAnswerModel())) {
      agent.enterPlanMode(context);
      RequireUserConfirmEvent pending = pauseAtPlanExit(agent, context);

      events(agent, context, confirmation(pending.getToolCalls().get(0), false));

      assertTrue(agent.isPlanModeActive(context), "rejected plan_exit must not modify plan state");
    }
  }

  @Test
  void invalidAndDuplicateConfirmationNeverAuthorizeExit(@TempDir Path root) throws Exception {
    Path workspace = Files.createDirectory(root.resolve("workspace"));
    var store = new JsonFileAgentStateStore(root.resolve("state"));
    var context = context();
    try (HarnessAgent agent = create(workspace, store, new ExitThenAnswerModel())) {
      agent.enterPlanMode(context);
      RequireUserConfirmEvent pending = pauseAtPlanExit(agent, context);
      ToolUseBlock requested = pending.getToolCalls().get(0);

      ToolUseBlock stale = ToolUseBlock.builder().id("unrelated-id").name("plan_exit")
          .input(Map.of()).build();
      assertThrows(RuntimeException.class, () -> events(agent, context, confirmation(stale, true)));
      assertTrue(agent.isPlanModeActive(context));

      Msg twice = UserMessage.builder().metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS,
          List.of(new ConfirmResult(true, requested), new ConfirmResult(true, requested)))).build();
      assertThrows(RuntimeException.class, () -> events(agent, context, twice));
      assertTrue(agent.isPlanModeActive(context));

      // The current legitimate pending call is still the only one eligible for approval.
      events(agent, context, confirmation(requested, true));
      assertFalse(agent.isPlanModeActive(context));
    }
  }

  @Test
  void pendingPermissionSurvivesSdkStateStoreReopen(@TempDir Path root) throws Exception {
    Path workspace = Files.createDirectory(root.resolve("workspace"));
    Path statePath = root.resolve("state");
    var context = context();
    ToolUseBlock pendingCall;
    try (HarnessAgent first = create(workspace, new JsonFileAgentStateStore(statePath),
        new ExitThenAnswerModel())) {
      first.enterPlanMode(context);
      pendingCall = pauseAtPlanExit(first, context).getToolCalls().get(0);
      assertTrue(first.isPlanModeActive(context));
    }

    try (HarnessAgent restored = create(workspace, new JsonFileAgentStateStore(statePath),
        new TextOnlyModel())) {
      assertTrue(restored.isPlanModeActive(context));
      events(restored, context, confirmation(pendingCall, true));
      assertFalse(restored.isPlanModeActive(context));
    }
  }

  private static HarnessAgent create(Path workspace, JsonFileAgentStateStore store, Model model) {
    return HarnessAgent.builder()
        .name("f039-sdk-plan-spike")
        .model(model)
        .workspace(workspace)
        .abstractFilesystem(new LocalFilesystem(workspace))
        .stateStore(store)
        .enablePlanMode()
        .disableMemoryTools()
        .disableMemoryHooks()
        .disableCompaction()
        .build();
  }

  private static RuntimeContext context() {
    return RuntimeContext.builder().userId(USER).sessionId(SESSION).build();
  }

  private static RequireUserConfirmEvent pauseAtPlanExit(HarnessAgent agent, RuntimeContext context) {
    var confirm = events(agent, context, new UserMessage("Prepare a plan and ask to exit."))
        .stream().filter(RequireUserConfirmEvent.class::isInstance)
        .map(RequireUserConfirmEvent.class::cast).findFirst().orElseThrow();
    assertEquals(1, confirm.getToolCalls().size());
    assertNotNull(confirm.getToolCalls().get(0).getId());
    return confirm;
  }

  private static List<AgentEvent> events(HarnessAgent agent, RuntimeContext ctx, Msg message) {
    return agent.streamEvents(message, ctx).collectList().block(TIMEOUT);
  }

  private static Msg confirmation(ToolUseBlock call, boolean approved) {
    return UserMessage.builder()
        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(new ConfirmResult(approved, call))))
        .build();
  }

  private static ChatResponse toolExit() {
    var input = Map.<String, Object>of("summary", "Scope reviewed");
    return ChatResponse.builder().content(List.of(ToolUseBlock.builder()
        .id("plan-exit-1").name("plan_exit").input(input)
        .content(JsonUtils.getJsonCodec().toJson(input)).build()))
        .usage(new ChatUsage(1, 1, 0)).build();
  }

  private static ChatResponse textAnswer() {
    return ChatResponse.builder().content(List.of(TextBlock.builder().text("Done").build()))
        .usage(new ChatUsage(1, 1, 0)).build();
  }

  private static final class ExitThenAnswerModel implements Model {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools,
        GenerateOptions options) {
      return Flux.just(calls.getAndIncrement() == 0 ? toolExit() : textAnswer());
    }

    @Override
    public String getModelName() {
      return "f039-sdk-scripted";
    }
  }

  private static final class TextOnlyModel implements Model {
    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools,
        GenerateOptions options) {
      return Flux.just(textAnswer());
    }

    @Override
    public String getModelName() {
      return "f039-sdk-restored";
    }
  }
}
