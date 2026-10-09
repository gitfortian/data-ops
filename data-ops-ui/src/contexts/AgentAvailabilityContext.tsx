import { useModel } from '@umijs/max';
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { getApplicationFeatures } from '@/services/applicationFeatures';

interface AgentAvailability {
  agentEnabled?: boolean;
  loading: boolean;
  failed: boolean;
  refresh: () => void;
}

const AgentAvailabilityContext = createContext<AgentAvailability>({
  loading: true, failed: false, refresh: () => undefined,
});

/** An authenticated read projection of the server switch, never a substitute for RBAC. */
export function AgentAvailabilityProvider({ children }: { children: ReactNode }) {
  const { initialState } = useModel('@@initialState');
  const userId = initialState?.currentUser?.userid;
  const [revision, setRevision] = useState(0);
  const [snapshot, setSnapshot] = useState<{
    userId: string; revision: number; agentEnabled?: boolean; failed: boolean;
  }>();
  const refresh = useCallback(() => setRevision(value => value + 1), []);

  useEffect(() => {
    if (!userId) return;
    let active = true;
    void getApplicationFeatures().then(value => {
      if (active) setSnapshot({ userId, revision, agentEnabled: value.agentEnabled, failed: false });
    }).catch(() => {
      if (active) setSnapshot({ userId, revision, failed: true });
    });
    return () => { active = false; };
  }, [userId, revision]);

  const current = userId && snapshot?.userId === userId && snapshot.revision === revision
    ? snapshot : undefined;
  return <AgentAvailabilityContext.Provider value={{
    agentEnabled: current?.agentEnabled,
    loading: Boolean(userId && !current),
    failed: current?.failed ?? false,
    refresh,
  }}>{children}</AgentAvailabilityContext.Provider>;
}

export const useAgentAvailability = () => useContext(AgentAvailabilityContext);
