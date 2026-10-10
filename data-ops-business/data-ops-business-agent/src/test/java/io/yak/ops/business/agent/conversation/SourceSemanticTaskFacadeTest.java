package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.MessageTreeNode;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.MessageTreeRepository;
import io.yak.ops.business.agent.repository.support.TurnInputCodec;
import io.yak.ops.business.datasource.domain.DataSourceReference;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.metadata.api.MetadataQueryApi;
import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class SourceSemanticTaskFacadeTest {
  @TempDir Path workspace;
  private DataSourceReader sources = mock(DataSourceReader.class);
  private MetadataQueryApi metadata = mock(MetadataQueryApi.class);
  private PhysicalScopeEvidenceQueryApi evidence = mock(PhysicalScopeEvidenceQueryApi.class);
  private AgentSessionOwnerValidator owner = mock(AgentSessionOwnerValidator.class);
  private AgentChatService chat = mock(AgentChatService.class);
  private AgentTurnRepository turns = mock(AgentTurnRepository.class);
  private MessageTreeRepository messages = mock(MessageTreeRepository.class);
  private CurrentProject project = () -> Optional.of(new ProjectContext(31L,"test"));
  private SourceSemanticTaskFacade facade;
  private MockedStatic<YakSecurityContext> security;
  private MockedStatic<StpUtil> permissions;

  private PhysicalScopeEvidenceQueryApi.Evidence snapshot() {
    return new PhysicalScopeEvidenceQueryApi.Evidence(31L, "7", "warehouse", "public",
        "capture-1", "2026-10-10",
        "abcdef0123456789",
        List.of(new PhysicalScopeEvidenceQueryApi.Table("orders-key", "orders",
            "table-content", 2, List.of(
                new PhysicalScopeEvidenceQueryApi.Column("id","hash-id","BIGINT",true,"PK"),
                new PhysicalScopeEvidenceQueryApi.Column("amount","hash-amt","DECIMAL",false,"交易金额")))));
  }

  private List<SourceSemanticTaskFacade.ColumnChoice> choices() {
    return List.of(new SourceSemanticTaskFacade.ColumnChoice("orders-key",List.of("id","amount")));
  }

  @BeforeEach void setup() {
    security = mockStatic(YakSecurityContext.class);
    security.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
    permissions = mockStatic(StpUtil.class);
    when(sources.findReferences(List.of(7L)))
        .thenReturn(List.of(new DataSourceReference(7L,31L,"warehouse",DataSourceDbType.MYSQL)));
    when(evidence.readSelectedTables(List.of("orders-key"))).thenReturn(snapshot());
    facade = new SourceSemanticTaskFacade(sources,metadata,evidence,owner,chat,turns,
        messages,project,new io.yak.ops.business.agent.runtime.SourceSemanticStateBridge(
            new InMemoryAgentStateStore()),workspace.toString());
  }

  @AfterEach void close() { permissions.close(); security.close(); }

  @Test void sourcePreviewRequiresDatasourcePermissionAndCorrectProject() {
    var preview=facade.preview("7",choices());
    assertEquals(31L,preview.scope().projectId());
    assertEquals(1,preview.chunkCount());
    permissions.verify(() -> StpUtil.checkPermission(DataSourcePermissionCode.READ),atLeastOnce());
    assertThrows(IllegalArgumentException.class,() -> facade.preview("not-a-number",choices()));
    when(sources.findReferences(List.of(7L))).thenReturn(List.of());
    assertThrows(IllegalArgumentException.class,() -> facade.preview("7",choices()));
  }

  @Test void approvedPlanAndDurableOriginalTurnProduceVerifiedArtifact() throws Exception {
    var created=facade.create(new SourceSemanticTaskFacade.Create("session-1","7",
        choices(),"订单与金额业务背景"));
    assertEquals("PLANNED",created.status());
    assertTrue(Files.isRegularFile(workspace.resolve("31/42/"+created.taskId()+"/plans/PLAN.md")));
    assertTrue(created.planMarkdown().contains("订单与金额业务背景"));
    assertThrows(IllegalStateException.class,()->facade.next(created.taskId()));
    var ready=facade.approve(created.taskId(),created.planSha256());
    assertEquals("READY",ready.status());
    var submitted=facade.next(created.taskId());
    assertEquals("RUNNING",submitted.status());
    assertNotNull(submitted.activeTurnId());
    verify(chat).enqueueReservedSourceSemanticTurn(eq("session-1"),eq(submitted.activeTurnId()),
        anyString(),eq(42L),eq(31L),eq(created.taskId()));

    var turn=new AgentTurnRecord(submitted.activeTurnId(),"session-1",42L,31L,
        TurnKind.START,TurnInputCodec.encode(
            TurnInput.ofStart("user-msg","assistant-msg","bounded metadata").withSourceTask(created.taskId())),
        TurnStatus.COMPLETED,null,null,null,null,null);
    when(turns.findByTurnId(submitted.activeTurnId())).thenReturn(Optional.of(turn));
    when(messages.listBySession("session-1")).thenReturn(List.of(
        new MessageTreeNode("session-1","assistant-msg",null,"assistant",
            "## business evidence\n- 订单粒度：推测（待确认）",null,60L,true,null,null)));
    var done=facade.read(created.taskId());
    assertEquals("COMPLETED",done.status());
    assertEquals(1,done.completedChunks());
    String chunk=done.completedTurnIds().keySet().iterator().next();
    var artifact=facade.artifact(created.taskId(),chunk);
    assertEquals(submitted.activeTurnId(),artifact.turnId());
    assertTrue(artifact.markdown().contains("待确认"));
    assertEquals(done.resultDigests().get(chunk),artifact.sha256());
    assertEquals("COMPLETED",facade.read(created.taskId()).status());
  }

  @Test void sourceFingerprintDriftBlocksApprovalAndAdmission() {
    var task=facade.create(new SourceSemanticTaskFacade.Create("session-1","7",choices(),""));
    var changed=new PhysicalScopeEvidenceQueryApi.Evidence(31L,"7","warehouse",
        "public","capture-2","2026-10-10","different",
        List.of(new PhysicalScopeEvidenceQueryApi.Table("orders-key","orders",
            "different-table-hash",2,snapshot().tables().get(0).columns())));
    when(evidence.readSelectedTables(List.of("orders-key"))).thenReturn(changed);
    assertThrows(IllegalStateException.class,()->facade.approve(task.taskId(),task.planSha256()));
    verify(chat,never()).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
        anyString(),anyLong(),anyLong(),anyString());
  }

  @Test void missingOrModifiedPlanAndForgedApprovalAreRejected() throws Exception {
    var task=facade.create(new SourceSemanticTaskFacade.Create("session-1","7",choices(),""));
    assertThrows(IllegalStateException.class,()->facade.approve(task.taskId(),
        "abcdef0000000000000000000000000000000000000000000000000000000000"));
    Path file=workspace.resolve("31/42/"+task.taskId()+"/plans/PLAN.md");
    Files.writeString(file,"# Plan overwritten externally");
    assertThrows(IllegalStateException.class,()->facade.approve(task.taskId(),task.planSha256()));
    verify(chat,never()).enqueueReservedSourceSemanticTurn(anyString(),anyString(),
        anyString(),anyLong(),anyLong(),anyString());
  }
}
