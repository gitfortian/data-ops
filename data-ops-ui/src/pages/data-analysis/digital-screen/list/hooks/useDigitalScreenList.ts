import { resolveScreenTemplateById } from '@/services/screen-template-service';
import {
  deleteDigitalScreen,
  duplicateDigitalScreen,
  listDigitalScreens,
  type DigitalScreenInstance,
  type DigitalScreenStatus,
} from '@/services/digital-screen';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

export type DigitalScreenStatusFilter = 'all' | DigitalScreenStatus;

export interface DigitalScreenStatusItem {
  key: DigitalScreenStatusFilter;
  label: string;
  count: number;
}

export function useDigitalScreenList() {
  const [screens, setScreens] = useState<DigitalScreenInstance[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<DigitalScreenStatusFilter>('all');

  const loadScreens = useCallback(async () => {
    setIsLoading(true);
    try {
      setScreens(await listDigitalScreens());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载数字化大屏失败');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadScreens();
  }, [loadScreens]);

  const filteredScreens = useMemo(() => {
    const searchKeyword = keyword.trim().toLowerCase();
    return screens.filter((screen) => {
      if (status !== 'all' && screen.status !== status) return false;
      if (!searchKeyword) return true;
      const template = resolveScreenTemplateById(screen.templateId);
      return [screen.name, screen.description, template?.name]
        .some((field) => String(field || '').toLowerCase().includes(searchKeyword));
    });
  }, [keyword, screens, status]);

  const statusItems = useMemo<DigitalScreenStatusItem[]>(() => [
    { key: 'all', label: '全部', count: screens.length },
    { key: 'draft', label: '草稿', count: screens.filter((item) => item.status === 'draft').length },
    { key: 'published', label: '已发布', count: screens.filter((item) => item.status === 'published').length },
  ], [screens]);

  const duplicateScreen = useCallback(async (screen: DigitalScreenInstance) => {
    try {
      const duplicated = await duplicateDigitalScreen(screen.id);
      message.success('已复制大屏');
      await loadScreens();
      return duplicated;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复制大屏失败');
      return undefined;
    }
  }, [loadScreens]);

  const removeScreen = useCallback(async (screen: DigitalScreenInstance) => {
    try {
      await deleteDigitalScreen(screen.id);
      message.success('大屏已删除');
      await loadScreens();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除大屏失败');
    }
  }, [loadScreens]);

  const resetFilters = useCallback(() => {
    setKeyword('');
    setStatus('all');
  }, []);

  return {
    screens,
    filteredScreens,
    statusItems,
    status,
    keyword,
    isLoading,
    setStatus,
    setKeyword,
    resetFilters,
    duplicateScreen,
    removeScreen,
  };
}
