import React from 'react';
import { agentSessionApi } from '@/services/agent';
import { readContinuation, type SessionContinuation } from '@/services/agent/continuation';

export const SESSION_FOLLOW_INTERVAL = 3000;
export const SESSION_FOLLOW_LIMIT = 20;

interface FollowOptions {
  sessionId: string | null;
  turnId: string | null;
  active: boolean;
  generation: number;
  onActive: (view: SessionContinuation) => void;
  onSettled: () => void;
  onPause: (reason: string) => void;
}

/** A bounded read schedule, never a lifecycle owner or an inference retry loop. */
export function useSessionFollow(options: FollowOptions): void {
  const callbacks = React.useRef(options);
  callbacks.current = options;
  const { sessionId, turnId, active, generation } = options;
  React.useEffect(() => {
    if (!active || !sessionId || !turnId) return;
    // Keep callbacks with this read generation; late results cannot adopt a new session's handlers.
    const handlers = callbacks.current;
    let disposed = false;
    let busy = false;
    let attempts = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const clearTimer = () => { if (timer !== undefined) clearTimeout(timer); timer = undefined; };
    const schedule = () => {
      if (!disposed && !document.hidden) timer = setTimeout(check, SESSION_FOLLOW_INTERVAL);
    };
    const check = async () => {
      clearTimer();
      if (disposed || busy || document.hidden) return;
      if (attempts >= SESSION_FOLLOW_LIMIT) {
        disposed = true;
        handlers.onPause('自动检查已暂停，请手动刷新会话核对最新状态。');
        return;
      }
      busy = true;
      attempts += 1;
      try {
        const view = readContinuation(await agentSessionApi.continuation(sessionId), sessionId);
        if (disposed) return;
        if (view.turnId !== turnId || (view.status !== 'QUEUED' && view.status !== 'RUNNING')) {
          disposed = true;
          handlers.onSettled();
          return;
        }
        handlers.onActive(view);
        if (attempts >= SESSION_FOLLOW_LIMIT) {
          disposed = true;
          handlers.onPause('自动检查已暂停，请手动刷新会话核对最新状态。');
        }
      } catch (error) {
        if (!disposed) {
          disposed = true;
          handlers.onPause(`状态检查失败，已暂停继续，请刷新核对：${(error as Error).message}`);
        }
      } finally {
        busy = false;
        schedule();
      }
    };
    const visibility = () => {
      clearTimer();
      if (!document.hidden) void check();
    };
    document.addEventListener('visibilitychange', visibility);
    schedule();
    return () => { disposed = true; clearTimer(); document.removeEventListener('visibilitychange', visibility); };
  }, [sessionId, turnId, active, generation]);
}
