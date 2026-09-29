/** Agent 服务域后端契约（与 data-ops-business-agent controller v1 对齐）。 */

/** AG-UI 标准事件类型（官方化 v2：线上帧 type 在 data 内，官方 AguiEventType 映射）。 */
export type ChatEventType =
  | 'RUN_STARTED'
  | 'TEXT_MESSAGE_START'
  | 'TEXT_MESSAGE_CONTENT'
  | 'TEXT_MESSAGE_END'
  | 'REASONING_START'
  | 'REASONING_MESSAGE_START'
  | 'REASONING_MESSAGE_CONTENT'
  | 'REASONING_MESSAGE_END'
  | 'TOOL_CALL_START'
  | 'TOOL_CALL_ARGS'
  | 'TOOL_CALL_END'
  | 'TOOL_CALL_RESULT'
  | 'STEP_STARTED'
  | 'STEP_FINISHED'
  | 'RUN_FINISHED'
  | 'RUN_ERROR'
  | 'CUSTOM';

/** yak-ops 扩展载荷（服务端权威计时/迭代序/文本分层），经官方 rawEvent 槽透传。 */
export interface RawEventExtension {
  /** 渲染顺序规范：迭代序（1 起，成功模型调用边界递增）。 */
  iter?: number | null;
  /** 文本分层：FINAL=结果事件派生全文；缺省=流式增量。 */
  phase?: string | null;
  /** 思考块已耗时（ms，服务端权威）。 */
  thinkingElapsedMs?: number | null;
  /** 工具调用发起时刻（epoch ms）。 */
  startedAt?: number | null;
  /** 工具执行耗时（ms）。 */
  durationMs?: number | null;
  /** 工具终态（SUCCESS/ERROR/INTERRUPTED/DENIED/ABORTED）。 */
  toolStatus?: string | null;
  /** 轮次总耗时（ms，服务端 claim→终态）。 */
  elapsedMs?: number | null;
  /** Token 用量。 */
  totalTokens?: number | null;
  /** 分类错误码。 */
  errorCode?: string | null;
  /** CUSTOM 事件名（clarify_requested / turn_cancelled / exceeded_max_iters）。 */
  customName?: string | null;
}

/** SSE 推送的官方 AG-UI 事件帧（官方化 v2；dispatchBlock 负责 rawEvent 摊平与字段别名映射）。 */
export interface ChatTurnEvent {
  type: ChatEventType;
  /** 官方：会话 ID（threadId=sessionId）。 */
  threadId?: string | null;
  /** 官方：轮次 ID（runId=turnId）。 */
  runId?: string | null;
  /** 官方：消息 ID（文本/推理消息）。 */
  messageId?: string | null;
  /** TEXT_MESSAGE_CONTENT / REASONING_MESSAGE_CONTENT：流式增量文本。 */
  delta?: string | null;
  /** TOOL_CALL_START / TOOL_CALL_END / TOOL_CALL_RESULT：工具调用唯一标识。 */
  toolCallId?: string | null;
  /** TOOL_CALL_START：工具名称。 */
  toolCallName?: string | null;
  /** TOOL_CALL_RESULT：工具结果内容。 */
  content?: string | null;
  /** CUSTOM：事件名（clarify_requested / turn_cancelled / exceeded_max_iters）。 */
  name?: string | null;
  /** CUSTOM：事件载荷（clarify={toolCallId,toolName,question}；exceeded=错误消息字符串）。 */
  value?: unknown;
  /** RUN_ERROR：错误消息。 */
  message?: string | null;
  /** RUN_ERROR：分类错误码（TIMEOUT/USER_ERROR/PROVIDER_ERROR/GUARD_REJECTED/GENERIC）。 */
  code?: string | null;
  /** RUN_FINISHED：outcome（{type:'success'|'interrupt'}）。 */
  outcome?: { type?: string } | null;
  /** yak-ops 扩展（服务端权威计时/迭代序/文本分层），dispatchBlock 摊平到顶层扁平字段。 */
  rawEvent?: RawEventExtension | null;
  // ---- 以下为摊平后的扁平字段（dispatchBlock 从官方字段/rawEvent 映射填充） ----
  /** 工具名称（TOOL_CALL_START）。 */
  toolName?: string | null;
  /** 工具输出文本（TOOL_CALL_RESULT.content 映射）。 */
  toolResult?: string | null;
  /** 错误消息（RUN_ERROR.message 映射）。 */
  errorMessage?: string | null;
  /** 分类错误码（RUN_ERROR.code 映射）。 */
  errorCode?: string | null;
  /** 迭代序（rawEvent.iter 摊平）。 */
  iter?: number | null;
  /** 文本分层（rawEvent.phase 摊平）。 */
  phase?: string | null;
  /** 思考块已耗时（rawEvent 摊平）。 */
  thinkingElapsedMs?: number | null;
  /** 工具发起时刻（rawEvent 摊平）。 */
  startedAt?: number | null;
  /** 工具执行耗时（rawEvent 摊平）。 */
  durationMs?: number | null;
  /** 工具终态（rawEvent 摊平）。 */
  toolStatus?: string | null;
  /** 轮次总耗时（rawEvent 摊平）。 */
  elapsedMs?: number | null;
  /** Token 用量（rawEvent 摊平）。 */
  totalTokens?: number | null;
  /** CUSTOM 事件名（官方 name 映射）。 */
  customName?: string | null;
}

/** 会话列表项。 */
export interface AgentSession {
  sessionId: string;
  title?: string | null;
  updateTime?: string;
}

/** 历史回放轮次（turnId 供前端懒加载 trace v2 权威视图，可空）。 */
export interface HistoryTurn {
  role: string;
  content: string;
  turnId?: string | null;
  trace?: HistoryTraceStep[];
}

/** 历史 trace 步骤。 */
export interface HistoryTraceStep {
  kind: 'think' | 'call';
  text: string;
  toolCallId?: string | null;
  toolName?: string | null;
  resultText?: string | null;
}

/** HITL 反问应答。 */
export interface ToolFeedback {
  toolCallId: string;
  toolName: string;
  output: string;
}

/** 轮次提交回执：订阅 GET /chat/turns/{turnId}/events 获取事件流。 */
export interface TurnSubmitted {
  turnId: string;
}

/** 查询审计条目。 */
export interface QueryAudit {
  id: number;
  sessionId: string;
  datasetId: number;
  queryId?: string | null;
  status: 'SUCCESS' | 'FAILED' | 'REJECTED' | string;
  errorMessage?: string | null;
  returnedRows?: number | null;
  truncated?: boolean | null;
  elapsedMillis?: number | null;
  createTime?: string;
}

/** 分析报告条目（列表用）。 */
export interface AgentReport {
  id: number;
  sessionId: string;
  title: string;
  createTime?: string;
  updateTime?: string;
}

/** 报告详情（含正文 Markdown）。 */
export interface ReportDetail extends AgentReport {
  content: string;
}

/** 后端分页包体。 */
export interface PagingData<T> {
  bizData: T[];
  pagination: { total: number; pageNo: number; pageSize: number; pages?: number };
}

// ---- trace v2（O2：树/时间轴/聚合/完整性/渲染投影，权威通道） ----

/** 解码后步骤载荷（信封四档：INLINE/SUMMARY_HASH/HASH_ONLY/OMITTED）。 */
export interface StepPayload {
  mode: string;
  content?: string | null;
  truncated?: boolean | null;
  size?: number | null;
  sha256?: string | null;
  messageCount?: number | null;
  preview?: string | null;
}

/** span 节点（树与平铺共用；children 空即叶子）。 */
export interface SpanNode {
  id: number;
  kind: string;
  name: string;
  status: string;
  toolCallId?: string | null;
  parentStepId?: number | null;
  attempt?: number | null;
  durationMillis?: number | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  retryCount?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  /** 失败语义归一：业务错误结构优先于 errorMessage 模板句。 */
  failureDetail?: string | null;
  request?: StepPayload | null;
  response?: StepPayload | null;
  createTime?: string | null;
  children: SpanNode[];
}

/** 归一化时间轴条目：offsetMillis 相对轮次起点（存量行可能为 null，顺序兜底）。 */
export interface TimelineEntry {
  stepId: number;
  kind: string;
  name: string;
  status: string;
  offsetMillis?: number | null;
  durationMillis?: number | null;
}

/** kind 维度聚合。 */
export interface KindAggregate {
  kind: string;
  count: number;
  totalMillis: number;
  maxMillis: number;
  avgMillis: number;
  p95Millis: number;
  promptTokens?: number | null;
  completionTokens?: number | null;
}

/** 链路完整性（未观测到 ≠ 成功，断链显式化）。 */
export interface TraceCompleteness {
  complete: boolean;
  reasons: string[];
}

/** 观测类型渲染投影（注册表驱动，前端零改动渲染新 kind）。 */
export interface KindMeta {
  kind: string;
  title: string;
  color: string;
  icon: string;
  order: number;
}

/** 轮次 trace 详情 v2。 */
export interface TurnTraceView {
  turnId: string;
  sessionId: string;
  status: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  totalTokens?: number | null;
  elapsedMillis?: number | null;
  startTime?: string | null;
  endTime?: string | null;
  tree: SpanNode[];
  spans: SpanNode[];
  timeline: TimelineEntry[];
  aggregates: KindAggregate[];
  completeness: TraceCompleteness;
  kinds: KindMeta[];
}

/** 会话级观测矩阵行：一个轮次 × 各 kind 聚合。 */
export interface TurnObservability {
  turnId: string;
  status: string;
  createTime?: string | null;
  elapsedMillis?: number | null;
  totalTokens?: number | null;
  byKind: KindAggregate[];
}

/** 运行时动态配置条目（治理面板）。 */
export interface ConfigItem {
  key: string;
  kind: 'bool' | 'int' | string;
  description: string;
  dbValue?: string | null;
}

/** 会话级观测视图（turns × kinds 矩阵）。 */
export interface SessionObservability {
  sessionId: string;
  turns: TurnObservability[];
}

// ---- 技能在线管理（与后端 SkillVO / SkillSaveRequest / SkillActiveRequest 对齐） ----

/** 技能条目（与后端 SkillVO 1:1）。 */
export interface AgentSkillItem {
  /** 技能标识（全局唯一，列表 Tag 与详情展示名来源）。 */
  skillId: string;
  /** 展示名。 */
  name: string;
  /** 一句话描述（注入提示用）。 */
  description: string;
  /** 能力标签等元数据（JSON 对象，可选）。 */
  metadata: Record<string, unknown>;
  /** 技能正文（instructions，注入 System Prompt）。 */
  content: string;
  /** 在线启停状态（后端 ENABLED/DISABLED）。 */
  enabled: boolean;
  /** 乐观版本（后端 CAS 防并发覆盖；冲突时 409）。 */
  version: number;
  createTime: string;
  updateTime: string;
}

/** 注册/更新请求体（对应后端 SkillSaveRequest）。 */
export interface AgentSkillSaveInput {
  /** 注册时必填；更新时路径带 skillId，body 同步回传（后端 @NotBlank 强制）。 */
  skillId: string;
  name: string;
  description?: string;
  metadata?: Record<string, unknown>;
  content: string;
}

/** 在线启停请求体（对应后端 SkillActiveRequest）。 */
export interface AgentSkillActiveInput {
  active: boolean;
}
