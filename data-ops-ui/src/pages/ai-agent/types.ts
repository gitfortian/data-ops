// 后端契约类型统一由 services/agent 提供；此处按页面旧引用路径保持兼容别名
export type {
  AgentReport,
  AgentSession,
  ChatEventType,
  ChatTurnEvent,
  HistoryTurn,
  PagingData,
  QueryAudit,
  ReportDetail,
  ToolFeedback,
} from '@/services/agent';

export interface SessionMetaInfo {
  sessionId: string;
  title?: string | null;
}

/** 解析后的反问参数（question 必填，options 可选）。 */
export interface ClarifyPayload {
  toolCallId: string;
  toolName: string;
  question: string;
  options: string[];
}

/** 处理链路步骤：思考流式生长；调用带执行状态/耗时/输出。 */
export interface TraceStep {
  key: string;
  kind: 'think' | 'call';
  /** think=累积推理文本；call=工具名。 */
  text: string;
  toolCallId?: string;
  toolName?: string;
  running?: boolean;
  /** HITL 反问挂起：显式"等待输入"态，与 RUNNING 转圈区分（渲染顺序规范 §11.5）。 */
  waiting?: boolean;
  /** call=工具失败（TOOL_RESULT.toolStatus 非 SUCCESS，服务端权威）；驱动节点 error 态。 */
  failed?: boolean;
  /** call=工具输出文本（含执行 SQL）。 */
  resultText?: string;
  /** call=工具入参文本（权威水合后填充，SSE 帧不携带）。 */
  inputText?: string;
  /** call=失败语义归一后的真实失败原因（业务错误结构优先于模板句）。 */
  failureDetail?: string;
  /** 执行/思考耗时(ms)。 */
  durationMs?: number;
  startedAt?: number;
  /** 迭代序（服务端权威）：前端按迭代分组渲染的依据。 */
  iter?: number;
}

/** 前端消息模型。 */
export interface UIMessage {
  id: string;
  role: 'user' | 'assistant' | 'error';
  /** 回答正文（流式增量拼接）。 */
  content: string;
  /** 处理链路（DataAgent 风格时间线数据）。 */
  trace: TraceStep[];
  /** 关联的推理轮次（历史回放/水合用）。 */
  turnId?: string;
  /** Restored text whose execution identity could not be established. */
  unlinkedHistory?: boolean;
  /** Read from history with one assistant entry for this explicit turn; never set by SSE. */
  persistedHistory?: boolean;
  /** 本轮推理耗时（毫秒），TURN_FINISHED 时写入。 */
  elapsedMs?: number;
  /** 首个事件到达耗时（毫秒）。 */
  firstEventMs?: number;
  /** 本轮 Token 用量。 */
  totalTokens?: number;
  /** 错误信息。 */
  error?: string;
  /** 分类错误码（四级分发：码→文案→动作→去重）。 */
  errorCode?: string;
}

export const newSessionId = () => `sess-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

// 显式保留使用引用，避免 re-export 触发未使用告警的歧义
