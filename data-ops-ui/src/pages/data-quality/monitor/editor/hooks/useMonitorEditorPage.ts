import {
  listAllDataSources,
  listDataSourceColumns,
  type DataSourceRecord,
} from '@/services/data-source';
import {
  createQualityMonitor,
  getQualityMonitor,
  getQualityMonitorSettings,
  listQualityTemplates,
  updateQualityMonitor,
  type CatalogColumn,
  type SaveMonitorPayload,
  type TemplateView,
} from '@/services/data-quality';
import { history } from '@umijs/max';
import { Form, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import {
  buildSettings,
  DEFAULT_NOTIFICATION,
  DEFAULT_SCHEDULE,
  monitorRules,
  notificationFromSettings,
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
  const [rules, setRules] = useState<EditorRule[]>([]);
  const [schedule, setSchedule] =
    useState<ScheduleSettingsState>(DEFAULT_SCHEDULE);
  const [notification, setNotification] =
    useState<NotificationSettingsState>(DEFAULT_NOTIFICATION);
  const [nextRunTime, setNextRunTime] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

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
    const initialize = async () => {
      setLoading(true);
      try {
        const [sourcePage, templatePage] = await Promise.all([
          listAllDataSources(),
          listQualityTemplates(),
        ]);
        setDataSources(sourcePage.bizData || []);
        setTemplates(templatePage.records || []);

        if (editing && monitorId) {
          const [monitor, settings] = await Promise.all([
            getQualityMonitor(monitorId),
            getQualityMonitorSettings(monitorId),
          ]);
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
        message.error(errorMessage(error, '页面初始化失败'));
      } finally {
        setLoading(false);
      }
    };

    void initialize();
  }, [
    currentUser?.realName,
    currentUser?.username,
    editing,
    form,
    monitorId,
    query,
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

  const save = async () => {
    try {
      const values = await form.validateFields();
      if (!values.dataSourceId || !values.tableName) {
        throw new Error('监控对象无效，请从数据表监控页面重新创建');
      }

      validateEditorSettings(schedule, notification);
      validateRules(rules);
      const source = dataSources.find(
        (item) => Number(item.id) === Number(values.dataSourceId),
      );
      const payload: SaveMonitorPayload = {
        ...values,
        dataSourceId: Number(values.dataSourceId),
        dataSourceName: source?.name || values.dataSourceName,
        settings: buildSettings(schedule, notification),
        rules: rules.map(
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

      setSaving(true);
      const result =
        editing && monitorId
          ? await updateQualityMonitor(monitorId, payload)
          : await createQualityMonitor(payload);
      message.success(editing ? '质量监控已更新' : '质量监控已创建');
      history.push(`/data-quality/monitor/${result.id}`);
    } catch (error) {
      if (!hasFormErrors(error)) {
        message.error(errorMessage(error, '保存失败'));
      }
    } finally {
      setSaving(false);
    }
  };

  return {
    editing,
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
    dataSourceId,
    storedDataSourceName,
    databaseName,
    schemaName,
    tableName,
    selectedSource,
    save,
  };
};
