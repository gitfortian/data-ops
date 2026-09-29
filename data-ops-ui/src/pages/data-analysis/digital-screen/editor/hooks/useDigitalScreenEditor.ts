import type { PublishedDataset } from '@/services/dataset';
import { listPublishedDatasets } from '@/services/dataset';
import {
  getDigitalScreen,
  listDigitalScreenVersions,
  publishDigitalScreen,
  rollbackDigitalScreen,
  unpublishDigitalScreen,
  updateDigitalScreen,
  type DigitalScreenBindings,
  type DigitalScreenComponentBinding,
  type DigitalScreenInstance,
  type DigitalScreenVersionSummary,
} from '@/services/digital-screen';
import { resolveScreenTemplateById } from '@/services/screen-template-service';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { isBindableScreenComponent } from '../../runtime/binding';
import { useScreenRuntime } from '../../runtime/hooks/useScreenRuntime';

const sameBindings = (left: DigitalScreenBindings, right: DigitalScreenBindings) => (
  JSON.stringify(left) === JSON.stringify(right)
);

export function useDigitalScreenEditor(id?: string) {
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [bindings, setBindings] = useState<DigitalScreenBindings>({});
  const [selectedComponentId, setSelectedComponentId] = useState<string>();
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [datasetsError, setDatasetsError] = useState('');
  const [versions, setVersions] = useState<DigitalScreenVersionSummary[]>([]);
  const [isDatasetsLoading, setIsDatasetsLoading] = useState(true);
  const [isVersionsLoading, setIsVersionsLoading] = useState(false);
  const [isVersionsOpen, setIsVersionsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [isOfflining, setIsOfflining] = useState(false);
  const [rollingBackVersionNo, setRollingBackVersionNo] = useState<number>();

  const template = useMemo(
    () => (screen ? resolveScreenTemplateById(screen.templateId) : undefined),
    [screen],
  );
  const selectedComponent = useMemo(
    () => template?.components.find((component) => component.id === selectedComponentId),
    [template, selectedComponentId],
  );
  const runtime = useScreenRuntime(template, bindings, datasets);
  const isDirty = useMemo(() => {
    if (!screen) return false;
    return name.trim() !== screen.name
      || (description.trim() || '') !== (screen.description || '')
      || !sameBindings(bindings, screen.bindings || {});
  }, [bindings, description, name, screen]);

  const applyScreen = useCallback((detail: DigitalScreenInstance) => {
    setScreen(detail);
    setName(detail.name);
    setDescription(detail.description || '');
    setBindings(detail.bindings || {});
  }, []);

  const loadScreen = useCallback(async () => {
    if (!id) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    try {
      applyScreen(await getDigitalScreen(id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载数字化大屏失败');
    } finally {
      setIsLoading(false);
    }
  }, [applyScreen, id]);

  const loadVersions = useCallback(async () => {
    if (!id) return;
    setIsVersionsLoading(true);
    try {
      setVersions(await listDigitalScreenVersions(id));
    } catch (error) {
      setVersions([]);
      message.error(error instanceof Error ? error.message : '加载发布版本失败');
    } finally {
      setIsVersionsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadScreen();
  }, [loadScreen]);

  useEffect(() => {
    let active = true;
    setIsDatasetsLoading(true);
    setDatasetsError('');
    void listPublishedDatasets()
      .then((values) => {
        if (active) setDatasets(values);
      })
      .catch((error) => {
        if (!active) return;
        setDatasets([]);
        setDatasetsError(error instanceof Error ? error.message : '加载 Dataset 失败');
      })
      .finally(() => {
        if (active) setIsDatasetsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!template) return;
    if (selectedComponentId && template.components.some((component) => component.id === selectedComponentId)) return;
    const preferred = template.components.find(isBindableScreenComponent)
      || template.components[0];
    setSelectedComponentId(preferred?.id);
  }, [template, selectedComponentId]);

  const saveScreen = async (showSuccessMessage = true) => {
    if (!id || !screen) return undefined;
    if (!name.trim()) {
      message.warning('请输入大屏名称');
      return undefined;
    }
    if (!isDirty) {
      if (showSuccessMessage) message.info('当前草稿已是最新');
      return screen;
    }

    setIsSaving(true);
    try {
      const updated = await updateDigitalScreen(id, { name, description, bindings });
      applyScreen(updated);
      if (showSuccessMessage) message.success('草稿已保存');
      return updated;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存数字化大屏失败');
      return undefined;
    } finally {
      setIsSaving(false);
    }
  };

  const publishScreen = async () => {
    if (!id || !screen) return;
    setIsPublishing(true);
    try {
      const saved = await saveScreen(false);
      if (!saved) return;
      const updated = await publishDigitalScreen(id);
      applyScreen(updated);
      message.success(`大屏已发布${updated.publishedVersionNo ? ` · V${updated.publishedVersionNo}` : ''}`);
      if (isVersionsOpen) void loadVersions();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布数字化大屏失败');
    } finally {
      setIsPublishing(false);
    }
  };

  const offlineScreen = async () => {
    if (!id || !screen || screen.status !== 'published') return;
    setIsOfflining(true);
    try {
      const updated = await unpublishDigitalScreen(id);
      applyScreen(updated);
      message.success('大屏已取消发布，历史版本已保留');
      if (isVersionsOpen) void loadVersions();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消发布失败');
    } finally {
      setIsOfflining(false);
    }
  };

  const openVersions = () => {
    setIsVersionsOpen(true);
    void loadVersions();
  };

  const rollbackVersion = async (versionNo: number) => {
    if (!id) return;
    setRollingBackVersionNo(versionNo);
    try {
      const updated = await rollbackDigitalScreen(id, versionNo);
      applyScreen(updated);
      message.success(`已回滚 V${versionNo}，并追加发布为 V${updated.publishedVersionNo}`);
      await loadVersions();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '回滚发布版本失败');
    } finally {
      setRollingBackVersionNo(undefined);
    }
  };

  const updateSelectedBinding = (next?: DigitalScreenComponentBinding) => {
    if (!selectedComponent) return;
    setBindings((current) => {
      const result = { ...current };
      if (next) result[selectedComponent.id] = next;
      else delete result[selectedComponent.id];
      return result;
    });
  };

  const bindableCount = template?.components.filter(isBindableScreenComponent).length ?? 0;
  const isSelectedQuerying = selectedComponent
    ? runtime.loadingIds.includes(selectedComponent.id)
    : false;
  const selectedQueryError = selectedComponent
    ? runtime.errors[selectedComponent.id]
    : undefined;

  return {
    screen,
    name,
    description,
    bindings,
    selectedComponentId,
    datasets,
    datasetsError,
    versions,
    isDatasetsLoading,
    isVersionsLoading,
    isVersionsOpen,
    isLoading,
    isSaving,
    isPublishing,
    isOfflining,
    rollingBackVersionNo,
    isDirty,
    template,
    selectedComponent,
    runtime,
    bindableCount,
    isSelectedQuerying,
    selectedQueryError,
    setName,
    setDescription,
    setSelectedComponentId,
    setIsVersionsOpen,
    saveScreen,
    publishScreen,
    offlineScreen,
    openVersions,
    rollbackVersion,
    updateSelectedBinding,
  };
}
