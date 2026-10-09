package io.yak.ops.business.approval.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.approval.application.ApprovalService.ApprovalDetailView;
import io.yak.ops.business.approval.application.ApprovalService.TodoView;
import io.yak.ops.business.approval.dao.mapper.ApprovalFlowMapper;
import io.yak.ops.business.approval.dao.mapper.ApprovalInstanceMapper;
import io.yak.ops.business.approval.dao.mapper.ApprovalStepMapper;
import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.business.approval.registry.ApprovalFlowRegistry;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.approval.dao.model.ApprovalFlowPO;
import io.yak.ops.business.approval.dao.model.ApprovalInstancePO;
import io.yak.ops.business.approval.dao.model.ApprovalStepPO;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import io.yak.ops.common.enums.approval.ApprovalInstanceStatus;
import io.yak.ops.common.enums.approval.ApprovalStepStatus;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

/** 核心闭环单测(ticket 103):两级全链路、拒绝/撤销终态清理、乐观并发、在途唯一、回调纪律。 */
class ApprovalServiceTest {

  private static final String FLOW_CODE = "MODEL_PUBLISH";
  private static final String TWO_LEVELS =
      "[{\"level\":1,\"approvers\":[\"alice\",\"bob\"]},{\"level\":2,\"approvers\":[\"carol\"]}]";

  private ApprovalFlowMapper flowMapper;
  private ApprovalInstanceMapper instanceMapper;
  private ApprovalStepMapper stepMapper;
  private ApprovalFlowRegistry registry;
  private ApprovalFlowHandler handler;
  private AuditOperationHandle auditHandle;
  private ApprovalService service;

  @BeforeEach
  void setUp() {
    for (Class<?> po : List.of(ApprovalFlowPO.class, ApprovalInstancePO.class,
        ApprovalStepPO.class)) {
      TableInfoHelper.initTableInfo(
          new MapperBuilderAssistant(new MybatisConfiguration(), ""), po);
    }
    flowMapper = mock(ApprovalFlowMapper.class);
    instanceMapper = Mockito.mock(ApprovalInstanceMapper.class);
    stepMapper = Mockito.mock(ApprovalStepMapper.class);
    registry = mock(ApprovalFlowRegistry.class);
    handler = mock(ApprovalFlowHandler.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    auditHandle = mock(AuditOperationHandle.class);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(auditHandle);
    lenient().when(registry.find(eq(FLOW_CODE))).thenReturn(java.util.Optional.of(handler));
    lenient().when(registry.require(eq(FLOW_CODE))).thenReturn(handler);
    service = new ApprovalService(currentProject, flowMapper, instanceMapper, stepMapper,
        registry, auditService);
  }

  // ---------- 发起 ----------

  @Test
  void submitSnapshotsLevelsAndActivatesFirstLevel() {
    when(flowMapper.selectOne(any())).thenReturn(flow(true));
    when(instanceMapper.selectCount(any())).thenReturn(0L);

    ApprovalInstanceView view = service.submit(
        new ApprovalSubmitCommand(FLOW_CODE, "MODEL", "42", "发布订单模型", "{}", "tom"));

    ArgumentCaptor<ApprovalInstancePO> instance = ArgumentCaptor.forClass(ApprovalInstancePO.class);
    verify(instanceMapper).insert(instance.capture());
    ApprovalInstancePO po = instance.getValue();
    assertEquals(ApprovalInstanceStatus.PENDING.name(), po.getStatus());
    assertEquals("Y", po.getActiveFlag());
    assertEquals(1, po.getCurrentLevel());
    assertEquals("模型发布", po.getFlowName());

    ArgumentCaptor<ApprovalStepPO> steps = ArgumentCaptor.forClass(ApprovalStepPO.class);
    verify(stepMapper, times(3)).insert(steps.capture());
    List<ApprovalStepPO> rows = steps.getAllValues();
    assertEquals(ApprovalStepStatus.PENDING.name(), rows.get(0).getStatus());
    assertEquals(ApprovalStepStatus.PENDING.name(), rows.get(1).getStatus());
    assertEquals(ApprovalStepStatus.WAITING.name(), rows.get(2).getStatus());
    assertEquals(2, rows.get(2).getLevelNo());
    assertEquals("tom", view.applicant());
  }

  @Test
  void submitGuardsFlowAndHandlerAndDuplicatesAndPayload() {
    // 49001 流程未配置
    when(flowMapper.selectOne(any())).thenReturn(null);
    assertError(ApprovalErrorCode.FLOW_NOT_FOUND, () -> service.submit(submitCmd()));
    // 49002 停用
    when(flowMapper.selectOne(any())).thenReturn(flow(false));
    assertError(ApprovalErrorCode.FLOW_DISABLED, () -> service.submit(submitCmd()));
    // 49007 无 handler
    when(flowMapper.selectOne(any())).thenReturn(flow(true));
    doThrow(new ApprovalException(ApprovalErrorCode.HANDLER_NOT_REGISTERED, FLOW_CODE))
        .when(registry).require(FLOW_CODE);
    assertError(ApprovalErrorCode.HANDLER_NOT_REGISTERED, () -> service.submit(submitCmd()));
    Mockito.reset(registry);
    // 49003 在途重复
    when(instanceMapper.selectCount(any())).thenReturn(1L);
    assertError(ApprovalErrorCode.DUPLICATE_IN_FLIGHT, () -> service.submit(submitCmd()));
    // 49010 payload 超限
    when(instanceMapper.selectCount(any())).thenReturn(0L);
    String big = "x".repeat(ApprovalService.MAX_PAYLOAD_BYTES + 1);
    assertError(ApprovalErrorCode.PAYLOAD_TOO_LARGE, () -> service.submit(
        new ApprovalSubmitCommand(FLOW_CODE, "MODEL", "42", "t", big, "tom")));
    verify(instanceMapper, never()).insert(any(ApprovalInstancePO.class));
  }

  @Test
  void simultaneousDuplicateSubmissionFailsBeforeStepsOrSuccessAudit() {
    when(flowMapper.selectOne(any())).thenReturn(flow(true));
    when(instanceMapper.selectCount(any())).thenReturn(0L);
    when(instanceMapper.insert(any(ApprovalInstancePO.class)))
        .thenThrow(new DuplicateKeyException("in-flight approval unique index"));

    assertError(ApprovalErrorCode.DUPLICATE_IN_FLIGHT, () -> service.submit(submitCmd()));
    verify(stepMapper, never()).insert(any(ApprovalStepPO.class));
    verify(instanceMapper).insert(any(ApprovalInstancePO.class));
    verify(handler, never()).onApproved(any());
  }

  // ---------- 两级全链路 ----------

  @Test
  void twoLevelApproveChainThenCallbackOnce() {
    when(instanceMapper.selectById(7L)).thenReturn(instance(ApprovalInstanceStatus.PENDING, 1));
    when(stepMapper.selectOne(any()))
        .thenReturn(step(11L, 1, "alice", ApprovalStepStatus.PENDING))
        .thenReturn(step(13L, 2, "carol", ApprovalStepStatus.PENDING));
    when(stepMapper.selectList(any())).thenReturn(List.of(
        step(11L, 1, "alice", ApprovalStepStatus.APPROVED),
        step(12L, 1, "bob", ApprovalStepStatus.SKIPPED),
        step(13L, 2, "carol", ApprovalStepStatus.PENDING)));
    when(stepMapper.update(any(), any())).thenReturn(1);
    when(instanceMapper.update(any(), any())).thenReturn(1);

    ApprovalInstanceView first = service.approve(7L, null, "alice");
    assertEquals(ApprovalInstanceStatus.PENDING.name(), first.status());
    assertEquals(2, first.currentLevel());
    verify(handler, never()).onApproved(any());
    // 一级通过:自身置批 + 同侪 SKIPPED + 次级 WAITING→PENDING = 3 次 step 更新
    verify(stepMapper, times(3)).update(any(), any());

    ApprovalInstanceView second = service.approve(7L, "LGTM", "carol");
    assertEquals(ApprovalInstanceStatus.APPROVED.name(), second.status());
    // 终态再加:自身置批 + 同级清理 + 剩余 WAITING/PENDING → SKIPPED,累计 6 次
    verify(stepMapper, times(6)).update(any(), any());
    ArgumentCaptor<ApprovalDecision> decision = ArgumentCaptor.forClass(ApprovalDecision.class);
    verify(handler).onApproved(decision.capture());
    assertEquals("42", decision.getValue().bizId());
    assertEquals("carol", decision.getValue().lastApprover());
    assertEquals("LGTM", decision.getValue().comment());
    verify(handler, never()).onRejected(any());
  }

  // ---------- 拒绝 ----------

  @Test
  void rejectRequiresCommentAndFinalizesWithCallback() {
    assertError(ApprovalErrorCode.REJECT_COMMENT_REQUIRED,
        () -> service.reject(7L, "  ", "alice"));

    when(instanceMapper.selectById(7L)).thenReturn(instance(ApprovalInstanceStatus.PENDING, 1));
    when(stepMapper.selectOne(any()))
        .thenReturn(step(11L, 1, "alice", ApprovalStepStatus.PENDING));
    when(stepMapper.update(any(), any())).thenReturn(1);
    when(instanceMapper.update(any(), any())).thenReturn(1);

    ApprovalInstanceView view = service.reject(7L, "口径不对", "alice");

    assertEquals(ApprovalInstanceStatus.REJECTED.name(), view.status());
    ArgumentCaptor<ApprovalDecision> decision = ArgumentCaptor.forClass(ApprovalDecision.class);
    verify(handler).onRejected(decision.capture());
    assertEquals("口径不对", decision.getValue().comment());
    verify(handler, never()).onApproved(any());
  }

  // ---------- 撤销 ----------

  @Test
  void cancelOnlyByApplicantAndSkipsRemaining() {
    ApprovalInstancePO running = instance(ApprovalInstanceStatus.PENDING, 1);
    when(instanceMapper.selectById(7L)).thenReturn(running);
    when(stepMapper.update(any(), any())).thenReturn(1);
    when(stepMapper.selectList(any())).thenReturn(List.of());
    when(instanceMapper.update(any(), any())).thenReturn(1);

    assertError(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
        () -> service.cancel(7L, "not-tom", null));

    service.cancel(7L, "tom", "误发起");
    assertEquals(ApprovalInstanceStatus.CANCELED.name(), running.getStatus());
    assertEquals("误发起", running.getCancelReason());
    assertEquals("误发起", service.detail(7L, "tom", false).cancelReason());
    ArgumentCaptor<ApprovalDecision> decision = ArgumentCaptor.forClass(ApprovalDecision.class);
    verify(handler).onCanceled(decision.capture());
    assertNull(decision.getValue().lastApprover());
    // 终态清理:剩余 WAITING/PENDING → SKIPPED(1 次 step 批量更新)
    verify(stepMapper).update(any(), any());
  }

  // ---------- 并发与越权 ----------

  @Test
  void foreignProjectInstanceRejectsReadApprovalAndCancellationBeforeAnyStepWrite() {
    ApprovalInstancePO foreign = instance(ApprovalInstanceStatus.PENDING, 1);
    foreign.setProjectId(88L);
    when(instanceMapper.selectById(7L)).thenReturn(foreign);

    assertError(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
        () -> service.detail(7L, "tom", true));
    assertError(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
        () -> service.approve(7L, null, "alice"));
    assertError(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
        () -> service.cancel(7L, "tom", null));
    verify(stepMapper, never()).update(any(), any());
    verify(instanceMapper, never()).update(any(), any());
    verify(handler, never()).onApproved(any());
  }

  @Test
  void doubleClickOnlyOneSucceeds() {
    when(instanceMapper.selectById(7L)).thenReturn(instance(ApprovalInstanceStatus.PENDING, 1));
    when(stepMapper.selectOne(any()))
        .thenReturn(step(11L, 1, "alice", ApprovalStepStatus.PENDING));
    when(stepMapper.update(any(), any())).thenReturn(0); // 条件更新 0 行 = 已被他人处理

    assertError(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR, () -> service.approve(7L, null, "alice"));
    verify(instanceMapper, never()).update(any(), any());
    verify(handler, never()).onApproved(any());
  }

  @Test
  void notCurrentApproverGets49004() {
    when(instanceMapper.selectById(7L)).thenReturn(instance(ApprovalInstanceStatus.PENDING, 1));
    when(stepMapper.selectOne(any())).thenReturn(null);

    assertError(ApprovalErrorCode.NOT_CURRENT_APPROVER, () -> service.approve(7L, null, "eve"));
  }

  @Test
  void terminalInstanceRejectsFurtherActions() {
    when(instanceMapper.selectById(7L)).thenReturn(instance(ApprovalInstanceStatus.APPROVED, 2));

    assertError(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR, () -> service.approve(7L, null, "alice"));
    assertError(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
        () -> service.cancel(7L, "tom", null));
  }

  @Test
  void callbackFailurePropagatesAsVisibleError() {
    when(instanceMapper.selectById(7L)).thenReturn(instance(ApprovalInstanceStatus.PENDING, 2));
    when(stepMapper.selectOne(any()))
        .thenReturn(step(13L, 2, "carol", ApprovalStepStatus.PENDING));
    when(stepMapper.selectList(any())).thenReturn(List.of(
        step(11L, 1, "alice", ApprovalStepStatus.APPROVED),
        step(13L, 2, "carol", ApprovalStepStatus.PENDING)));
    when(stepMapper.update(any(), any())).thenReturn(1);
    when(instanceMapper.update(any(), any())).thenReturn(1);
    doThrow(new IllegalStateException("业务侧发布失败")).when(handler).onApproved(any());

    ApprovalException ex = assertThrows(ApprovalException.class,
        () -> service.approve(7L, null, "carol"));
    assertEquals(ApprovalErrorCode.CALLBACK_FAILED, ex.getErrorCode());
    assertTrue(ex.getUserMessage().contains("业务侧发布失败"));
    verify(auditHandle).failure(eq("APPROVAL_APPROVE_FAILED"), any(ApprovalException.class));
  }

  @Test
  void missingHandlerLeavesTerminalActionsPendingAndAuditsFailure() {
    when(instanceMapper.selectById(7L)).thenReturn(instance(ApprovalInstanceStatus.PENDING, 2));
    when(stepMapper.selectOne(any()))
        .thenReturn(step(13L, 2, "carol", ApprovalStepStatus.PENDING));
    when(stepMapper.selectList(any())).thenReturn(List.of(
        step(11L, 1, "alice", ApprovalStepStatus.APPROVED),
        step(13L, 2, "carol", ApprovalStepStatus.PENDING)));
    doThrow(new ApprovalException(ApprovalErrorCode.HANDLER_NOT_REGISTERED, FLOW_CODE))
        .when(registry).require(FLOW_CODE);

    assertError(ApprovalErrorCode.HANDLER_NOT_REGISTERED,
        () -> service.approve(7L, null, "carol"));

    verify(stepMapper, never()).update(any(), any());
    verify(instanceMapper, never()).update(any(), any());
    verify(auditHandle).failure(eq("APPROVAL_APPROVE_FAILED"), any(ApprovalException.class));
  }

  @Test
  void byBizLookupIsVisibleOnlyToApplicantApproverOrManager() {
    when(instanceMapper.selectList(any())).thenReturn(List.of(
        instance(ApprovalInstanceStatus.PENDING, 1)));
    when(stepMapper.selectCount(any())).thenReturn(1L, 0L);

    assertEquals(7L, service.findVisible(FLOW_CODE, "MODEL", "42", "tom", false).id());
    assertEquals(7L, service.findVisible(FLOW_CODE, "MODEL", "42", "alice", false).id());
    assertError(ApprovalErrorCode.NOT_INVOLVED,
        () -> service.findVisible(FLOW_CODE, "MODEL", "42", "eve", false));
    assertEquals(7L, service.findVisible(FLOW_CODE, "MODEL", "42", "admin", true).id());
    verify(stepMapper, times(2)).selectCount(any());
  }

  // ---------- 查询 ----------

  @Test
  void detailVisibilityAndTodoMapping() {
    ApprovalInstancePO running = instance(ApprovalInstanceStatus.PENDING, 1);
    when(instanceMapper.selectById(7L)).thenReturn(running);
    when(stepMapper.selectList(any())).thenReturn(List.of(
        step(11L, 1, "alice", ApprovalStepStatus.PENDING),
        step(13L, 2, "carol", ApprovalStepStatus.WAITING)));

    assertError(ApprovalErrorCode.NOT_INVOLVED, () -> service.detail(7L, "eve", false));
    ApprovalDetailView asAlice = service.detail(7L, "alice", false);
    assertEquals(2, asAlice.steps().size());
    assertNotNull(service.detail(7L, "admin", true));

    Page<ApprovalStepPO> page = new Page<>(1, 20);
    page.setRecords(List.of(step(11L, 1, "alice", ApprovalStepStatus.PENDING)));
    page.setTotal(1);
    when(stepMapper.selectPage(any(), any())).thenReturn(page);
    when(instanceMapper.selectList(any())).thenReturn(List.of(running));
    List<TodoView> todo = service.todo("alice", 1, 20).records();
    assertEquals(1, todo.size());
    assertEquals(7L, todo.get(0).instance().id());
    assertEquals(1, todo.get(0).levelNo());
  }

  @Test
  void todoAndHandledNeverProjectForeignOrDeletedInstances() {
    ApprovalInstancePO foreign = instance(ApprovalInstanceStatus.PENDING, 1);
    foreign.setProjectId(99L);
    ApprovalInstancePO deleted = instance(ApprovalInstanceStatus.PENDING, 1);
    deleted.setId(8L);
    deleted.setDeleted(true);
    ApprovalInstancePO valid = instance(ApprovalInstanceStatus.PENDING, 1);
    valid.setId(9L);

    ApprovalStepPO foreignStep = step(11L, 1, "alice", ApprovalStepStatus.PENDING);
    ApprovalStepPO deletedStep = step(12L, 1, "alice", ApprovalStepStatus.PENDING);
    deletedStep.setInstanceId(8L);
    ApprovalStepPO validStep = step(13L, 1, "alice", ApprovalStepStatus.PENDING);
    validStep.setInstanceId(9L);
    Page<ApprovalStepPO> steps = new Page<>(1, 20);
    steps.setRecords(List.of(foreignStep, deletedStep, validStep));
    steps.setTotal(3);
    when(stepMapper.selectPage(any(), any())).thenReturn(steps);
    // A corrupt/misconfigured query response must fail closed at the projection boundary.
    when(instanceMapper.selectList(any())).thenReturn(List.of(foreign, deleted, valid));

    List<TodoView> todo = service.todo("alice", 1, 20).records();
    List<ApprovalService.HandledView> handled = service.handled("alice", 1, 20).records();
    assertEquals(List.of(9L), todo.stream().map(v -> v.instance().id()).toList());
    assertEquals(List.of(9L), handled.stream().map(v -> v.instance().id()).toList());

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<LambdaQueryWrapper> instanceQueries =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(instanceMapper, times(2)).selectList(instanceQueries.capture());
    for (LambdaQueryWrapper<?> query : instanceQueries.getAllValues()) {
      assertTrue(query.getSqlSegment().contains("project_id"));
      assertTrue(query.getSqlSegment().contains("deleted"));
      assertTrue(query.getSqlSegment().contains("id IN"));
      assertTrue(query.getParamNameValuePairs().containsValue(1L));
      assertTrue(query.getParamNameValuePairs().containsValue(false));
    }
  }

  @Test
  void findByBizQueriesAlwaysExcludeDeletedInstancesInsideProject() {
    when(instanceMapper.selectList(any())).thenReturn(List.of());
    assertNull(service.find(FLOW_CODE, "MODEL", "42"));

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<LambdaQueryWrapper> instanceQueries =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(instanceMapper, times(2)).selectList(instanceQueries.capture());
    for (LambdaQueryWrapper<?> query : instanceQueries.getAllValues()) {
      assertTrue(query.getSqlSegment().contains("project_id"));
      assertTrue(query.getSqlSegment().contains("deleted"));
      assertTrue(query.getParamNameValuePairs().containsValue(1L));
      assertTrue(query.getParamNameValuePairs().containsValue(false));
    }
  }

  @Test
  void findByBizPrefersInFlightThenLatest() {
    ApprovalInstancePO running = instance(ApprovalInstanceStatus.PENDING, 1);
    // 在途优先:第一次查询(在途)命中即返回,不再查最近一单
    when(instanceMapper.selectList(any())).thenReturn(List.of(running));
    assertEquals(7L, service.find(FLOW_CODE, "MODEL", "42").id());

    // 无在途 → 最近一单
    when(instanceMapper.selectList(any()))
        .thenReturn(List.of())
        .thenReturn(List.of(running));
    assertEquals(7L, service.find(FLOW_CODE, "MODEL", "42").id());

    // 从未发起 → null
    when(instanceMapper.selectList(any())).thenReturn(List.of());
    assertNull(service.find(FLOW_CODE, "MODEL", "42"));
  }

  // ---------- 夹具 ----------

  private ApprovalSubmitCommand submitCmd() {
    return new ApprovalSubmitCommand(FLOW_CODE, "MODEL", "42", "发布订单模型", "{}", "tom");
  }

  private void assertError(ApprovalErrorCode code, Runnable action) {
    ApprovalException ex = assertThrows(ApprovalException.class, action::run);
    assertEquals(code, ex.getErrorCode(), () -> "expect " + code + ", got " + ex.getUserMessage());
  }

  private static ApprovalFlowPO flow(boolean enabled) {
    ApprovalFlowPO po = new ApprovalFlowPO();
    po.setId(3L);
    po.setProjectId(1L);
    po.setFlowCode(FLOW_CODE);
    po.setFlowName("模型发布");
    po.setStepsJson(TWO_LEVELS);
    po.setEnabled(enabled);
    po.setDeleted(false);
    return po;
  }

  private static ApprovalInstancePO instance(ApprovalInstanceStatus status, int currentLevel) {
    ApprovalInstancePO po = new ApprovalInstancePO();
    po.setId(7L);
    po.setProjectId(1L);
    po.setFlowCode(FLOW_CODE);
    po.setFlowName("模型发布");
    po.setBizType("MODEL");
    po.setBizId("42");
    po.setTitle("发布订单模型");
    po.setPayloadJson("{}");
    po.setApplicant("tom");
    po.setStatus(status.name());
    po.setCurrentLevel(currentLevel);
    po.setActiveFlag(status == ApprovalInstanceStatus.PENDING ? "Y" : null);
    po.setDeleted(false);
    return po;
  }

  private static ApprovalStepPO step(Long id, int levelNo, String approver,
      ApprovalStepStatus status) {
    ApprovalStepPO po = new ApprovalStepPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setInstanceId(7L);
    po.setLevelNo(levelNo);
    po.setApprover(approver);
    po.setStatus(status.name());
    po.setDeleted(false);
    return po;
  }
}
