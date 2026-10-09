import { LoadingOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons';
import type { BubbleItemType, ConversationsProps, ThoughtChainItemType } from '@ant-design/x';
import Bubble from '@ant-design/x/lib/bubble';
import Conversations from '@ant-design/x/lib/conversations';
import Sender from '@ant-design/x/lib/sender';
import ThoughtChain from '@ant-design/x/lib/thought-chain';
import { Alert, Button, Collapse, Layout, message, Space, Tabs, Tag, Typography } from 'antd';
import React from 'react';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { agentChatApi, agentSessionApi, streamTurnEvents } from '@/services/agent';
import AuditTable from './components/AuditTable';
import ConfigPanel from './components/ConfigPanel';
import { useAgentStyles } from './components/pageStyles';
import ReportsTab from './components/ReportsTab';
import QuestionPreparationPanel from './components/QuestionPreparationPanel';
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

import { governanceQuestions, governanceSourcePath, governanceTaskTitle, parseGovernanceTarget } from '@/services/agent/governance';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { readScenarioHistory } from '@/services/agent/scenarioHistory';
import ScenarioHistoryCard from '@/components/ai/ScenarioHistoryCard';
import QueryClarificationCard from '@/components/ai/QueryClarificationCard';
import { readClarificationQuestion } from '@/services/agent/clarification';
import type { TurnSubmitPayload } from '@/services/agent';
import GovernanceEvidenceCards from '@/components/ai/GovernanceEvidenceCards';
import { visibleGovernanceText } from '@/services/agent/suggestions';
import { isTerminal, readContinuation, sessionLocation, type SessionContinuation } from '@/services/agent/continuation';
import { useSessionFollow } from './useSessionFollow';

const { Sider, Content } = Layout;

const turnStatusLabels: Record<NonNullable<SessionContinuation['status']>, string> = {
  QUEUED: '排队中', RUNNING: '推理中', WAITING_INPUT: '等待补充信息', COMPLETED: '已完成',
  FAILED: '执行失败', CANCELLED: '已停止', INTERRUPTED: '已中断',
};

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
  const initialSession = React.useRef(new URLSearchParams(window.location.search).get('sessionId'));
  const [governanceTarget, setGovernanceTarget] = React.useState(() => initialSession.current ? null : parseGovernanceTarget(window.location.search));
  const [historyLoading, setHistoryLoading] = React.useState(Boolean(initialSession.current));
  const [historyError, setHistoryError] = React.useState('');
  const [continuation, setContinuation] = React.useState<SessionContinuation | null>(null);
  const [stopping, setStopping] = React.useState(false);
  const historyRequest = React.useRef(0);
  const continuationBlocked = React.useRef(Boolean(initialSession.current));
  const [sessions, setSessions] = React.useState<AgentSession[]>([]);
  const [sessionLoading, setSessionLoading] = React.useState(false);
  const [activeSessionId, setActiveSessionId] = React.useState<string | null>(null);
  const [messages, setMessages] = React.useState<UIMessage[]>([]);
  const [streaming, setStreaming] = React.useState(false);
  const streamingRef = React.useRef(false);
  streamingRef.current = streaming;
  const liveTurnRef = React.useRef<string | null>(null);
  const stoppingRef = React.useRef<number | null>(null);
  const [connectionNote, setConnectionNote] = React.useState('');
  const [clarify, setClarify] = React.useState<ClarifyPayload | null>(null);
  const [connection, setConnection] = React.useState<ConnectionState>('idle');
  const [input, setInput] = React.useState('');
  const inputRef = React.useRef<HTMLDivElement | null>(null);
  const { styles } = useAgentStyles();
  // 技能管理 Tab 可见性：按 agent:skill:read（文档 §1/§4），无 read 权限不渲染该 Tab。
  const { can } = usePermissionAccess();
  const canReadSkills = can(AGENT_SKILL_READ);
  const hasText = input.trim().length > 0;
  const preparationBlocked = streaming || !!clarify || historyLoading || !!historyError
    || !!continuation?.blockingReason || !can('agent:chat:run');
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
  const sendingRef = React.useRef(false);
  const draftSourceRef = React.useRef<string | undefined>(undefined);
  const [draftLoading, setDraftLoading] = React.useState(false);
  const inputValueRef = React.useRef(input);
  inputValueRef.current = input;

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
      if (!document.hidden && abortRef.current && isWatchdogDue(lastFrameAtRef.current, Date.now())) {
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
    const generation = historyRequest.current;
    try {
      const view = await agentSessionApi.trace(turnId);
      if (generation !== historyRequest.current) return;
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
    const generation = historyRequest.current;
    setContinuation(null);
    setConnectionNote('');
    liveTurnRef.current = null;
    streamingRef.current = true;
    setStreaming(true);
    setClarify(null);
    seenErrorKeysRef.current = new Set();
    transition('submit');
    turnStartRef.current = Date.now();
    firstEventRef.current = 0;
    thinkStartRef.current = 0;
    toolStartRef.current = new Map();
    const controller = new AbortController();
    let settled = false;
    const current = () => generation === historyRequest.current && !controller.signal.aborted && !settled;
    abortRef.current = controller;
    // 提交/执行分离：先入队拿 turnId，再订阅事件流（断线由服务层按游标自动续播）
    let submitted;
    try {
      submitted = await agentChatApi.submit(payload);
      if (!current()) return;
      if (typeof submitted.turnId !== 'string' || !submitted.turnId.trim()) throw new Error('missing receipt identity');
      liveTurnRef.current = submitted.turnId;
      draftSourceRef.current = undefined;
    } catch (error) {
      if (!current()) return;
      settled = true;
      streamingRef.current = false;
      setStreaming(false); stopWatchdog(); transition('fail');
      abortRef.current = null;
      sendingRef.current = false;
      if (payload.message) setInput(payload.message);
      continuationBlocked.current = true;
      setHistoryError('提交未确认，请刷新会话核对状态后继续。');
      return;
    }
    await streamTurnEvents(
      { turnId: submitted.turnId, signal: controller.signal },
      {
        onEvent: (event) => { if (current()) handleEvent(event); },
        onReconnect: () => { if (current()) transition('reconnect-start'); },
        onComplete: () => {
          if (!current()) return;
          settled = true;
          flushPendingEvents();
          flushTicker();
          stopWatchdog();
          transition('finish');
          const msgId = assistantIdRef.current;
          patchAssistant((draft) => ({
            ...draft,
            turnId: submitted.turnId,
          }));
          if (msgId) {
            void hydrateTrace(msgId, submitted.turnId);
          }
          assistantIdRef.current = '';
          liveTurnRef.current = null;
          streamingRef.current = false;
          setStreaming(false);
          abortRef.current = null;
          sendingRef.current = false;
          void refreshLatest(payload.sessionId, generation);
          refreshSessions();
        },
        onError: () => {
          if (!current()) return;
          settled = true;
          flushPendingEvents();
          flushTicker();
          const msgId = assistantIdRef.current;
          patchAssistant((draft) => ({
            ...draft,
            turnId: submitted.turnId,
          }));
          if (msgId) {
            void hydrateTrace(msgId, submitted.turnId);
          }
          stopWatchdog();
          controller.abort();
          transition('fail');
          setConnectionNote('连接已结束，正在核对后台实际状态；已收到的内容保留。');
          assistantIdRef.current = '';
          liveTurnRef.current = null;
          streamingRef.current = false;
          setStreaming(false);
          abortRef.current = null;
          sendingRef.current = false;
          void refreshLatest(payload.sessionId, generation);
        },
      },
    );
  };

  const sendMessage = async (text?: string) => {
    const content = (text ?? input).trim();
    if (!content || streaming || sendingRef.current || clarify || continuationBlocked.current) {
      return;
    }
    sendingRef.current = true;
    historyRequest.current += 1;
    setDraftLoading(false);
    const expectedLatestTurnId = draftSourceRef.current;
    const sessionId = activeSessionId ?? newSessionId();
    setActiveSessionId(sessionId);
    window.history.replaceState(window.history.state, '', sessionLocation(sessionId));
    setContinuation(null);
    setInput('');
    setMessages((prev) => [...prev, { id: nextMessageId(), role: 'user', content, trace: [] }]);
    await launchStream({ sessionId, message: content, governanceTarget: governanceTarget ?? undefined,
      ...(expectedLatestTurnId ? { expectedLatestTurnId } : {}) });
  };

  const answerClarify = async (answer: string) => {
    if (!clarify || clarify.toolName !== 'request_clarification' || !clarify.toolCallId.trim()
      || !can('agent:chat:run') || historyLoading || historyError || streaming || sendingRef.current || continuationBlocked.current
      || !answer.trim() || answer.length > 2000) {
      return;
    }
    try { readClarificationQuestion(clarify.question); } catch { return; }
    sendingRef.current = true;
    historyRequest.current += 1;
    setDraftLoading(false);
    const sessionId = activeSessionId!;
    setMessages((prev) => [...prev, { id: nextMessageId(), role: 'user', content: answer, trace: [] }]);
    await launchStream({
      sessionId,
      toolResults: [{ toolCallId: clarify.toolCallId, toolName: clarify.toolName, output: answer }],
    });
  };

  const selectSession = async (sessionId: string, preserveInput = false) => {
    if (streamingRef.current) {
      message.warning('当前正在推理，请等待结束或停止后再切换');
      return;
    }
    const request = ++historyRequest.current;
    stoppingRef.current = null;
    continuationBlocked.current = true;
    setHistoryLoading(true); setHistoryError(''); setContinuation(null); setStopping(false);
    if (!preserveInput) setMessages([]);
    assistantIdRef.current = '';
    if (!preserveInput) draftSourceRef.current = undefined;
    setDraftLoading(false);
    setGovernanceTarget(null);
    if (!preserveInput) setInput('');
    setActiveSessionId(sessionId);
    window.history.replaceState(window.history.state, '', sessionLocation(sessionId));
    setClarify(null);
    try {
      const [historyResult, contextResult] = await Promise.allSettled([
        agentSessionApi.history(sessionId), agentSessionApi.continuation(sessionId),
      ]);
      if (request !== historyRequest.current) return;
      const assistantCounts = new Map<string, number>();
      if (historyResult.status === 'fulfilled') for (const turn of historyResult.value) {
        if (turn.role === 'assistant' && turn.turnId) assistantCounts.set(turn.turnId, (assistantCounts.get(turn.turnId) ?? 0) + 1);
      }
      const restored = (historyResult.status === 'fulfilled' ? historyResult.value : [])
        .filter((turn) => (turn.role === 'user' || turn.role === 'assistant' || turn.role === 'error') && turn.content)
        .map((turn) => ({
          id: nextMessageId(),
          role: turn.role as UIMessage['role'],
          content: turn.content,
          turnId: turn.turnId ?? undefined,
          unlinkedHistory: turn.role === 'assistant' && !turn.turnId,
          persistedHistory: turn.role === 'assistant' && !!turn.turnId && assistantCounts.get(turn.turnId) === 1,
          trace: (turn.turnId ? turn.trace ?? [] : []).map((step, idx) => ({
            key: `${step.kind === 'think' ? 't' : 'c'}-${step.toolCallId ?? idx}`,
            kind: step.kind as 'think' | 'call',
            text: step.text ?? '',
            toolCallId: step.toolCallId ?? undefined,
            toolName: step.toolName ?? undefined,
            running: false,
            resultText: step.resultText ?? undefined,
          })),
        }));
      if (historyResult.status === 'fulfilled') setMessages(restored);
      if (historyResult.status === 'rejected') throw historyResult.reason;
      if (contextResult.status === 'rejected') throw contextResult.reason;
      const view = readContinuation(contextResult.value, sessionId);
      setContinuation(view);
      setConnectionNote('');
      setGovernanceTarget(view.governanceTarget ?? null);
      continuationBlocked.current = Boolean(view.blockingReason);
      if (view.clarification) setClarify({ ...view.clarification, options: [] });
      // 历史回放切换（O2）：最近一条 assistant 轮次自动水合权威链路（入参/结果/失败归因不丢）
      const lastAssistant = [...restored].reverse().find((m) => m.role === 'assistant' && m.turnId);
      if (lastAssistant?.turnId) {
        void hydrateTrace(lastAssistant.id, lastAssistant.turnId);
      }
    } catch (error) {
      if (request === historyRequest.current) {
        continuationBlocked.current = true;
        setHistoryError(`会话恢复失败，已暂停继续：${(error as Error).message}`);
      }
    } finally {
      if (request === historyRequest.current) setHistoryLoading(false);
    }
  };

  const startNewSession = () => {
    if (streamingRef.current) {
      message.warning('当前正在推理，请先停止');
      return;
    }
    historyRequest.current += 1;
    liveTurnRef.current = null; setConnectionNote('');
    stoppingRef.current = null;
    draftSourceRef.current = undefined; setDraftLoading(false);
    continuationBlocked.current = false;
    setHistoryLoading(false); setHistoryError(''); setContinuation(null); setStopping(false);
    window.history.replaceState(window.history.state, '', sessionLocation(null));
    setGovernanceTarget(null);
    setInput('');
    setActiveSessionId(null);
    setMessages([]);
    setClarify(null);
  };

  const refreshLatest = async (sessionId: string, generation: number) => {
    continuationBlocked.current = true;
    try {
      const view = readContinuation(await agentSessionApi.continuation(sessionId), sessionId);
      if (generation !== historyRequest.current) return;
      setContinuation(view); setGovernanceTarget(view.governanceTarget ?? null);
      setConnectionNote((note) => note ? '连接已结束；已核对后台状态，请按下方实际状态继续。' : '');
      continuationBlocked.current = Boolean(view.blockingReason);
      if (view.clarification) setClarify({ ...view.clarification, options: [] });
    } catch {
      if (generation === historyRequest.current) setHistoryError('最新状态未确认，请刷新会话核对后继续。');
    }
  };

  const fillQuestionDraft = async () => {
    if (!can('agent:chat:run') || !activeSessionId || !isTerminal(continuation) || inputValueRef.current.trim()
      || draftLoading || streaming || historyLoading || historyError || continuationBlocked.current) return;
    const generation = historyRequest.current;
    const shown = continuation!;
    setDraftLoading(true);
    try {
      const view = readContinuation(await agentSessionApi.continuation(activeSessionId), activeSessionId);
      if (generation !== historyRequest.current) return;
      if (view.turnId !== shown.turnId || view.status !== shown.status
        || JSON.stringify(view.governanceTarget) !== JSON.stringify(shown.governanceTarget)) {
        setHistoryError('任务已变化，请刷新会话后核对。'); continuationBlocked.current = true; return;
      }
      setContinuation(view);
      if (view.blockingReason || !view.questionDraft || inputValueRef.current.trim()) return;
      draftSourceRef.current = view.turnId!;
      setInput(view.questionDraft);
    } catch {
      if (generation === historyRequest.current) { setHistoryError('原问题未能核对，请刷新会话。'); continuationBlocked.current = true; }
    } finally {
      if (generation === historyRequest.current) setDraftLoading(false);
    }
  };

  React.useEffect(() => {
    if (initialSession.current) void selectSession(initialSession.current);
    return () => {
      historyRequest.current += 1;
      abortRef.current?.abort();
      if (tickerRef.current) clearInterval(tickerRef.current);
      if (pipelineTimerRef.current) clearTimeout(pipelineTimerRef.current);
      stopWatchdog();
    };
  }, []);

  const following = continuation?.status === 'QUEUED' || continuation?.status === 'RUNNING';
  const followingRequest = historyRequest.current;
  useSessionFollow({
    sessionId: activeSessionId,
    turnId: continuation?.turnId ?? null,
    active: following && !streaming && !historyLoading && !historyError && !stopping,
    generation: followingRequest,
    onActive: (view) => { if (followingRequest === historyRequest.current) setContinuation(view); },
    onSettled: () => { if (followingRequest === historyRequest.current && activeSessionId) void selectSession(activeSessionId, true); },
    onPause: (reason) => {
      if (followingRequest === historyRequest.current) { continuationBlocked.current = true; setHistoryError(reason); }
    },
  });

  const stopConfirmedTurn = async (turnId: string, sessionId: string) => {
    if (stoppingRef.current != null || !can('agent:chat:run')) return;
    const request = ++historyRequest.current;
    stoppingRef.current = request;
    continuationBlocked.current = true;
    flushPendingEvents(); flushTicker(); stopWatchdog();
    abortRef.current?.abort(); abortRef.current = null;
    liveTurnRef.current = null; streamingRef.current = false;
    sendingRef.current = false; setStreaming(false);
    setConnectionNote('正在请求停止所见轮次，请等待实际状态核对。');
    setStopping(true);
    try {
      await agentChatApi.cancelTurn(turnId);
      if (request === historyRequest.current) await selectSession(sessionId, true);
    } catch (error) {
      if (request === historyRequest.current) {
        // A lost acknowledgement cannot prove whether the stop reached the server.
        setHistoryError('停止请求未确认，请刷新会话核对状态。');
        setConnectionNote('');
      }
    } finally {
      if (stoppingRef.current === request) stoppingRef.current = null;
      if (request === historyRequest.current) setStopping(false);
    }
  };

  const stopRestoredTurn = async () => {
    if (continuation?.turnId && activeSessionId && following && !stopping) {
      await stopConfirmedTurn(continuation.turnId, activeSessionId);
    }
  };

  const stopLiveTurn = () => {
    if (!can('agent:chat:run')) return;
    if (clarify) {
      setConnectionNote('本轮正在等待补充信息，请回答原问题或刷新核对。');
      return;
    }
    const turnId = liveTurnRef.current;
    if (!turnId || !activeSessionId) {
      setConnectionNote('提交尚未确认，暂不能停止；请等待回执后核对。');
      return;
    }
    void stopConfirmedTurn(turnId, activeSessionId);
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

  const scenarioReviews = new Map(messages.filter(item => item.role === 'assistant').map(item => [item.id,
    readScenarioHistory(item.content, activeSessionId, item.turnId, !!item.persistedHistory,
      !historyLoading && !historyError && !streaming && can('agent:session:read') ? continuation : null)]));
  const bubbleItems: BubbleItemType[] = messages.map((item) => {
    const scenarioReview = scenarioReviews.get(item.id);
    return ({
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
        <Alert type="error" showIcon message={item.content}
          description="独立失败记录；此处展示位置不代表它与前面回答的执行顺序。" />
      ) : (
        <Space direction="vertical" style={{ maxWidth: '100%' }}>
          {item.unlinkedHistory && <Typography.Text type="secondary">
            未能确定这段历史回答所属的执行轮次，暂不展示关联执行证据。
          </Typography.Text>}
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
                  </Typography.Text>
                );
              })()
            : null}
          {item.content ? (
            <Typography.Text style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {item.role === 'assistant' ? visibleGovernanceText(scenarioReview?.status === 'READY'
                ? scenarioReview.text : item.content) : item.content}
            </Typography.Text>
          ) : null}
          {item.role === 'assistant' && item.content ? <GovernanceEvidenceCards text={item.content} /> : null}
          {item.role === 'assistant' && item.content ? <ScenarioHistoryCard review={scenarioReview ?? { status: 'NONE' }} /> : null}
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
                return (
                  <Alert
                    type={presentation.severity}
                    showIcon
                    message={presentation.title}
                    description={
                      <Space direction="vertical" size={4}>
                        <span>{presentation.hint}</span>
                      </Space>
                    }
                  />
                );
              })()
            : null}
        </Space>
      ),
    loading: item.role === 'assistant' && !item.content && !item.trace.length && !item.error && streaming,
    });
  });

  const conversationItems: ConversationsProps['items'] = sessions.map((session) => ({
    key: session.sessionId,
    label: session.title?.trim() || `会话 ${session.sessionId.slice(-8)}`,
  }));

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
            message={governanceTaskTitle(governanceTarget)}
            description={
              <Space wrap>
                <span>{governanceTarget.purpose === 'MODEL_STRUCTURE_REVIEW' ? '固定已保存比较；当前映射没有历史快照。准备问题后请核对并明确发送。' : governanceTarget.purpose === 'CONSUMER_VERSION_IMPACT'
                  ? '仅核对所选来源版本的归属、有效订阅与该版本成功使用；读取窗口有上限，覆盖缺口需回原页面人工核查。'
                  : governanceTarget.purpose === 'ASSET_IMPACT'
                  ? '仅说明所选资产的一跳结构关系、已接入业务使用和页面活动；保留范围与缺口，不判断完整影响或发布安全。'
                  : governanceTarget.qualityBaselineExecutionNo
                  ? '仅比较选定的两次历史执行；历史定义变化、截断和缺口需人工核对，不直接认定质量改善。'
                  : governanceTarget.qualityExecutionNo !== undefined
                  ? '围绕本次历史执行核对事实、缺口和人工检查步骤；具体根因需验证，调整规则请回源页面另行发起。'
                  : '按当前权限读取证据，解读完成后可回到来源核验。'}</span>
                {governanceQuestions(governanceTarget).map((question, index) => (
                  <Button key={question} size="small" disabled={preparationBlocked || hasText || draftLoading}
                    onClick={() => { if (!inputValueRef.current.trim() && !preparationBlocked) { draftSourceRef.current = undefined; setInput(question); } }}>
                    {governanceTarget.purpose === 'MODEL_STRUCTURE_REVIEW' ? '准备结构变更说明' : governanceTarget.purpose === 'CONSUMER_VERSION_IMPACT' ? '准备消费影响说明' : governanceTarget.purpose === 'ASSET_IMPACT' ? '准备影响说明' : governanceTarget.qualityBaselineExecutionNo ? '比较两次执行' : governanceTarget.qualityExecutionNo !== undefined ? (index === 0 ? '解读与排查' : '补充排查信息')
                      : governanceTarget.qualityMonitorId !== undefined ? '生成规则候选'
                        : governanceTarget.purpose === 'ASSET_DESCRIPTION' ? '生成描述候选' : (index === 0 ? '解释结果' : '排查建议')}
                  </Button>
                ))}
                <Button size="small" href={governanceSourcePath(governanceTarget)}>
                  {governanceTarget.qualityExecutionNo !== undefined ? '返回本次执行核对' : '返回来源'}
                </Button>
                {governanceTarget.qualityBaselineExecutionNo && <Button size="small"
                  href={`/data-quality/execution/${encodeURIComponent(governanceTarget.qualityBaselineExecutionNo)}`}>返回基准执行核对</Button>}
              </Space>
            }
            style={{ marginBottom: 12 }}
          />
        )}
        {governanceTarget && <QuestionPreparationPanel
          key={`${activeSessionId ?? 'new'}:${JSON.stringify(governanceTarget)}:${continuation?.turnId ?? ''}`}
          target={governanceTarget} disabled={preparationBlocked || draftLoading} hasInput={hasText}
          onFill={(question) => { if (!inputValueRef.current.trim() && !preparationBlocked) { draftSourceRef.current = undefined; setInput(question); } }} />}
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
                        {governanceTarget ? <Typography.Text type="secondary">请使用上方任务问题或提问准备，核对当前所选对象后明确发送。</Typography.Text>
                          : <><Typography.Text type="secondary">用自然语言描述需求，例如：</Typography.Text>
                        <Space wrap style={{ justifyContent: 'center', marginTop: 12 }}>
                          {['上个月各区域销售额是多少？', '最近30天订单量趋势如何？', '帮我看看销量最高的10个商品'].map(
                            (sample) => (
                              <Button key={sample} size="small" disabled={preparationBlocked || hasText}
                                onClick={() => { if (!inputValueRef.current.trim() && !preparationBlocked) setInput(sample); }}>
                                {sample}
                              </Button>
                            ),
                          )}
                        </Space>
                        </>}
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

                  {clarify && (clarify.toolName !== 'request_clarification' || !clarify.toolCallId.trim()
                    ? <Alert type="error" message="待答问题暂无法核对，请刷新原会话。" />
                    : <QueryClarificationCard
                    key={JSON.stringify([activeSessionId, clarify.toolCallId, clarify.question])}
                    question={clarify.question}
                    disabled={!can('agent:chat:run') || streaming || historyLoading || !!historyError || !!continuation?.blockingReason}
                    onAnswer={answer => void answerClarify(answer)} />)}

                  <div ref={inputRef} tabIndex={-1} className={sendIdle ? styles.sendDisabled : undefined}>
                    {historyLoading && <Alert type="info" showIcon message="正在恢复会话与任务范围，请稍候…" />}
                    {connectionNote && <Alert type="info" showIcon message={connectionNote} />}
                    {(historyError || continuation?.blockingReason) && <Alert type="warning" showIcon
                      message={historyError || continuation?.blockingReason}
                      action={<Space>
                        <Button disabled={streaming || historyLoading || stopping} onClick={() => activeSessionId && void selectSession(activeSessionId, true)}>刷新会话</Button>
                        {following && <Button disabled={!can('agent:chat:run') || stopping || historyLoading} loading={stopping} onClick={() => void stopRestoredTurn()}>停止本轮</Button>}
                      </Space>} />}
                    {following && !historyError && <Typography.Text type="secondary">正在自动检查状态；页面隐藏时暂停，达到检查上限后可手动刷新。</Typography.Text>}
                    {continuation?.status && <p>最近轮次状态：{turnStatusLabels[continuation.status]}；历史证据仅供回看，继续提问会重新读取并检查权限。</p>}
                    {activeSessionId && continuation?.status && !historyError && !continuation.blockingReason && <Button
                      disabled={streaming || historyLoading || stopping || !can('agent:session:read')}
                      onClick={() => void selectSession(activeSessionId, true)}>刷新会话</Button>}
                    {isTerminal(continuation) && !historyError && !continuation?.blockingReason && <Alert showIcon
                      type={continuation?.status === 'FAILED' ? 'warning' : 'info'}
                      message={continuation?.status === 'FAILED' ? presentError(continuation.errorCode).title
                        : continuation?.status === 'CANCELLED' ? '本轮后续生成已停止'
                          : continuation?.status === 'INTERRUPTED' ? '本轮已中断' : '本轮推理已结束'}
                      description={<Space direction="vertical">
                        <span>{continuation?.status === 'FAILED' ? presentError(continuation.errorCode).hint
                          : '已发生的调用与证据保留。推理结束不代表质量通过或问题已解决；重新提问会创建新轮。'}</span>
                        <span>请核对当前任务范围，重新补充需要的澄清背景。</span>
                        {continuation?.questionDraft ? <Button disabled={!can('agent:chat:run') || hasText || draftLoading || historyLoading || streaming}
                          loading={draftLoading} onClick={() => void fillQuestionDraft()}>填入本轮问题</Button>
                          : <span>{continuation?.draftUnavailableReason ?? '原问题不可用，请手工整理新问题。'}</span>}
                        {hasText && <span>已有未发送内容，保留编辑；清空后可填写原问题。</span>}
                      </Space>} />}
                    <Sender
                      value={input}
                      onChange={(value) => { if (!value.trim()) draftSourceRef.current = undefined; setInput(value); }}
                      onSubmit={(message) => sendMessage(message)}
                      onCancel={stopLiveTurn}
                      loading={streaming}
                      disabled={Boolean(clarify) || historyLoading || Boolean(historyError || continuation?.blockingReason)}
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

export default function ScopedAiAgentPage() {
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const entry = new URLSearchParams(window.location.search);
  if (!entry.get('sessionId') && ['purpose', 'consumerProductType', 'consumerProductIdentity', 'consumerVersionIdentity', 'reviewModelId', 'reviewBaselineVersionNo', 'reviewDefinition'].some((key) => entry.has(key)) && !parseGovernanceTarget(window.location.search)) {
    return <Alert type="error" showIcon message="治理任务入口无效" description="请返回来源页面重新选择资产与任务。" />;
  }
  return <AiAgentPage key={JSON.stringify([currentProject?.id, can('agent:session:read')])} />;
}
