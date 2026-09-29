import type { PublishedDataset } from '@/services/dataset';
import { resolvePublishedDatasetsByIds } from '@/services/dataset';
import {
  getPublishedDigitalScreen,
  type DigitalScreenBindings,
  type DigitalScreenInstance,
} from '@/services/digital-screen';
import { resolveScreenTemplateById } from '@/services/screen-template-service';
import { useEffect, useMemo, useState } from 'react';
import { useScreenRuntime } from '../../runtime/hooks/useScreenRuntime';
import { collectScreenRuntimeDatasetIds } from '../../runtime/planner';
import { SCREEN_RUNTIME_VIEWER_REFRESH_INTERVAL_MS } from '../../runtime/policy';

const EMPTY_BINDINGS: DigitalScreenBindings = {};
const VIEWER_RUNTIME_OPTIONS = {
  refreshIntervalMs: SCREEN_RUNTIME_VIEWER_REFRESH_INTERVAL_MS,
} as const;

export function useDigitalScreenViewer(id?: string) {
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [loadError, setLoadError] = useState('');
  const [dataError, setDataError] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!id) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setLoadError('');
    void getPublishedDigitalScreen(id)
      .then(setScreen)
      .catch((error) => {
        setScreen(undefined);
        setLoadError(error instanceof Error ? error.message : '加载已发布大屏失败');
      })
      .finally(() => setIsLoading(false));
  }, [id]);

  const template = useMemo(
    () => (screen ? resolveScreenTemplateById(screen.templateId) : undefined),
    [screen],
  );
  const runtimeDatasetIds = useMemo(
    () => collectScreenRuntimeDatasetIds(template, screen?.bindings ?? EMPTY_BINDINGS),
    [screen, template],
  );
  const runtimeDatasetKey = runtimeDatasetIds.join('|');

  useEffect(() => {
    if (!runtimeDatasetIds.length) {
      setDatasets([]);
      setDataError('');
      return undefined;
    }

    const controller = new AbortController();
    setDataError('');
    void resolvePublishedDatasetsByIds(runtimeDatasetIds, { signal: controller.signal })
      .then(({ datasets: values, errors }) => {
        if (controller.signal.aborted) return;
        setDatasets(values);
        const messages = Object.values(errors);
        setDataError(messages.length
          ? `${messages.length} 个绑定 Dataset 元数据加载失败：${messages[0]}`
          : '');
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        setDatasets([]);
        setDataError(error instanceof Error ? error.message : '加载大屏绑定 Dataset 失败');
      });

    return () => controller.abort();
  }, [runtimeDatasetIds, runtimeDatasetKey]);

  const runtime = useScreenRuntime(
    template,
    screen?.bindings ?? EMPTY_BINDINGS,
    datasets,
    VIEWER_RUNTIME_OPTIONS,
  );

  return {
    screen,
    template,
    runtime,
    loadError,
    dataError,
    isLoading,
  };
}
