import {
  listAllDataSources,
  listDataSourceColumns,
  type DataSourceRecord,
} from '@/services/data-source';
import {
  createQualityMonitor,
  getQualityEditorSnapshot,
  listQualityTemplates,
  updateQualityMonitor,
  validateQualitySuggestions,
  type CatalogColumn,
  type SaveMonitorPayload,
  type SaveRulePayload,
  type TemplateView,
} from '@/services/data-quality';
import { history } from '@umijs/max';
import { Form, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { sameRuleConditions } from '@/services/data-quality/ruleComparison';

import {
  buildSettings,
  DEFAULT_NOTIFICATION,
  DEFAULT_SCHEDULE,
  monitorRules,
  notificationFromSettings,
  ruleDefaults,
  scheduleFromSettings,
  validateEditorSettings,
  type EditorRule,
  type NotificationSettingsState,
  type ScheduleSettingsState,
} from '../model';
import { validateRules } from '../RuleEditor';

interface CurrentUserView {
  realName?: string;
  username?: string;
}

interface UseMonitorEditorPageOptions {
  monitorId?: string;
  query: URLSearchParams;
  currentUser?: CurrentUserView;
}

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const hasFormErrors = (error: unknown) =>
  typeof error === 'object' && error !== null && 'errorFields' in error;

export const useMonitorEditorPage = ({
  monitorId,
  query,
  currentUser,
}: UseMonitorEditorPageOptions) => {
  const editing = Boolean(monitorId);
  const [form] = Form.useForm<SaveMonitorPayload>();
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [columns, setColumns] = useState<CatalogColumn[]>([]);
  const [templates, setTemplates] = useState<TemplateView[]>([]);
  const [rules, storeRules] = useState<EditorRule[]>([]);
  const rulesRef = useRef<EditorRule[]>([]);
  const setRules: Dispatch<SetStateAction<EditorRule[]>> = useCallback((value) => {
    const next = typeof value === 'function' ? value(rulesRef.current) : value;
    rulesRef.current = next;
    storeRules(next);
  }, []);
  const [schedule, setSchedule] =
    useState<ScheduleSettingsState>(DEFAULT_SCHEDULE);
  const [notification, setNotification] =
    useState<NotificationSettingsState>(DEFAULT_NOTIFICATION);
  const [nextRunTime, setNextRunTime] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [adopting, setAdopting] = useState(false);
  const [definition, setDefinition] = useState('');
  const editorVersion = useRef(0);
  const operation = useRef<{ kind: 'adopt' | 'save' } | null>(null);

  const dataSourceId = Form.useWatch('dataSourceId', form);
  const storedDataSourceName = Form.useWatch('dataSourceName', form);
  const databaseName = Form.useWatch('databaseName', form);
  const schemaName = Form.useWatch('schemaName', form);
  const tableName = Form.useWatch('tableName', form);

  const selectedSource = useMemo(
    () =>
      dataSources.find((item) => Number(item.id) === Number(dataSourceId)),
    [dataSourceId, dataSources],
  );

  useEffect(() => {
    const version = ++editorVersion.current;
    setDefinition(''); setRules([]); setAdopting(false); setSaving(false);
    operation.current = null;
    const initialize = async () => {
      setLoading(true);
      try {
        const [sourcePage, templatePage] = await Promise.all([
          listAllDataSources(),
          listQualityTemplates(),
        ]);
        if (version !== editorVersion.current) return;
        setDataSources(sourcePage.bizData || []);
        setTemplates(templatePage.records || []);

        if (editing && monitorId) {
          const loaded = await getQualityEditorSnapshot(monitorId);
          if (version !== editorVersion.current) return;
          const { monitor, settings } = loaded;
          setDefinition(loaded.definition);
          form.setFieldsValue({
            name: monitor.name,
            description: monitor.description,
            dataSourceId: monitor.dataSourceId,
            dataSourceName: monitor.dataSourceName,
            databaseName: monitor.databaseName,
            schemaName: monitor.schemaName,
            tableName: monitor.tableName,
            whereClause: monitor.whereClause,
            owner: monitor.owner,
            enabled: monitor.enabled,
          });
          setRules(monitorRules(monitor));
          setSchedule(scheduleFromSettings(settings));
          setNotification(notificationFromSettings(settings));
          setNextRunTime(settings.nextRunTime);
        } else {
          form.setFieldsValue({
            dataSourceId: Number(query.get('dataSourceId')) || undefined,
            dataSourceName: query.get('dataSourceName') || undefined,
            databaseName: query.get('databaseName') || undefined,
            schemaName: query.get('schemaName') || undefined,
            tableName: query.get('tableName') || undefined,
            owner: currentUser?.realName || currentUser?.username || 'system',
            enabled: true,
          });
        }
      } catch (error) {
        if (version === editorVersion.current) message.error(errorMessage(error, '页面初始化失败'));
      } finally {
        if (version === editorVersion.current) setLoading(false);
      }
    };

    void initialize();
    return () => { if (version === editorVersion.current) editorVersion.current += 1; };
  }, [
    currentUser?.realName,
    currentUser?.username,
    editing,
    form,
    monitorId,
    query,
    setRules,
  ]);

  useEffect(() => {
    if (!dataSourceId || !tableName) {
      setColumns([]);
      return;
    }

    listDataSourceColumns(dataSourceId, databaseName, schemaName, tableName)
      .then(setColumns)
      .catch((error) => message.error(errorMessage(error, '字段加载失败')));
  }, [dataSourceId, databaseName, schemaName, tableName]);

  const adoptRule = async (rule: SaveRulePayload, expectedDefinition: string, isCurrent: () => boolean) => {
    if (loading || !monitorId || !definition || definition !== expectedDefinition) {
      throw new Error('配置已改变，请重新加载');
    }
    if (operation.current) throw new Error('正在校验或保存，请稍后再试');
    if (rulesRef.current.some((current) => sameRuleConditions(current, rule))) {
      throw new Error('当前表单已有相同条件的规则，请核对现有规则');
    }
    const request = { kind: 'adopt' as const };
    const version = editorVersion.current;
    operation.current = request;
    setAdopting(true);
    try {
      const valid = await validateQualitySuggestions(monitorId, expectedDefinition, [rule]);
      if (version !== editorVersion.current || !isCurrent()) return;
      if (valid.length !== 1) throw new Error('候选校验未返回唯一规则，请重新生成');
      const normalized = valid[0];
      const template = templates.find((value) => value.id === normalized.templateId);
      if (!template) throw new Error('模板已改变，请重新加载');
      // Recheck the latest form after the asynchronous source validation.
      if (rulesRef.current.some((current) => sameRuleConditions(current, normalized))) {
        throw new Error('当前表单已有相同条件的规则，请核对现有规则');
      }
      setRules([...rulesRef.current, { ...ruleDefaults(template), ...normalized, enabled: false }]);
    } finally {
      if (operation.current === request) {
        operation.current = null;
        if (version === editorVersion.current) setAdopting(false);
      }
    }
  };

  const save = async () => {
    if (loading) return;
    if (operation.current) {
      message.warning('正在校验或保存，请完成后再保存配置');
      return;
    }
    const request = { kind: 'save' as const };
    const version = editorVersion.current;
    operation.current = request;
    setSaving(true);
    const submittedRules = rulesRef.current.map((rule) => ({ ...rule, enumValues: [...(rule.enumValues || [])] }));
    try {
      const values = await form.validateFields();
      if (version !== editorVersion.current) return;
      if (!values.dataSourceId || !values.tableName) {
        throw new Error('监控对象无效，请从数据表监控页面重新创建');
      }

      validateEditorSettings(schedule, notification);
      validateRules(submittedRules);
      const source = dataSources.find(
        (item) => Number(item.id) === Number(values.dataSourceId),
      );
      const payload: SaveMonitorPayload = {
        ...values,
        expectedDefinition: editing ? definition : undefined,
        dataSourceId: Number(values.dataSourceId),
        dataSourceName: source?.name || values.dataSourceName,
        settings: buildSettings(schedule, notification),
        rules: submittedRules.map(
          ({
            key: _key,
            templateCode: _code,
            ruleType: _type,
            scope: _scope,
            dimension: _dimension,
            ...rule
          }) => rule,
        ),
      };

      const result =
        editing && monitorId
          ? await updateQualityMonitor(monitorId, payload)
          : await createQualityMonitor(payload);
      if (version !== editorVersion.current) return;
      message.success(editing ? '质量监控已更新' : '质量监控已创建');
      history.push(`/data-quality/monitor/${result.id}`);
    } catch (error) {
      if (version === editorVersion.current && !hasFormErrors(error)) {
        message.error(errorMessage(error, '保存失败'));
      }
    } finally {
      if (operation.current === request) {
        operation.current = null;
        if (version === editorVersion.current) setSaving(false);
      }
    }
  };

  return {
    editing,
    definition,
    form,
    columns,
    templates,
    rules,
    setRules,
    schedule,
    setSchedule,
    notification,
    setNotification,
    nextRunTime,
    loading,
    saving,
    adopting,
    adoptRule,
    dataSourceId,
    storedDataSourceName,
    databaseName,
    schemaName,
    tableName,
    selectedSource,
    save,
  };
};
