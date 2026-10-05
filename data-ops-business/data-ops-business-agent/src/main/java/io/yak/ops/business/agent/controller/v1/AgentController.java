package io.yak.ops.business.agent.controller.v1;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.agent.AgentPermissionCode;
import io.yak.ops.business.agent.conversation.AgentChatService;
import io.yak.ops.business.agent.conversation.query.AgentSessionQueryService;
import io.yak.ops.business.agent.controller.v1.dto.AgentRequests;
import io.yak.ops.business.agent.controller.v1.dto.AgentRequests.ChatTurnSubmitRequest;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.HistoryVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.KindAggregateVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.KindMetaVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.QueryAuditVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.ReportDetailVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.ReportVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.SessionVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.SessionObservabilityVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.SpanNodeVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.StepPayloadVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.TraceStepVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.TimelineEntryVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.TurnObservabilityVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.TurnSubmittedVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.TurnTraceVO;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.CompletenessVO;
import io.yak.ops.business.agent.domain.QueryAuditItem;
import io.yak.ops.business.agent.domain.ReportEntry;
import io.yak.ops.business.agent.domain.SpanNode;
import io.yak.ops.business.agent.domain.StepPayload;
import io.yak.ops.business.agent.domain.TimelineEntry;
import io.yak.ops.business.agent.domain.ToolFeedback;
import io.yak.ops.business.agent.domain.TurnTraceView;
import io.yak.ops.business.agent.report.AgentReportService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;

/** AI 分析对话接口。Controller 只进入稳定 Facade，不触碰内部角色。 */
@Tag(name = "AI 分析对话接口")
@RestController
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class AgentController {

  private final AgentChatService agentChatService;
  private final AgentSessionQueryService sessionQueryService;
  private final AgentReportService reportService;
  private final io.yak.ops.business.agent.conversation.AgentConfigManageService configManageService;

  /**
   * 提交一轮推理（提交/执行分离）：验证 + 落库后立即返回 turnId，推理由后台异步执行。
   * 事件经 {@code GET /chat/turns/{turnId}/events} 订阅获取。
   */
  @Operation(summary = "提交智能分析轮次（立即返回 turnId，后台执行）")
  @PostMapping("/chat/turns")
  @RequiresPermission(AgentPermissionCode.CHAT_RUN)
  public Result<TurnSubmittedVO> submitTurn(@Valid @RequestBody ChatTurnSubmitRequest request) {
    if (request.isResume() && request.governanceTarget() != null) {
      throw new IllegalArgumentException("恢复轮次不得替换治理对象");
    }
    String turnId =
        request.isResume()
            ? agentChatService.submitResume(
                request.sessionId(),
                request.toolResults().stream()
                    .map(input -> new ToolFeedback(input.toolCallId(), input.toolName(), input.output()))
                    .toList())
            : agentChatService.submitTurn(request.sessionId(), requireMessage(request), request.governanceTarget());
    return Result.success(new TurnSubmittedVO(turnId));
  }

  /** 订阅轮次事件流（SSE）。重连时携带 Last-Event-ID 头或 cursor 查询参数即可增量续播。 */
  @Operation(summary = "订阅轮次事件流（SSE，支持 Last-Event-ID 断线续播）")
  @GetMapping(value = "/chat/turns/{turnId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequiresPermission(AgentPermissionCode.CHAT_RUN)
  public SseEmitter turnEvents(
      @PathVariable String turnId,
      @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
      @RequestParam(name = "cursor", required = false) Long cursor,
      HttpServletResponse servletResponse) {
    // 禁用各级代理/浏览器对事件流的缓冲：identity 显式声明不做内容编码，
    // X-Accel-Buffering 针对 nginx 类反向代理，保证逐帧到达前端
    servletResponse.setHeader("X-Accel-Buffering", "no");
    servletResponse.setHeader("Cache-Control", "no-cache");
    servletResponse.setHeader("Connection", "keep-alive");
    servletResponse.setHeader("Content-Encoding", "identity");
    return agentChatService.openEventStream(turnId, lastEventId, cursor);
  }

  private String requireMessage(ChatTurnSubmitRequest request) {
    if (request.message() == null || request.message().isBlank()) {
      throw new IllegalArgumentException("消息内容不能为空");
    }
    return request.message();
  }

  @Operation(summary = "查看当前用户会话列表")
  @GetMapping("/sessions")
  @RequiresPermission(AgentPermissionCode.SESSION_READ)
  public Result<List<SessionVO>> listSessions() {
    List<SessionVO> sessions =
        sessionQueryService.listCurrentUserSessions().stream()
            .map(meta -> new SessionVO(meta.sessionId(), meta.title(), meta.updateTime()))
            .toList();
    return Result.success(sessions);
  }

  @Operation(summary = "回放会话历史")
  @GetMapping("/sessions/{sessionId}/history")
  @RequiresPermission(AgentPermissionCode.SESSION_READ)
  public Result<List<HistoryVO>> history(@PathVariable String sessionId) {
    List<HistoryVO> history =
        sessionQueryService.history(sessionId).stream()
            .map(turn -> new HistoryVO(
                turn.role(),
                turn.content(),
                turn.turnId(),
                turn.trace().stream()
                    .map(s -> new TraceStepVO(s.kind(), s.text(), s.toolCallId(), s.toolName(), s.resultText()))
                    .toList()))
            .toList();
    return Result.success(history);
  }

  @Operation(summary = "查看轮次 trace 详情 v2（树/时间轴/聚合/完整性/渲染投影，服务端权威计时）")
  @GetMapping("/turns/{turnId}/trace")
  @RequiresPermission(AgentPermissionCode.SESSION_READ)
  public Result<TurnTraceVO> turnTrace(@PathVariable String turnId) {
    TurnTraceView view = sessionQueryService.turnTrace(turnId);
    return Result.success(toTurnTraceVO(view));
  }

  @Operation(summary = "运行时动态配置清单（登记键位 + 当前值；null=使用种子默认值）")
  @GetMapping("/config")
  @RequiresPermission(AgentPermissionCode.CONFIG_READ)
  public Result<List<io.yak.ops.business.agent.controller.v1.vo.AgentViews.ConfigItemVO>> listConfig() {
    return Result.success(configManageService.list().stream()
        .map(item -> new io.yak.ops.business.agent.controller.v1.vo.AgentViews.ConfigItemVO(
            item.key(), item.kind(), item.description(), item.dbValue(),
            item.effectiveValue(), item.valueSource(), item.updateMode()))
        .toList());
  }

  @Operation(summary = "更新运行时动态配置（热生效；value 空串回退种子默认值）")
  @PutMapping("/config/{key}")
  @RequiresPermission(AgentPermissionCode.CONFIG_MANAGE)
  public Result<Boolean> updateConfig(
      @PathVariable String key,
      @jakarta.validation.Valid @RequestBody io.yak.ops.business.agent.controller.v1.dto.AgentRequests.ConfigUpdateRequest request) {
    configManageService.update(key, request.value());
    return Result.success(true);
  }

  @Operation(summary = "会话级观测矩阵（turns × kinds：慢轮次/重试异常/失败集中定位）")
  @GetMapping("/sessions/{sessionId}/observability")
  @RequiresPermission(AgentPermissionCode.SESSION_READ)
  public Result<SessionObservabilityVO> sessionObservability(@PathVariable String sessionId) {
    var view = sessionQueryService.sessionObservability(sessionId);
    return Result.success(new SessionObservabilityVO(
        view.sessionId(),
        view.turns().stream()
            .map(row -> new TurnObservabilityVO(
                row.turnId(),
                row.status(),
                row.createTime(),
                row.elapsedMillis(),
                row.totalTokens(),
                row.byKind().stream().map(AgentController::toKindAggregateVO).toList()))
            .toList()));
  }

  /** domain read model -> VO（v2 六视图 1:1 投影，载荷已在组装器解码）。 */
  private static TurnTraceVO toTurnTraceVO(TurnTraceView view) {
    return new TurnTraceVO(
        view.turnId(),
        view.sessionId(),
        view.status(),
        view.errorCode(),
        view.errorMessage(),
        view.totalTokens(),
        view.elapsedMillis(),
        view.createTime(),
        view.startTime(),
        view.endTime(),
        view.tree().stream().map(AgentController::toSpanNodeVO).toList(),
        view.spans().stream().map(AgentController::toSpanNodeVO).toList(),
        view.timeline().stream()
            .map(t -> new TimelineEntryVO(
                t.stepId(), t.kind(), t.name(), t.status(), t.offsetMillis(), t.durationMillis()))
            .toList(),
        view.aggregates().stream().map(AgentController::toKindAggregateVO).toList(),
        new CompletenessVO(view.completeness().complete(), view.completeness().reasons()),
        view.kinds().stream()
            .map(k -> new KindMetaVO(k.kind(), k.title(), k.color(), k.icon(), k.order()))
            .toList());
  }

  private static SpanNodeVO toSpanNodeVO(SpanNode node) {
    return new SpanNodeVO(
        node.id(),
        node.kind(),
        node.name(),
        node.status(),
        node.toolCallId(),
        node.parentStepId(),
        node.attempt(),
        node.durationMillis(),
        node.promptTokens(),
        node.completionTokens(),
        node.retryCount(),
        node.errorCode(),
        node.errorMessage(),
        node.failureDetail(),
        toStepPayloadVO(node.request()),
        toStepPayloadVO(node.response()),
        node.createTime(),
        node.children().stream().map(AgentController::toSpanNodeVO).toList());
  }

  private static StepPayloadVO toStepPayloadVO(StepPayload payload) {
    return payload == null ? null : new StepPayloadVO(
        payload.mode(),
        payload.content(),
        payload.truncated(),
        payload.size(),
        payload.sha256(),
        payload.messageCount(),
        payload.preview());
  }

  private static KindAggregateVO toKindAggregateVO(io.yak.ops.business.agent.domain.KindAggregate aggregate) {
    return new KindAggregateVO(
        aggregate.kind(),
        aggregate.count(),
        aggregate.totalMillis(),
        aggregate.maxMillis(),
        aggregate.avgMillis(),
        aggregate.p95Millis(),
        aggregate.promptTokens(),
        aggregate.completionTokens());
  }

  @Operation(summary = "删除会话（报告保留）")
  @DeleteMapping("/sessions/{sessionId}")
  @RequiresPermission(AgentPermissionCode.SESSION_DELETE)
  public Result<Boolean> deleteSession(@PathVariable String sessionId) {
    agentChatService.deleteSession(sessionId);
    return Result.success(true);
  }

  @Operation(summary = "停止生成（取消当前推理并释放会话）")
  @PostMapping("/sessions/{sessionId}/cancel")
  @RequiresPermission(AgentPermissionCode.CHAT_RUN)
  public Result<Boolean> cancel(@PathVariable String sessionId) {
    agentChatService.cancel(sessionId);
    return Result.success(true);
  }

  @Operation(summary = "查询审计分页")
  @PostMapping("/queries/page")
  @RequiresPermission(AgentPermissionCode.SESSION_READ)
  public Result<PagingData<QueryAuditVO>> auditPage(
      @Valid @RequestBody AgentRequests.QueryAuditPageRequest request) {
    PageData<QueryAuditItem> page =
        sessionQueryService.auditPage(
            request.pageNo(), request.pageSize(), request.sessionId(), request.datasetId());
    return Result.success(PagingData.from(page.map(this::toAuditVO)));
  }

  private QueryAuditVO toAuditVO(QueryAuditItem item) {
    return new QueryAuditVO(
        item.id(),
        item.sessionId(),
        item.datasetId(),
        item.queryId(),
        item.status(),
        item.errorMessage(),
        item.returnedRows(),
        item.truncated(),
        item.elapsedMillis(),
        item.createTime());
  }

  @Operation(summary = "重命名会话")
  @PutMapping("/sessions/{sessionId}")
  @RequiresPermission(AgentPermissionCode.SESSION_UPDATE)
  public Result<Boolean> renameSession(
      @PathVariable String sessionId,
      @Valid @RequestBody AgentRequests.RenameSessionRequest request) {
    agentChatService.renameSession(sessionId, request.title());
    return Result.success(true);
  }

  @Operation(summary = "分页查询分析报告")
  @PostMapping("/reports/page")
  @RequiresPermission(AgentPermissionCode.REPORT_READ)
  public Result<PagingData<ReportVO>> pageReports(
      @Valid @RequestBody AgentRequests.ReportPageRequest request) {
    PageData<ReportEntry> page =
        reportService.page(request.pageNo(), request.pageSize(), request.keyword());
    return Result.success(PagingData.from(page.map(this::toReportVO)));
  }

  @Operation(summary = "查看报告详情")
  @GetMapping("/reports/{reportId}")
  @RequiresPermission(AgentPermissionCode.REPORT_READ)
  public Result<ReportDetailVO> reportDetail(@PathVariable Long reportId) {
    ReportEntry entry = reportService.detail(reportId);
    String content = reportService.content(reportId);
    return Result.success(new ReportDetailVO(entry.id(), entry.sessionId(), entry.title(), content, entry.createTime(), entry.updateTime()));
  }

  @Operation(summary = "删除分析报告")
  @DeleteMapping("/reports/{reportId}")
  @RequiresPermission(AgentPermissionCode.REPORT_DELETE)
  public Result<Boolean> deleteReport(@PathVariable Long reportId) {
    reportService.delete(reportId);
    return Result.success(true);
  }

  private ReportVO toReportVO(ReportEntry entry) {
    return new ReportVO(entry.id(), entry.sessionId(), entry.title(), entry.createTime(), entry.updateTime());
  }
}
