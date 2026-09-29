import type { ScreenTemplate } from '@/components/screen-engine';
import { createDigitalScreen } from '@/services/digital-screen';
import { listAvailableScreenTemplates } from '@/services/screen-template-service';
import { message } from 'antd';
import { useMemo, useState } from 'react';

export function useDigitalScreenTemplates() {
  const [category, setCategory] = useState('全部');
  const [keyword, setKeyword] = useState('');
  const [previewTemplate, setPreviewTemplate] = useState<ScreenTemplate>();
  const [selectedTemplate, setSelectedTemplate] = useState<ScreenTemplate>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  const availableTemplates = useMemo(
    () => listAvailableScreenTemplates().map((record) => record.template),
    [],
  );
  const categories = useMemo(
    () => ['全部', ...new Set(availableTemplates.map((template) => template.category))],
    [availableTemplates],
  );
  const templates = useMemo(() => {
    const searchKeyword = keyword.trim().toLowerCase();
    return availableTemplates.filter((template) => {
      if (category !== '全部' && template.category !== category) return false;
      if (!searchKeyword) return true;
      return [template.name, template.description, template.category]
        .some((field) => String(field || '').toLowerCase().includes(searchKeyword));
    });
  }, [availableTemplates, category, keyword]);

  const openCreate = (template: ScreenTemplate) => {
    setSelectedTemplate(template);
    setName(template.name);
    setDescription('');
  };

  const closeCreate = () => setSelectedTemplate(undefined);

  const createScreen = async () => {
    if (!selectedTemplate) return undefined;
    if (!name.trim()) {
      message.warning('请输入大屏名称');
      return undefined;
    }

    setIsCreating(true);
    try {
      const screen = await createDigitalScreen({
        name,
        description,
        templateId: selectedTemplate.id,
      });
      message.success('数字化大屏已创建');
      setSelectedTemplate(undefined);
      return screen;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建数字化大屏失败');
      return undefined;
    } finally {
      setIsCreating(false);
    }
  };

  return {
    category,
    keyword,
    previewTemplate,
    selectedTemplate,
    name,
    description,
    isCreating,
    categories,
    templates,
    setCategory,
    setKeyword,
    setPreviewTemplate,
    setName,
    setDescription,
    openCreate,
    closeCreate,
    createScreen,
  };
}
