import HttpUtils, { type HttpRequestOptions } from '@/utils/HttpUtils';
import { applyCurrentProjectHeader } from '@/utils/security/projectContext';
import type {
  AgentReport,
  AgentSession,
  AgentSkillItem,
  AgentSkillSaveInput,
  ChatTurnEvent,
  ConfigItem,
  HistoryTurn,
  PagingData,
  QueryAudit,
  ReportDetail,
  SessionObservability,
  ToolFeedback,
  TurnSubmitted,
  TurnTraceView,
} from './types';

/**
 * Agent API 基础路径：
 * - 开发环境：从 window.location 自动推导同主机 8080 端口，绕过 dev server 代理的 SSE 缓冲
 * - 生产环境：留空走同源 Nginx 代理
 */
const deriveAgentBase = (): string => {
  // 开发环境自动推导：同协议 + 同主机 + 8080 端口，避免硬编码 localhost/IP
  if (process.env.NODE_ENV === 'development' && typeof window !== 'undefined') {
    const { protocol, hostname } = window.location;
    return `${protocol}//${hostname}:8080`;
  }
  return '';
};

const AGENT_BASE = deriveAgentBase();
const PREFIX = `${AGENT_BASE}/api/v1/agent`;

const INCLUDE_CREDENTIALS: RequestInit = { credentials: 'include' };

/**
 * 技能接口请求选项：跨源直连带 Cookie；业务错误（400/404/409）关闭全局兜底通知，
 * 由页面运行时按 code 分流展示（文档 §2.2：注册重复/乐观冲突/技能不存在各配专属文案+动作）。
 */
const SKILL_REQUEST_OPTIONS: HttpRequestOptions = {
  credentials: 'include',
  skipErrorHandler: true,
};

export const agentSessionApi = {
  list: () => HttpUtils.getData<AgentSession[]>(`${PREFIX}/sessions`, INCLUDE_CREDENTIALS),
  continuation: (sessionId: string) =>
    HttpUtils.getData<import('./continuation').SessionContinuation>(
      `${PREFIX}/sessions/${encodeURIComponent(sessionId)}/continuation`, INCLUDE_CREDENTIALS),
  history: (sessionId: string) =>
    HttpUtils.getData<HistoryTurn[]>(
      `${PREFIX}/sessions/${encodeURIComponent(sessionId)}/history`,
      INCLUDE_CREDENTIALS,
    ),
  /** 轮次 trace v2 权威视图（树/时间轴/聚合/完整性/渲染投影）。 */
  trace: (turnId: string) =>
    HttpUtils.getData<TurnTraceView>(`${PREFIX}/turns/${encodeURIComponent(turnId)}/trace`, INCLUDE_CREDENTIALS),
  /** 会话级观测矩阵（turns × kinds）。 */
  observability: (sessionId: string) =>
    HttpUtils.getData<SessionObservability>(
      `${PREFIX}/sessions/${encodeURIComponent(sessionId)}/observability`,
      INCLUDE_CREDENTIALS,
    ),
  rename: (sessionId: string, title: string) =>
    HttpUtils.putData<boolean>(`${PREFIX}/sessions/${encodeURIComponent(sessionId)}`, { title }, INCLUDE_CREDENTIALS),
  remove: (sessionId: string) =>
    HttpUtils.deleteData<boolean>(`${PREFIX}/sessions/${encodeURIComponent(sessionId)}`, INCLUDE_CREDENTIALS),
  /** 停止生成：后台取消推理并收敛轮次终态。 */
  cancel: (sessionId: string) =>
    fetch(`${PREFIX}/sessions/${encodeURIComponent(sessionId)}/cancel`, {
      method: 'POST',
      headers: applyCurrentProjectHeader(`${PREFIX}/sessions/${encodeURIComponent(sessionId)}/cancel`, {}),
      credentials: 'include',
      keepalive: true,
    }),
};

export const agentConfigApi = {
  list: () => HttpUtils.getData<ConfigItem[]>(`${PREFIX}/config`, INCLUDE_CREDENTIALS),
  update: (key: string, value: string) =>
    HttpUtils.putData<boolean>(`${PREFIX}/config/${encodeURIComponent(key)}`, { value }, INCLUDE_CREDENTIALS),
};

export interface TurnSubmitPayload {
  sessionId: string;
  message?: string;
  toolResults?: ToolFeedback[];
  governanceTarget?: import('./governance').GovernanceTarget;
}

export const agentChatApi = {
  cancelTurn: (turnId: string) =>
    HttpUtils.postData<boolean>(`${PREFIX}/chat/turns/${encodeURIComponent(turnId)}/cancel`, {}, INCLUDE_CREDENTIALS),
  /**
   * 提交一轮推理：仅入队立即返回 turnId，推理由后端异步执行。
   * 携带 toolResults 视为 HITL 反问恢复（同一轮续跑）。
   */
  submit: async (payload: TurnSubmitPayload): Promise<TurnSubmitted> => {
    const response = await fetch(`${PREFIX}/chat/turns`, {
      method: 'POST',
      headers: applyCurrentProjectHeader(`${PREFIX}/chat/turns`, {
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(payload),
      credentials: 'include',
    });
    if (!response.ok) {
      let detail = `HTTP ${response.status}`;
      try {
        const envelope = await response.json();
        detail = envelope?.message || detail;
      } catch {
        // 非 JSON 错误体，保留状态码信息
      }
      throw new Error(detail);
    }
    const envelope = (await response.json()) as { bizData?: TurnSubmitted; data?: TurnSubmitted };
    const submitted = envelope.bizData ?? envelope.data;
    if (!submitted?.turnId) {
      throw new Error('提交响应缺少 turnId');
    }
    return submitted;
  },
};

export interface TurnStreamHandler {
  onEvent: (event: ChatTurnEvent) => void;
  onComplete: () => void;
  onError: (message: string) => void;
  /** 网络瞬断进入退避重连（携带续播游标）时通知，供连接状态机展示 reconnecting。 */
  onReconnect?: (attempt: number) => void;
}

interface TurnStreamRequest {
  turnId: string;
  /** 续播起始游标（上次收到的 event_id），缺省 0 表示订阅全量帧。 */
  cursor?: number;
  signal: AbortSignal;
}

interface SseFrameMeta {
  eventName: string;
  eventId?: number;
  dataLines: string[];
}

const dispatchBlock = (block: string, handler: TurnStreamHandler): number | undefined => {
  const meta: SseFrameMeta = { eventName: 'message', dataLines: [] };
  block.split(/\r?\n/).forEach((line) => {
    if (!line || line.startsWith(':')) {
      return;
    }
    if (line.startsWith('event:')) {
      meta.eventName = line.slice(6).trim();
      return;
    }
    if (line.startsWith('id:')) {
      meta.eventId = Number.parseInt(line.slice(3).trim(), 10);
      return;
    }
    if (line.startsWith('data:')) {
      meta.dataLines.push(line.slice(5).trimStart());
    }
  });
  if (!meta.dataLines.length) {
    return meta.eventId;
  }
  try {
    const payload = JSON.parse(meta.dataLines.join('\n')) as Partial<ChatTurnEvent>;
    // 官方化 v2：type 在 data 内（官方编码无 event: 名）；event: 仅作旧服务端兼容兜底
    const type =
      (payload.type as ChatTurnEvent['type']) ??
      (meta.eventName !== 'message' ? (meta.eventName as ChatTurnEvent['type']) : undefined);
    handler.onEvent({
      ...(payload as ChatTurnEvent),
      // rawEvent 扩展摊平：iter/phase/计时/tokens/errorCode/customName 到顶层（渲染逻辑零改动）
      ...(payload.rawEvent ?? {}),
      type,
      // 官方字段别名 → 既有扁平字段
      toolName: payload.toolCallName ?? payload.toolName,
      toolResult: payload.content ?? payload.toolResult,
      customName: payload.name ?? payload.customName,
      errorMessage: payload.message ?? payload.errorMessage,
      errorCode: payload.code ?? payload.errorCode,
    });
  } catch {
    handler.onError('收到无法解析的流式数据');
  }
  return meta.eventId;
};

const sleepBackoff = async (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener(
      'abort',
      () => {
        clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });

/**
 * 订阅轮次事件流（GET SSE）：服务端事件先落投递日志再补发，帧头 id 即投递游标。
 * 网络瞬断按指数退避携带游标重连续播（无重复无丢失）；连续失败才上抛错误；
 * 服务端在轮次终态完成流，订阅端不会反向影响执行事实。
 */
export async function streamTurnEvents(
  { turnId, cursor = 0, signal }: TurnStreamRequest,
  handler: TurnStreamHandler,
): Promise<void> {
  let position = cursor;
  let attempt = 0;
  for (;;) {
    try {
      const query = position > 0 ? `?cursor=${position}` : '';
      const response = await fetch(`${PREFIX}/chat/turns/${encodeURIComponent(turnId)}/events${query}`, {
        headers: applyCurrentProjectHeader(`${PREFIX}/chat/turns/${encodeURIComponent(turnId)}/events${query}`, {
          Accept: 'text/event-stream',
        }),
        signal,
        credentials: 'include',
      });
      if (!response.ok || !response.body) {
        let detail = `HTTP ${response.status}`;
        try {
          const envelope = await response.json();
          detail = envelope?.message || detail;
        } catch {
          // 非 JSON 错误体，保留状态码信息
        }
        handler.onError(detail);
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      for (;;) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        let boundary = buffer.indexOf('\n\n');
        while (boundary >= 0) {
          const block = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          if (block.trim()) {
            const seenId = dispatchBlock(block, handler);
            if (seenId !== undefined && !Number.isNaN(seenId)) {
              position = seenId;
            }
          }
          boundary = buffer.indexOf('\n\n');
        }
      }
      if (buffer.trim()) {
        dispatchBlock(buffer, handler);
      }
      handler.onComplete();
      return;
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        handler.onComplete();
        return;
      }
      attempt += 1;
      if (attempt > 3) {
        handler.onError(`连接已中断，请刷新查看进度：${(error as Error).message}`);
        return;
      }
      handler.onReconnect?.(attempt);
      await sleepBackoff(300 * 2 ** (attempt - 1), signal);
    }
  }
}

/** 查询审计分页（仅本人留痕）。 */
export const agentAuditApi = {
  page: (body: { pageNo: number; pageSize: number; sessionId?: string; datasetId?: number }) =>
    HttpUtils.post<PagingData<QueryAudit>>(`${PREFIX}/queries/page`, body, {
      credentials: 'include',
    }).then((envelope) => envelope.data),
};

/** 技能在线管理（skills）：注册/列表/详情/更新/在线启停/删除，热生效（下一轮推理即按新状态注入）。 */
export const agentSkillApi = {
  /** 在线注册技能（POST /api/v1/agent/skills，SKILL_MANAGE）。 */
  register: (input: AgentSkillSaveInput) =>
    HttpUtils.postData<AgentSkillItem>(`${PREFIX}/skills`, input, SKILL_REQUEST_OPTIONS),

  /** 技能列表（GET /api/v1/agent/skills，SKILL_READ）。 */
  list: () => HttpUtils.getData<AgentSkillItem[]>(`${PREFIX}/skills`, SKILL_REQUEST_OPTIONS),

  /** 技能详情（GET /api/v1/agent/skills/{skillId}，SKILL_READ）。 */
  detail: (skillId: string) =>
    HttpUtils.getData<AgentSkillItem>(`${PREFIX}/skills/${encodeURIComponent(skillId)}`, SKILL_REQUEST_OPTIONS),

  /** 更新技能（PUT /api/v1/agent/skills/{skillId}，SKILL_MANAGE；409=乐观版本冲突）。 */
  update: (skillId: string, input: AgentSkillSaveInput) =>
    HttpUtils.putData<AgentSkillItem>(`${PREFIX}/skills/${encodeURIComponent(skillId)}`, input, SKILL_REQUEST_OPTIONS),

  /** 在线启停（PUT /api/v1/agent/skills/{skillId}/active，SKILL_MANAGE）；热生效：下一轮推理即按新状态注入。 */
  setActive: (skillId: string, active: boolean) =>
    HttpUtils.putData<boolean>(
      `${PREFIX}/skills/${encodeURIComponent(skillId)}/active`,
      { active },
      SKILL_REQUEST_OPTIONS,
    ),

  /** 删除技能（DELETE /api/v1/agent/skills/{skillId}，SKILL_MANAGE；热卸）。 */
  remove: (skillId: string) =>
    HttpUtils.deleteData<boolean>(`${PREFIX}/skills/${encodeURIComponent(skillId)}`, undefined, SKILL_REQUEST_OPTIONS),
};

/** 分析报告管理。 */
export const agentReportApi = {
  page: (body: { pageNo: number; pageSize: number; keyword?: string }) =>
    HttpUtils.post<PagingData<AgentReport>>(`${PREFIX}/reports/page`, body, {
      credentials: 'include',
    }).then((envelope) => envelope.data),
  detail: (id: number) => HttpUtils.getData<ReportDetail>(`${PREFIX}/reports/${id}`, { credentials: 'include' }),
  remove: (id: number) => HttpUtils.deleteData<boolean>(`${PREFIX}/reports/${id}`, { credentials: 'include' }),
};
