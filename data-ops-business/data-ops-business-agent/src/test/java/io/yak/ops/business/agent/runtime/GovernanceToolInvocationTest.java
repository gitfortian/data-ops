package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.context.TrustedUserScope;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.agent.gateway.GovernanceEvidenceGateway;
import io.yak.ops.business.agent.toolset.AgentToolExecution;
import io.yak.ops.business.agent.toolset.GovernanceEvidenceTools;
import io.yak.ops.business.asset.api.AssetGovernanceQueryApi;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.UserExecutionScope;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/** Real SDK registration -> worker tool context -> live identity scope -> source API -> cited final. */
class GovernanceToolInvocationTest {
  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }

  @Test void sdkPropagatesTrustedIdentityAndTurnLedgerOnToolWorker() {
    var users = mock(UserDao.class);
    var snapshots = mock(AuthorizationSnapshotService.class);
    User user = new User(); user.setId(7L); user.setUserName("analyst");
    when(users.selectByUserId(7L)).thenReturn(user);
    when(snapshots.get(7L)).thenReturn(new AuthorizationSnapshot(List.of(), Set.of("agent:chat:run"), List.of(), Set.of(42L)));
    var trusted = new TrustedUserScope(users, snapshots);
    UserExecutionScope scope = new UserExecutionScope() {
      @Override public <T> T call(long userId, long projectId, Supplier<T> action) {
        return trusted.call(userId, projectId, action);
      }
    };
    var authorization = new io.yak.ops.core.security.ActionAuthorization() {
      @Override public void requirePermission(String code) {
        if (!YakSecurityContext.hasPermission(code)) throw new ActionAccessDeniedException(code);
      }
      @Override public void requirePermissionIfAuthenticated(String code) { requirePermission(code); }
    };
    var executor = new AgentToolExecution(scope, authorization);
    var api = mock(AssetGovernanceQueryApi.class);
    when(api.require(7)).thenAnswer(invocation -> {
      assertEquals("analyst", YakSecurityContext.getCurrentUsername());
      assertEquals(42L, YakSecurityContext.getCurrentProjectId());
      return new AssetGovernanceQueryApi.AssetFact(7, "table:sales", "METADATA", "table-7", "销售", "销售事实表", "owner", "PUBLISHED", null);
    });
    var tools = new GovernanceEvidenceTools(executor, new GovernanceEvidenceGateway(provider(api), provider(null)));
    var toolkit = new Toolkit(); toolkit.registerTool(tools);
    assertTrue(toolkit.getToolNames().containsAll(Set.of("get_asset_evidence", "get_asset_section_evidence", "get_quality_execution_evidence", "search_assets")));
    var execution = new AgentExecutionContext(new GovernanceTarget(7L, null));
    var context = RuntimeContext.builder().userId("7").sessionId("governance-sdk").build();
    context.put(AgentExecutionContext.PROJECT_ID, 42L);
    context.put(AgentExecutionContext.class, execution);
    AtomicInteger calls = new AtomicInteger();
    var model = mock(Model.class); when(model.getModelName()).thenReturn("fixture");
    when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
      if (calls.getAndIncrement() == 0) return Flux.just(new ChatResponse("r1",
          List.of(ToolUseBlock.builder().id("c1").name("get_asset_evidence")
              .input(Map.of("asset_id", 7L)).content("{\"asset_id\":7}").build()), null, null, "tool_calls"));
      List<io.agentscope.core.message.Msg> messages = invocation.getArgument(0);
      assertFalse(execution.evidence().entries().isEmpty(), "Tool result: "
          + messages.stream().flatMap(message -> message.getContentBlocks(io.agentscope.core.message.ToolResultBlock.class).stream())
              .flatMap(result -> result.getOutput().stream()).filter(TextBlock.class::isInstance)
              .map(block -> ((TextBlock) block).getText()).toList());
      return Flux.just(new ChatResponse("r2", List.of(TextBlock.builder().text("资产为销售事实表 ["
          + execution.evidence().entries().getFirst().id() + "]").build()), null, null, "stop"));
    });
    var store = new InMemoryAgentStateStore();
    var observations = mock(AgentObservationCollector.class);
    var agent = ReActAgent.builder().name("fixture").model(model).sysPrompt("fixture").toolkit(toolkit).stateStore(store)
        .middleware(new GovernanceContextMiddleware(tools, observations, session -> "turn-sdk")).build();
    var events = GovernanceAnswerGuard.guard(agent.streamEvents(List.of(new UserMessage("解释资产")), context), execution, context, agent)
        .subscribeOn(Schedulers.boundedElastic()).collectList().block(Duration.ofSeconds(10));
    verify(api, times(2)).require(7);
    assertEquals(2, execution.evidence().entries().size());
    verify(observations).toolCall(eq("governance-sdk"), eq("turn-sdk"), anyString(), eq("get_asset_evidence"),
        eq(true), anyLong(), anyLong(), anyString(), anyString(), isNull(), isNull());
    String answer = events.stream().filter(AgentResultEvent.class::isInstance).map(e -> ((AgentResultEvent) e).getResult().getTextContent()).findFirst().orElseThrow();
    assertTrue(answer.contains("销售事实表"));
    assertTrue(answer.contains("/data-asset/detail/7"));
    assertFalse(YakSecurityContext.isAuthenticated());
  }
}
