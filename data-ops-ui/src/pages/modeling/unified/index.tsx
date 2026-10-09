import { history, useLocation, useParams } from '@umijs/max';
import { Descriptions, message, Space, Tabs, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { MODELING_DIALECT_LABELS, MODELING_PUBLISHED_EVENT, MODELING_STATUS_LABELS } from '@/pages/modeling/constants';
import ModelLineagePanel from '@/pages/modeling/components/ModelLineagePanel';
import ModelVersionPanel from '@/pages/modeling/components/ModelVersionPanel';
import ModelingModelDetail from '@/pages/modeling/detail';
import ModelLifecycleTab from '@/pages/data-lifecycle/components/ModelLifecycleTab';
import LayerFieldMappingPage from '@/pages/modeling/layer-mapping';
import MappingPanel from '@/pages/modeling/mapping';
import { listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { getModelingModel } from '@/services/modeling/api';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { listSemanticLayers } from '@/services/semantic/api';
import type { SemanticLayerRecord } from '@/services/semantic/types';

const COMING_SOON: Record<string, { title: string; ticket: string; note: string }> = {
  task: { title: '加工任务', ticket: '21/22', note: '来源映射生成 SQL 并接入数据开发后展示' },
};

/** 模型统一视图(ticket 25):一页看清全貌;各维度空态优雅降级、懒加载。 */
const UnifiedModelView: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const modelId = params.id;
  const location = useLocation();
  const queryTab = new URLSearchParams(location.search).get('tab');
  const tabKeys = ['overview', 'structure', 'mapping', 'layer-mapping', 'process', 'lineage', 'lifecycle', 'version', 'task'];
  const activeTab = queryTab && tabKeys.includes(queryTab) ? queryTab : 'structure';
  const [model, setModel] = useState<ModelingModelRecord>();
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [loadError, setLoadError] = useState(false);

  const reloadModel = useCallback(() => {
    if (!modelId) {
      return;
    }
    getModelingModel(modelId)
      .then((data) => setModel(data))
      .catch(() => {
        setLoadError(true);
        message.error('加载模型信息失败');
      });
  }, [modelId]);

  useEffect(() => {
    if (!modelId) {
      return;
    }
    reloadModel();
    // 概览"分层/目标库/数据源"经分层配置与数据源目录解析(2026-09-17),失败不阻断概览
    listSemanticLayers()
      .then((list) => setLayers(list ?? []))
      .catch(() => setLayers([]));
    listDataSources({ pageNo: 1, pageSize: 200 })
      .then((result) => setDatasources(result.bizData ?? []))
      .catch(() => setDatasources([]));
  }, [modelId, reloadModel]);

  // 发布联动(01):直发或审批回调发布后,头部状态 Tag 与基本信息 Tab 同步刷新
  useEffect(() => {
    const onPublished = (event: Event) => {
      const detail = (event as CustomEvent<{ modelId?: string | number }>).detail;
      if (detail?.modelId != null && String(detail.modelId) !== String(modelId)) return;
      reloadModel();
    };
    window.addEventListener(MODELING_PUBLISHED_EVENT, onPublished);
    return () => window.removeEventListener(MODELING_PUBLISHED_EVENT, onPublished);
  }, [modelId, reloadModel]);

  // 模型关联分层 → 目标库/数据源展示名(只读解析,不改后端)
  const layer = model?.layerCode ? layers.find((item) => item.code === model.layerCode) : undefined;
  const layerLabel = layer ? `${layer.name}（${layer.code}）` : (model?.layerCode ?? '未关联');
  const databaseName = layer?.databaseName ?? '-';
  const datasourceName = layer?.datasourceId
    ? (datasources.find((item) => item.id === layer.datasourceId)?.name ?? `#${layer.datasourceId}`)
    : '-';

  // 来源数据源信息(用于血缘展示)
  const sourceDatasourceName = model?.sourceDatasourceId
    ? (datasources.find((item) => item.id === model.sourceDatasourceId)?.name ?? `#${model.sourceDatasourceId}`)
    : null;
  const sourceFullTable = model?.sourceDatabase && model?.sourceTable
    ? `${model.sourceDatabase}.${model.sourceTable}`
    : model?.sourceTable ?? null;

  const overview = model ? (
    <div className="max-w-[880px]">
      <Descriptions
        bordered
        size="small"
        column={2}
        items={[
          { key: 'code', label: '模型编码', children: model.code },
          { key: 'name', label: '模型名称', children: model.name },
          { key: 'layer', label: '模型分层', children: layerLabel },
          {
            key: 'domain',
            label: '业务域',
            children: model.domainName || '未关联',
          },
          {
            key: 'process',
            label: '业务过程',
            children: model.processName || '未关联',
          },
          { key: 'database', label: '目标库', children: databaseName },
          { key: 'datasource', label: '数据源', children: datasourceName },
          {
            key: 'dialect',
            label: '方言',
            children: MODELING_DIALECT_LABELS[model.dialect || ''] || model.dialect || '-',
          },
          {
            key: 'status',
            label: '状态',
            children: MODELING_STATUS_LABELS[model.status || ''] || model.status || '-',
          },
          { key: 'description', label: '描述', children: model.description || '-', span: 2 },
          // 来源信息(其他)
          ...(sourceDatasourceName || sourceFullTable
            ? [
                { key: 'sourceHeader', label: '来源信息', children: '', span: 2 },
                { key: 'sourceDatasource', label: '来源数据源', children: sourceDatasourceName || '-' },
                { key: 'sourceTable', label: '来源表', children: sourceFullTable || '-' },
              ]
            : []),
        ]}
      />
    </div>
  ) : (
    <YakEmpty
      compact
      title={loadError ? '加载失败' : '加载中…'}
      description={loadError ? '请返回列表后重试' : undefined}
    />
  );

  const comingSoonTab = (key: string) => {
    const info = COMING_SOON[key];
    return <YakEmpty compact title={`${info.title}尚未开放`} description={`${info.note}（ticket ${info.ticket}）`} />;
  };

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">
            {model?.name ?? '模型详情'}
            {model?.code ? (
              <Typography.Text code className="!ml-2 !text-[13px]">
                {model.code}
              </Typography.Text>
            ) : null}
            {model?.status ? <Tag className="!ml-2">{MODELING_STATUS_LABELS[model.status] || model.status}</Tag> : null}
          </div>
          <div className="mt-1 text-[13px] text-[#667085]">
            统一视图：概览、表结构、来源映射、分层映射、血缘、版本与业务过程一页看清
          </div>
        </div>
        <Space>
          <YakButton className="!h-9 !rounded-lg !px-4" onClick={() => history.push('/modeling')}>
            返回列表
          </YakButton>
        </Space>
      </div>

      <Tabs
        className="mt-3"
        activeKey={activeTab}
        onChange={(tab) => {
          const query = new URLSearchParams(location.search); query.set('tab', tab);
          history.replace({ pathname: location.pathname, search: `?${query.toString()}` });
        }}
        items={[
          { key: 'overview', label: '基本信息', children: overview },
          { key: 'structure', label: '表结构', children: <ModelingModelDetail /> },
          { key: 'mapping', label: '来源映射', children: <MappingPanel /> },
          {
            key: 'layer-mapping',
            label: '分层映射',
            children: <LayerFieldMappingPage />,
          },
          {
            key: 'process',
            label: '业务过程',
            children: (
              <YakEmpty
                compact
                title="业务过程维度（M4 预留）"
                description="派生建模（ticket 44）与业务过程主线视图（ticket 47）开放后在此展示"
              />
            ),
          },
          { key: 'lineage', label: '血缘', children: <ModelLineagePanel /> },
          {
            key: 'lifecycle',
            label: '生命周期',
            children: <ModelLifecycleTab modelId={modelId ? Number(modelId) : null} />,
          },
          { key: 'version', label: '版本', children: <ModelVersionPanel /> },
          { key: 'task', label: '加工任务', children: comingSoonTab('task') },
        ]}
      />
    </div>
  );
};

export default UnifiedModelView;
