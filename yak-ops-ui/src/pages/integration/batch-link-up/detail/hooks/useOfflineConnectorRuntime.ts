import { useCallback, useEffect, useState } from 'react';

import {
  getOfflineSyncConnectorRuntime,
  type OfflineConnectorRuntimeSnapshot,
} from '@/services/batch-link-up';

export interface OfflineConnectorRuntimeState {
  snapshot: OfflineConnectorRuntimeSnapshot | null;
  loading: boolean;
  error: string;
  reload: () => Promise<void>;
}

export default function useOfflineConnectorRuntime(): OfflineConnectorRuntimeState {
  const [snapshot, setSnapshot] =
    useState<OfflineConnectorRuntimeSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const reload = useCallback(async () => {
    try {
      setLoading(true);
      const next = await getOfflineSyncConnectorRuntime();
      setSnapshot(next);
      setError('');
    } catch (cause: any) {
      setError(cause?.message || '无法读取 Link-Up Connector 运行时能力');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;

    const load = async () => {
      try {
        const next = await getOfflineSyncConnectorRuntime();
        if (!active) return;
        setSnapshot(next);
        setError('');
      } catch (cause: any) {
        if (!active) return;
        setError(cause?.message || '无法读取 Link-Up Connector 运行时能力');
      } finally {
        if (active) setLoading(false);
      }
    };

    void load();

    return () => {
      active = false;
    };
  }, []);

  return { snapshot, loading, error, reload };
}
