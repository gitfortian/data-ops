import { LoadingOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons';
import type { BubbleItemType, ConversationsProps, ThoughtChainItemType } from '@ant-design/x';
import Bubble from '@ant-design/x/lib/bubble';
import Conversations from '@ant-design/x/lib/conversations';
import Sender from '@ant-design/x/lib/sender';
import ThoughtChain from '@ant-design/x/lib/thought-chain';
import { Alert, Button, Collapse, Input, Layout, message, Space, Tabs, Tag, Typography } from 'antd';
import React from 'react';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import AuditTable from './components/AuditTable';
import ConfigPanel from './components/ConfigPanel';
import { useAgentStyles } from './components/pageStyles';
import ReportsTab from './components/ReportsTab';
import SkillsTab from './components/SkillsTab';
import { AGENT_SKILL_READ } from './skill-runtime';
import {
  type ConnectionEvent,
  type ConnectionState,
  coalesceEvents,
  errorKey,
  isWatchdogDue,
  nextConnectionState,
  presentError,
} from './stream-runtime';
import {
  applyClarifyWaiting,
  applyThinkingElapsed,
  applyToolCall,
  applyToolResult,
  closeRunningCalls,
  convergeRunningCalls,
  ensureThinkStep,
  extractCaliber,
  hydrateTraceSteps,
  markThinkStopped,
  type TraceChainEntry,
  toChainEntries,
  toolCardVisual,
} from './trace-runtime';
import {
  type AgentSession,
  type ChatTurnEvent,
  type ClarifyPayload,
  newSessionId,
  type ToolFeedback,
  type TraceStep,
  type UIMessage,
} from './types';

import { governanceQuestions, governanceSourcePath, parseGovernanceTarget } from '@/services/agent/governance';
import type { TurnSubmitPayload } from '@/services/agent';
import GovernanceEvidenceCards from '@/components/ai/GovernanceEvidenceCards';
import { visibleGovernanceText } from '@/services/agent/suggestions';

const { Sider, Content } = Layout;

let messageIdSeed = 0;
const nextMessageId = () => `m-${Date.now().toString(36)}-${messageIdSeed++}`;

function AvatarFallback(props: { icon: React.ReactNode; background: string; color?: string }) {
  return (
    <div
      style={{
        width: 32,
        height: 32,
        borderRadius: 8,
        background: props.background,
        color: props.color ?? '#fff',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 15,
      }}
    >
      {props.icon}
    </div>
  );
}

const AiAgentPage: React.FC = () => {
  const [governanceTarget, setGovernanceTarget] = React.useState(() => parseGovernanceTarget(window.location.search));
  const [sessions, setSessions] = React.useState<AgentSession[]>([]);
  const [sessionLoading, setSessionLoading] = React.useState(false);
  const [activeSessionId, setActiveSessionId] = React.useState<string | null>(null);
  const [messages, setMessages] = React.useState<UIMessage[]>([]);
  const [streaming, setStreaming] = React.useState(false);
  const [clarify, setClarify] = React.useState<ClarifyPayload | null>(null);
  const [connection, setConnection] = React.useState<ConnectionState>('idle');
  const [input, setInput] = React.useState('');
  const inputRef = React.useRef<HTMLDivElement | null>(null);
  const { styles } = useAgentStyles();
  // 技能管理 Tab 可见性：按 agent:skill:read（文档 §1/§4），无 read 权限不渲染该 Tab。
  const { can } = usePermissionAccess();
  const canReadSkills = can(AGENT_SKILL_READ);
  const hasText = input.trim().length > 0;
  const sendIdle = !hasText && !streaming;

  const abortRef = React.useRef<AbortController | null>(null);
  const assistantIdRef = React.useRef<string>('');
  const turnStartRef = React.useRef(0);
  const firstEventRef = React.useRef(0);
  const thinkStartRef = React.useRef(0);
  const toolStartRef = React.useRef(new Map<string, number>());

  // ---- 打字机缓冲：网络层无论攒多大块，页面都按固定节奏逐字放出 ----
  const pendingThinkRef = React.useRef('');
  const pendingContentRef = React.useRef('');
  const tickerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);

  // ---- Phase 2 渲染管线：帧先入队，30ms 批量合流应用（禁止逐帧直驱 setState）----
  const pendingEventsRef = React.useRef<ChatTurnEvent[]>([]);
  const pipelineTimerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastFrameAtRef = React.useRef(0);
  const watchdogRef = React.useRef<ReturnType<typeof setInterval> | null>(null);
  const seenErrorKeysRef = React.useRef<Set<string>>(new Set());
  const lastUserMessageRef = React.useRef('');

  const connectionRef = React.useRef<ConnectionState>('idle');
  const transition = (event: ConnectionEvent) => {
    connectionRef.current = nextConnectionState(connectionRef.current, event);
    setConnection(connectionRef.current);
  };

  const flushTicker = () => {
    if (tickerRef.current !== null) {
      clearInterval(tickerRef.current);
      tickerRef.current = null;
    }
    const thinkRemain = pendingThinkRef.current;
    const contentRemain = pendingContentRef.current;
    pendingThinkRef.current = '';
    pendingContentRef.current = '';
    patchAssistant((draft) => ({ ...draft, trace: markThinkStopped(draft.trace) }));
    if (contentRemain) {
      patchAssistant((draft) => ({ ...draft, content: draft.content + contentRemain }));
    }
    if (thinkRemain) {
      patchAssistant((draft) => {
        const last = draft.trace[draft.trace.length - 1];
        if (last && last.kind === 'think') {
          const trace = [...draft.trace.slice(0, -1), { ...last, text: last.text + thinkRemain }];
          return { ...draft, trace };
        }
        return draft;
      });
    }
  };

  const startTicker = () => {
    if (tickerRef.current !== null) {
      return;
    }
    tickerRef.current = setInterval(() => {
      const thinkBuf = pendingThinkRef.current;
      const contentBuf = pendingContentRef.current;
      if (!thinkBuf && !contentBuf) {
        return; // 缓冲已空，等待下一批数据
      }
      if (thinkBuf) {
        const take = Math.max(2, Math.ceil(thinkBuf.length / 12));
        pendingThinkRef.current = thinkBuf.slice(take);
        const piece = thinkBuf.slice(0, take);
        patchAssistant((draft) => {
          const last = draft.trace[draft.trace.length - 1];
          if (last && last.kind === 'think') {
            const trace = [...draft.trace.slice(0, -1), { ...last, text: last.text + piece }];
            return { ...draft, trace };
          }
          return draft;
        });
      }
      if (contentBuf) {
        const take = Math.max(2, Math.ceil(contentBuf.length / 12));
        pendingContentRef.current = contentBuf.slice(take);
        const piece = contentBuf.slice(0, take);
        patchAssistant((draft) => ({ ...draft, content: draft.content + piece }));
      }
    }, 33);
  };

  const refreshSessions = React.useCallback(async () => {
    setSessionLoading(true);
    try {
      setSessions(await agentSessionApi.list());
    } catch (error) {
      message.error(`会话列表加载失败：${(error as Error).message}`);
    } finally {
      setSessionLoading(false);
    }
  }, []);

  React.useEffect(() => {
    refreshSessions();
  }, [refreshSessions]);

  const openAssistant = () => {
    if (assistantIdRef.current) {
      return;
    }
    const id = nextMessageId();
    assistantIdRef.current = id;
    setMessages((prev) => [...prev, { id, role: 'assistant', content: '', trace: [] }]);
  };

  const patchAssistant = (patch: (draft: UIMessage) => UIMessage) => {
    const id = assistantIdRef.current;
    if (!id) {
      return;
    }
    setMessages((prev) => prev.map((item) => (item.id === id ? patch(item) : item)));
  };

  const applyEvent = (event: ChatTurnEvent) => {
    switch (event.type) {
      case 'REASONING_MESSAGE_CONTENT': {
        openAssistant();
        // 迭代序（服务端权威，rawEvent 已摊平到 event.iter）：新思考块开启新迭代组（§11.4）
        patchAssistant((draft) => ({
          ...draft,
          trace: ensureThinkStep(draft.trace, typeof event.iter === 'number' ? event.iter : undefined),
        }));
        pendingThinkRef.current += event.delta ?? '';
        // 思考块耗时：用服务端权威值实时回写（前端不再掐表）
        if (typeof event.thinkingElapsedMs === 'number') {
          patchAssistant((draft) => ({
            ...draft,
            trace: applyThinkingElapsed(draft.trace, event.thinkingElapsedMs!),
          }));
        }
        startTicker();
        break;
      }
      case 'TEXT_MESSAGE_CONTENT': {
        openAssistant();
        patchAssistant((draft) => ({ ...draft, trace: markThinkStopped(draft.trace) }));
        pendingContentRef.current += event.delta ?? '';
        startTicker();
        break;
      }
      case 'TOOL_CALL_START':
        openAssistant();
        patchAssistant((draft) => ({
          ...draft,
          trace: applyToolCall(draft.trace, {
            toolCallId: event.toolCallId,
            toolName: event.toolName,
            iter: event.iter,
            // 工具发起时刻：服务端权威值（rawEvent.startedAt 摊平），缺失时本地兜底
            startedAt: typeof event.startedAt === 'number' ? event.startedAt : Date.now(),
          }),
        }));
        break;
      // 官方双帧：TOOL_CALL_END 闭合调用 + 耗时/状态；TOOL_CALL_RESULT 携带结果内容
      case 'TOOL_CALL_END':
        patchAssistant((draft) => ({
          ...draft,
          trace: applyToolResult(draft.trace, event),
        }));
        break;
      case 'TOOL_CALL_RESULT':
        patchAssistant((draft) => ({
          ...draft,
          trace: applyToolResult(draft.trace, event),
        }));
        break;
      case 'CUSTOM': {
        // 官方 CUSTOM 扩展点：name=customName，载荷在 value（dispatchBlock 已映射 name→customName）
        const customName = event.customName ?? event.name;
        if (customName === 'clarify_requested') {
          const value = (event.value ?? {}) as { toolCallId?: string; toolName?: string; question?: string };
          openAssistant();
          // HITL 等待是显式状态，不与 RUNNING 转圈混淆（§11.5）
          patchAssistant((draft) => ({
            ...draft,
            trace: applyClarifyWaiting(draft.trace, value.toolCallId ?? event.toolCallId),
          }));
          setClarify({
            toolCallId: value.toolCallId ?? event.toolCallId ?? '',
            toolName: value.toolName ?? event.toolName ?? '',
            question: value.question ?? event.delta ?? '',
            options: [],
          });
        } else if (customName === 'turn_cancelled') {
          openAssistant();
          // 终态防御收敛：停止生成不得残留"执行中"卡片（§11.4）
          patchAssistant((draft) => ({
            ...draft,
            trace: closeRunningCalls(draft.trace),
          }));
        } else if (customName === 'exceeded_max_iters') {
          openAssistant();
          transition('fail');
          patchAssistant((draft) => ({
            ...draft,
            error: typeof event.value === 'string' ? event.value : '已达最大推理轮数',
            trace: closeRunningCalls(draft.trace),
          }));
        }
        break;
      }
      case 'RUN_FINISHED':
        // 轮次耗时以服务端权威值（event.elapsedMs）为准，缺失才本地兜底；onComplete 同规则
        patchAssistant((draft) =>
          draft.elapsedMs == null
            ? {
                ...draft,
                elapsedMs: typeof event.elapsedMs === 'number' ? event.elapsedMs : Date.now() - turnStartRef.current,
              }
            : draft,
        );
        convergeRunningCards();
        break;
      case 'RUN_ERROR': {
        openAssistant();
        // 四级分发第三级·同源去重：同轮内相同 (码+正文) 只呈现一次
        const key = errorKey(event.errorCode, event.errorMessage);
        if (!seenErrorKeysRef.current.has(key)) {
          seenErrorKeysRef.current.add(key);
        }
        transition('fail');
        patchAssistant((draft) => ({
          ...draft,
          error: event.errorMessage ?? '执行失败',
          errorCode: event.errorCode ?? undefined,
          // 服务端 ABORTED 帧已闭合卡片，此处仅对历史帧/异常路径兜底
          trace: closeRunningCalls(draft.trace),
        }));
        break;
      }
      default:
        break;
    }
  };

  // ---- 连接看门狗（Phase 2）：流式期间 60s 无帧判疑似断链 → reconnecting；
  // 服务层指数退避续播成功（收到帧）即恢复 streaming。标签页重见时立即核查。
  const stopWatchdog = () => {
    if (watchdogRef.current !== null) {
      clearInterval(watchdogRef.current);
      watchdogRef.current = null;
    }
  };

  const startWatchdog = () => {
    stopWatchdog();
    watchdogRef.current = setInterval(() => {
      if (isWatchdogDue(lastFrameAtRef.current, Date.now())) {
        transition('reconnect-start');
      }
    }, 5000);
  };

  React.useEffect(() => {
    const onVisible = () => {
      if (!document.hidden && isWatchdogDue(lastFrameAtRef.current, Date.now())) {
        transition('reconnect-start');
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, []);

  // 渲染管线：帧入队 → 30ms 合流批应用；终态/反问帧旁路直通保证时序
  const flushPendingEvents = () => {
    if (pipelineTimerRef.current !== null) {
      clearTimeout(pipelineTimerRef.current);
      pipelineTimerRef.current = null;
    }
    const batch = pendingEventsRef.current;
    pendingEventsRef.current = [];
    if (!batch.length) {
      return;
    }
    for (const event of coalesceEvents(batch)) {
      applyEvent(event);
    }
  };

  const handleEvent = (event: ChatTurnEvent) => {
    lastFrameAtRef.current = Date.now();
    transition('frame');
    if (typeof lastFrameAtRef.current === 'number' && connectionRef.current === 'connecting') {
      transition('first-frame');
      startWatchdog();
    }
    const terminal =
      event.type === 'RUN_FINISHED' ||
      event.type === 'RUN_ERROR' ||
      (event.type === 'CUSTOM' &&
        (event.customName === 'turn_cancelled' ||
         event.customName === 'exceeded_max_iters' ||
         event.customName === 'clarify_requested'));
    pendingEventsRef.current.push(event);
    if (terminal) {
      flushPendingEvents();
      return;
    }
    if (pipelineTimerRef.current === null) {
      pipelineTimerRef.current = setTimeout(flushPendingEvents, 30);
    }
  };

  // ---- 权威水合（O2 双通道裁决）：轮次终态后拉取 trace v2，把入参/结果/失败归因/
  // 真实状态合并进链路卡片。SSE 帧承载实时流，本函数承载事实侧补全；think 文本保留不覆盖。
  const hydrateTrace = async (messageId: string, turnId: string) => {
    try {
      const view = await agentSessionApi.trace(turnId);
      setMessages((prev) =>
        prev.map((item) => {
          if (item.id !== messageId) {
            return item;
          }
          return { ...item, trace: hydrateTraceSteps(item.trace, view.spans) };
        }),
      );
    } catch {
      // 水合失败不影响会话渲染（SSE 帧已提供基础信息）
    }
  };

  // ---- 终态对账钩子（渲染顺序规范 §11.4）：终态后仍 RUNNING 的卡按 ABORTED 自愈收敛
  // 并上报违例——顺序异常从"看着怪"变为可报告事件。
  const convergeRunningCards = () => {
    patchAssistant((draft) => {
      const { trace, violations } = convergeRunningCalls(draft.trace);
      if (violations.length) {
        console.warn('[trace-violation] 终态残留运行卡（服务端应已补发 ABORTED 帧）：', violations);
      }
      return violations.length ? { ...draft, trace } : draft;
    });
  };

  const launchStream = async (payload: TurnSubmitPayload) => {
    setStreaming(true);
    setClarify(null);
    seenErrorKeysRef.current = new Set();
    if (payload.message) {
      lastUserMessageRef.current = payload.message;
    }
    transition('submit');
    turnStartRef.current = Date.now();
    firstEventRef.current = 0;
    thinkStartRef.current = 0;
    toolStartRef.current = new Map();
    const controller = new AbortController();
    abortRef.current = controller;
    // 提交/执行分离：先入队拿 turnId，再订阅事件流（断线由服务层按游标自动续播）
    const submitted = await agentChatApi.submit(payload);
    await streamTurnEvents(
      { turnId: submitted.turnId, signal: controller.signal },
      {
        onEvent: handleEvent,
        onReconnect: () => transition('reconnect-start'),
        onComplete: () => {
          flushPendingEvents();
          flushTicker();
          stopWatchdog();
          transition('finish');
          const msgId = assistantIdRef.current;
          patchAssistant((draft) => ({
            ...draft,
            turnId: submitted.turnId,
            elapsedMs: Date.now() - turnStartRef.current,
          }));
          convergeRunningCards();
          if (msgId) {
            void hydrateTrace(msgId, submitted.turnId);
          }
          assistantIdRef.current = '';
          setStreaming(false);
          abortRef.current = null;
          refreshSessions();
        },
        onError: (text) => {
          flushPendingEvents();
          flushTicker();
          openAssistant();
          const msgId = assistantIdRef.current;
          patchAssistant((draft) => ({
            ...draft,
            turnId: submitted.turnId,
            elapsedMs: Date.now() - turnStartRef.current,
            error: text,
          }));
          convergeRunningCards();
          if (msgId) {
            void hydrateTrace(msgId, submitted.turnId);
          }
          stopWatchdog();
          transition('fail');
          assistantIdRef.current = '';
          setStreaming(false);
          abortRef.current = null;
        },
      },
    );
  };

  const sendMessage = async (text?: string) => {
    const content = (text ?? input).trim();
    if (!content || streaming || clarify) {
      return;
    }
    const sessionId = activeSessionId ?? newSessionId();
    setActiveSessionId(sessionId);
    setInput('');
    setMessages((prev) => [...prev, { id: nextMessageId(), role: 'user', content, trace: [] }]);
    await launchStream({ sessionId, message: content, governanceTarget: governanceTarget ?? undefined });
  };

  const answerClarify = async (answer: string) => {
    if (!clarify || streaming) {
      return;
    }
    const sessionId = activeSessionId!;
    setMessages((prev) => [...prev, { id: nextMessageId(), role: 'user', content: answer, trace: [] }]);
    await launchStream({
      sessionId,
      toolResults: [{ toolCallId: clarify.toolCallId, toolName: clarify.toolName, output: answer }],
    });
  };

  const selectSession = async (sessionId: string) => {
    if (streaming) {
      message.warning('当前正在推理，请等待结束或停止后再切换');
      return;
    }
    setGovernanceTarget(null);
    setInput('');
    setActiveSessionId(sessionId);
    setClarify(null);
    try {
      const history = await agentSessionApi.history(sessionId);
      const restored = history
        .filter((turn) => (turn.role === 'user' || turn.role === 'assistant' || turn.role === 'error') && turn.content)
        .map((turn) => ({
          id: nextMessageId(),
          role: turn.role as UIMessage['role'],
          content: turn.content,
          turnId: turn.turnId ?? undefined,
          trace: (turn.trace ?? []).map((step, idx) => ({
            key: `${step.kind === 'think' ? 't' : 'c'}-${step.toolCallId ?? idx}`,
            kind: step.kind as 'think' | 'call',
            text: step.text ?? '',
            toolCallId: step.toolCallId ?? undefined,
            toolName: step.toolName ?? undefined,
            running: false,
            resultText: step.resultText ?? undefined,
          })),
        }));
      setMessages(restored);
      // 历史回放切换（O2）：最近一条 assistant 轮次自动水合权威链路（入参/结果/失败归因不丢）
      const lastAssistant = [...restored].reverse().find((m) => m.role === 'assistant' && m.turnId);
      if (lastAssistant?.turnId) {
        void hydrateTrace(lastAssistant.id, lastAssistant.turnId);
      }
    } catch (error) {
      message.error(`历史加载失败：${(error as Error).message}`);
      setMessages([]);
    }
  };

  const startNewSession = () => {
    if (streaming) {
      message.warning('当前正在推理，请先停止');
      return;
    }
    setGovernanceTarget(null);
    setInput('');
    setActiveSessionId(null);
    setMessages([]);
    setClarify(null);
  };

  const renameSession = async (sessionId: string, title: string) => {
    try {
      await agentSessionApi.rename(sessionId, title);
      refreshSessions();
    } catch (error) {
      message.error(`重命名失败：${(error as Error).message}`);
    }
  };

  const deleteSession = async (sessionId: string) => {
    try {
      await agentSessionApi.remove(sessionId);
      if (activeSessionId === sessionId) {
        startNewSession();
      }
      refreshSessions();
    } catch (error) {
      message.error(`删除失败：${(error as Error).message}`);
    }
  };

  /** 思考/工具链路 -> ThoughtChain 节点（O2：迭代分组 + 工具卡四要素；分组/状态映射在 trace-runtime）。 */
  const toChainItems = (trace: TraceStep[]): ThoughtChainItemType[] => {
    const items: ThoughtChainItemType[] = [];
    const entries: TraceChainEntry[] = toChainEntries(trace);
    entries.forEach((entry) => {
      if (entry.type === 'divider') {
        items.push({
          key: entry.key,
          title: <Tag color="default">第 {entry.iter} 轮推理</Tag>,
          status: 'success',
        });
        return;
      }
      const step = entry.step;
      if (step.kind === 'think') {
        items.push({
          key: step.key,
          title: '思考',
          status: step.running ? 'loading' : 'success',
          description:
            !step.running && typeof step.durationMs === 'number'
              ? `思考 ${(step.durationMs / 1000).toFixed(1)} 秒`
              : undefined,
          content: (
            <Typography.Paragraph
              type="secondary"
              italic
              style={{
                marginBottom: 0,
                fontSize: 12.5,
                whiteSpace: 'pre-wrap',
                maxHeight: 180,
                overflowY: 'auto',
              }}
            >
              {step.text}
            </Typography.Paragraph>
          ),
        });
        return;
      }
      // 工具卡四要素：耗时 / 入参 / 结果 / 状态与错误码（O2 一等公民化；映射纯函数在 trace-runtime）
      const { color: statusColor, statusText, failed } = toolCardVisual(step);
      const sections = [
        step.inputText
          ? {
              key: 'in',
              label: '入参',
              children: (
                <pre className={styles.tracePre} style={{ maxHeight: 160 }}>
                  {step.inputText}
                </pre>
              ),
            }
          : null,
        step.resultText
          ? {
              key: 'out',
              label: failed ? '响应' : '结果',
              children: (
                <pre className={styles.tracePre} style={{ maxHeight: 220 }}>
                  {step.resultText}
                </pre>
              ),
            }
          : null,
      ].filter(Boolean) as { key: string; label: string; children: React.ReactNode }[];
      items.push({
        key: step.key,
        title: <Tag color={statusColor}>{step.toolName}</Tag>,
        status: step.running ? 'loading' : failed ? 'error' : 'success',
        description: (
          <Typography.Text type={failed ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
            {statusText}
          </Typography.Text>
        ),
        content: (
          <>
            {failed && step.failureDetail ? (
              <Typography.Paragraph type="danger" style={{ marginBottom: 4, fontSize: 12.5 }}>
                {step.failureDetail}
              </Typography.Paragraph>
            ) : null}
            {sections.length > 0 ? (
              <Collapse
                ghost
                size="small"
                items={sections.map((s) => ({
                  key: s.key,
                  label: <span style={{ fontSize: 12 }}>{s.label}</span>,
                  children: s.children,
                }))}
              />
            ) : null}
          </>
        ),
      });
    });
    return items;
  };

  /** 等待首个事件期间（已发出问题但尚无任何回复内容）显示加载态。 */
  const lastMessage = messages[messages.length - 1];
  const waitingFirst =
    streaming &&
    (!lastMessage ||
      (lastMessage.role === 'assistant' && !lastMessage.content && !lastMessage.trace.length && !lastMessage.error));

  const bubbleItems: BubbleItemType[] = messages.map((item) => ({
    role: item.role === 'error' ? 'assistant' : item.role,
    key: item.id,
    placement: item.role === 'user' ? 'end' : 'start',
    avatar:
      item.role === 'user' ? (
        <AvatarFallback icon={<UserOutlined />} background="#f0f0f0" color="#595959" />
      ) : (
        <AvatarFallback icon={<RobotOutlined />} background="linear-gradient(135deg, #1677ff, #36cfc9)" />
      ),
    content:
      item.role === 'user' ? (
        item.content
      ) : item.role === 'error' ? (
        <Alert type="error" showIcon message={item.content} />
      ) : (
        <Space direction="vertical" style={{ maxWidth: '100%' }}>
          {item.elapsedMs != null ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              已完成 {item.trace.filter((step) => step.kind === 'call').length} 步 · 用时{' '}
              {(item.elapsedMs / 1000).toFixed(1)} 秒
            </Typography.Text>
          ) : null}
          {item.trace.length ? <ThoughtChain items={toChainItems(item.trace)} /> : null}
          {item.role === 'assistant' && (item.elapsedMs != null || item.trace.some((st) => st.kind === 'call'))
            ? (() => {
                const toolCount = item.trace.filter((st) => st.kind === 'call').length;
                const stats = [
                  toolCount > 0 ? `${toolCount} 次工具调用` : null,
                  item.elapsedMs != null ? `耗时 ${(item.elapsedMs / 1000).toFixed(1)} 秒` : null,
                  item.totalTokens ? `${item.totalTokens} tokens` : null,
                ].filter(Boolean);
                return (
                  <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                    本回合 {stats.join(' · ')}
                    {!streaming && lastUserMessageRef.current ? (
                      <Button
                        type="link"
                        size="small"
                        style={{ padding: 0, marginLeft: 8, fontSize: 11 }}
                        onClick={() => void sendMessage(lastUserMessageRef.current)}
                      >
                        重新生成
                      </Button>
                    ) : null}
                  </Typography.Text>
                );
              })()
            : null}
          {item.content ? (
            <Typography.Text style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {item.role === 'assistant' ? visibleGovernanceText(item.content) : item.content}
            </Typography.Text>
          ) : null}
          {item.role === 'assistant' && item.content ? <GovernanceEvidenceCards text={item.content} /> : null}
          {(() => {
            // PI-103 口径卡：从 trace-runtime 的 extractCaliber 提取（单一实现，可测）
            const caliber = extractCaliber(
              item.trace
                .filter((st) => st.toolName === 'run_semantic_query' && st.resultText)
                .map((st) => st.resultText as string),
            );
            return caliber ? (
              <div
                style={{
                  border: '1px solid #d9d9d9',
                  borderRadius: 6,
                  padding: '6px 10px',
                  margin: '6px 0',
                  background: '#fafafa',
                  fontSize: 12,
                }}
              >
                <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                  口径卡（来自语义层，数字必须与当轮工具结果一致）
                </Typography.Text>
                <div>{caliber}</div>
              </div>
            ) : null;
          })()}
          {item.error
            ? (() => {
                const presentation = presentError(item.errorCode, item.error);
                const runAction = () => {
                  if (presentation.action.type === 'retry' && lastUserMessageRef.current) {
                    void sendMessage(lastUserMessageRef.current);
                  } else if (presentation.action.type === 'narrow') {
                    inputRef.current?.focus();
                  } else if (presentation.action.type === 'check-config') {
                    message.info(presentation.hint);
                  }
                };
                return (
                  <Alert
                    type={presentation.severity}
                    showIcon
                    message={presentation.title}
                    description={
                      <Space direction="vertical" size={4}>
                        <span>{presentation.hint}</span>
                        {presentation.action.type !== 'none' && !streaming ? (
                          <Button size="small" type="primary" ghost onClick={runAction}>
                            {presentation.action.label}
                          </Button>
                        ) : null}
                      </Space>
                    }
                  />
                );
              })()
            : null}
        </Space>
      ),
    loading: item.role === 'assistant' && !item.content && !item.trace.length && !item.error && streaming,
  }));

  const conversationItems: ConversationsProps['items'] = sessions.map((session) => ({
    key: session.sessionId,
    label: session.title?.trim() || `会话 ${session.sessionId.slice(-8)}`,
  }));

  const parseClarify = (payload: ClarifyPayload) => {
    try {
      const args = JSON.parse(payload.question) as { question?: string; options?: string[] };
      return {
        question: args.question || payload.question,
        options: Array.isArray(args.options) ? args.options : ([] as string[]),
      };
    } catch {
      return { question: payload.question, options: [] as string[] };
    }
  };

  return (
    <Layout style={{ height: 'calc(100vh - 56px)', background: '#fff' }}>
      <Sider width={256} theme="light" style={{ borderInlineEnd: '1px solid #f0f0f0', padding: 12 }}>
        <Button type="primary" block onClick={startNewSession} style={{ marginBottom: 12 }}>
          新建会话
        </Button>
        <Conversations
          items={conversationItems}
          activeKey={activeSessionId ?? undefined}
          onActiveChange={(key) => selectSession(key)}
          menu={(session) => ({
            items: [
              {
                key: 'rename',
                label: '重命名',
                onClick: () => {
                  const title = window.prompt('新的会话标题');
                  if (title?.trim()) {
                    renameSession(session.key, title.trim());
                  }
                },
              },
              {
                key: 'delete',
                label: '删除',
                danger: true,
                onClick: () => {
                  deleteSession(session.key);
                },
              },
            ],
          })}
        />
        {!sessions.length && !sessionLoading ? <Typography.Text type="secondary">暂无会话</Typography.Text> : null}
      </Sider>
      <Content style={{ display: 'flex', flexDirection: 'column', padding: 16 }}>
        {governanceTarget && (
          <Alert
            type="info"
            showIcon
            message={governanceTarget.qualityMonitorId !== undefined ? `质量监控 #${governanceTarget.qualityMonitorId} 规则建议`
              : governanceTarget.assetId !== undefined ? `资产 #${governanceTarget.assetId} 治理解读`
                : `质量执行 ${governanceTarget.qualityExecutionNo} 解读与排查`}
            description={
              <Space wrap>
                <span>{governanceTarget.qualityExecutionNo !== undefined
                  ? '围绕本次历史执行核对事实、缺口和人工检查步骤；具体根因需验证，调整规则请回源页面另行发起。'
                  : '按当前权限读取证据，解读完成后可回到来源核验。'}</span>
                {governanceQuestions(governanceTarget).map((question, index) => (
                  <Button key={question} size="small" disabled={streaming || !!clarify} onClick={() => setInput(question)}>
                    {governanceTarget.qualityExecutionNo !== undefined ? (index === 0 ? '解读与排查' : '补充排查信息')
                      : governanceTarget.qualityMonitorId !== undefined ? '生成规则候选' : (index === 0 ? '解释结果' : '排查建议')}
                  </Button>
                ))}
                <Button size="small" href={governanceSourcePath(governanceTarget)}>
                  {governanceTarget.qualityExecutionNo !== undefined ? '返回本次执行核对' : '返回来源'}
                </Button>
              </Space>
            }
            style={{ marginBottom: 12 }}
          />
        )}
        {(connection === 'connecting' || connection === 'reconnecting') && (
          <div style={{ padding: '4px 16px' }}>
            <Tag color={connection === 'reconnecting' ? 'orange' : 'processing'}>
              {connection === 'reconnecting' ? '连接中断，正在自动重连续播…' : '正在建立连接…'}
            </Tag>
          </div>
        )}
        <Tabs
          items={[
            {
              key: 'chat',
              label: '对话',
              children: (
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    height: 'calc(100vh - 235px)',
                  }}
                >
                  <div style={{ flex: 1, overflowY: 'auto' }}>
                    {bubbleItems.length === 0 && !clarify ? (
                      <div style={{ textAlign: 'center', marginTop: 60 }}>
                        <Typography.Title level={4} type="secondary">
                          AI 数据与治理助手
                        </Typography.Title>
                        <Typography.Text type="secondary">用自然语言描述需求，例如：</Typography.Text>
                        <Space wrap style={{ justifyContent: 'center', marginTop: 12 }}>
                          {['上个月各区域销售额是多少？', '最近30天订单量趋势如何？', '帮我看看销量最高的10个商品'].map(
                            (sample) => (
                              <Button key={sample} size="small" onClick={() => setInput(sample)}>
                                {sample}
                              </Button>
                            ),
                          )}
                        </Space>
                      </div>
                    ) : (
                      <Bubble.List items={bubbleItems} />
                    )}

                    {waitingFirst ? (
                      <Space style={{ padding: '6px 4px 10px' }} size={8}>
                        <LoadingOutlined spin style={{ color: '#1677ff' }} />
                        <Typography.Text type="secondary">正在思考并处理您的问题…</Typography.Text>
                      </Space>
                    ) : null}
                  </div>

                  {clarify
                    ? (() => {
                        const { question, options } = parseClarify(clarify);
                        return (
                          <Alert
                            type="warning"
                            showIcon
                            message="AI 需要补充信息"
                            description={
                              <Space direction="vertical" style={{ width: '100%' }}>
                                <span>{question}</span>
                                {options.length ? (
                                  <Space wrap>
                                    {options.map((option) => (
                                      <Button key={option} size="small" onClick={() => answerClarify(option)}>
                                        {option}
                                      </Button>
                                    ))}
                                  </Space>
                                ) : null}
                                <Input.Search
                                  placeholder="或输入你的回答..."
                                  enterButton="回答"
                                  onSearch={(value) => {
                                    const text = value.trim();
                                    if (text) {
                                      answerClarify(text);
                                    }
                                  }}
                                />
                              </Space>
                            }
                            style={{ marginTop: 12 }}
                          />
                        );
                      })()
                    : null}

                  <div ref={inputRef} tabIndex={-1} className={sendIdle ? styles.sendDisabled : undefined}>
                    <Sender
                      value={input}
                      onChange={(value) => setInput(value)}
                      onSubmit={(message) => sendMessage(message)}
                      onCancel={() => {
                        abortRef.current?.abort();
                        const sessionId = activeSessionId;
                        if (sessionId) {
                          // 显式通知后端取消，立即释放会话互斥标记
                          agentSessionApi.cancel(sessionId);
                        }
                      }}
                      loading={streaming}
                      disabled={Boolean(clarify)}
                      placeholder={clarify ? '请先回答上方问题' : '输入数据分析问题，Enter 发送'}
                    />
                  </div>
                </div>
              ),
            },
            { key: 'audits', label: '查询审计', children: <AuditTable /> },
            {
              key: 'config',
              label: '配置',
              children: <ConfigPanel />,
            },

            { key: 'reports', label: '分析报告', children: <ReportsTab /> },
            ...(canReadSkills ? [{ key: 'skills', label: '技能管理', children: <SkillsTab /> }] : []),
          ]}
        />
      </Content>
    </Layout>
  );
};

export default AiAgentPage;
