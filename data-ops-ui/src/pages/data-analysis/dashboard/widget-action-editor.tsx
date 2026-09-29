import { appRoutes } from '@/config/navigation';
import { Input, Select } from 'antd';
import {
  ArrowRight,
  GitBranch,
  LayoutDashboard,
  MousePointerClick,
  Workflow,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { fetchDashboards } from './dashboard-service';
import type {
  DashboardInlineAnalysisSpec,
  DashboardSummary,
  DashboardWidgetBehavior,
  DashboardWidgetClickAction,
  PublishedDataset,
} from './model';

const YAK_TARGET_IDS = new Set([
  'data-source',
  'batch-link-up',
  'data-development-execution',
  'workflow-definition',
  'workflow-schedules',
  'workflow-instances',
  'data-quality-table-config',
  'data-quality-execution',
]);

const ACTION_OPTIONS: Array<{
  label: string;
  value: DashboardWidgetClickAction;
}> = [
  { label: '无额外操作', value: 'none' },
  { label: '下钻', value: 'drill' },
  { label: '跳转仪表盘', value: 'dashboard' },
  { label: '跳转 Yak 页面', value: 'yak' },
];

const hasBehavior = (behavior: DashboardWidgetBehavior) => Boolean(
  behavior.crossFilters?.length
  || (behavior.clickAction && behavior.clickAction !== 'none'),
);

export function DashboardWidgetActionEditor({
  currentDashboardId,
  spec,
  dataset,
  onChange,
}: {
  currentDashboardId: string;
  spec: DashboardInlineAnalysisSpec;
  dataset: PublishedDataset;
  onChange: (behavior: DashboardWidgetBehavior | undefined) => void;
}) {
  const behavior = spec.dashboardBehavior || {};
  const action = behavior.clickAction || 'none';
  const [dashboards, setDashboards] = useState<DashboardSummary[]>([]);

  useEffect(() => {
    let active = true;
    void fetchDashboards()
      .then((items) => {
        if (active) setDashboards(items);
      })
      .catch(() => {
        if (active) setDashboards([]);
      });
    return () => {
      active = false;
    };
  }, []);

  const dimensionOptions = dataset.fields
    .filter((field) => field.role === 'dimension')
    .map((field) => ({ label: `${field.label} · ${field.dataType}`, value: field.key }));
  const firstDimension = spec.dimensions[0];
  const configuredDrillFields = behavior.drillFields || [];
  const drillFields = firstDimension
    ? [firstDimension, ...configuredDrillFields.filter((field) => field !== firstDimension)]
    : configuredDrillFields;
  const dashboardOptions = dashboards
    .filter((item) => item.id !== currentDashboardId && item.publishedVersionNo > 0)
    .map((item) => ({
      label: `${item.name} · 已发布 V${item.publishedVersionNo}`,
      value: item.id,
    }));
  const yakOptions = useMemo(() => appRoutes
    .filter((route) => YAK_TARGET_IDS.has(route.id))
    .map((route) => ({ label: route.title, value: route.path })), []);

  const emit = (next: DashboardWidgetBehavior) => {
    onChange(hasBehavior(next) ? next : undefined);
  };

  const patch = (next: Partial<DashboardWidgetBehavior>) => {
    emit({ ...behavior, ...next });
  };

  const changeAction = (clickAction: DashboardWidgetClickAction) => {
    if (clickAction === 'none') {
      emit({
        ...behavior,
        clickAction: undefined,
        drillFields: undefined,
        targetDashboardId: undefined,
        targetPath: undefined,
        queryParam: undefined,
      });
      return;
    }
    if (clickAction === 'drill') {
      patch({
        clickAction,
        drillFields: drillFields.length ? drillFields : firstDimension ? [firstDimension] : [],
        targetDashboardId: undefined,
        targetPath: undefined,
        queryParam: undefined,
      });
      return;
    }
    if (clickAction === 'dashboard') {
      patch({
        clickAction,
        drillFields: undefined,
        targetPath: undefined,
        queryParam: undefined,
        targetDashboardId: behavior.targetDashboardId || dashboardOptions[0]?.value,
      });
      return;
    }
    patch({
      clickAction,
      drillFields: undefined,
      targetDashboardId: undefined,
      targetPath: behavior.targetPath || yakOptions[0]?.value,
    });
  };

  return (
    <div>
      <div className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
        <MousePointerClick size={12} />
        点击行为
      </div>
      <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">
        这是图表的主要点击动作；直接图表联动和筛选器联动仍可同时执行。
      </div>

      <Select
        size="small"
        className="mt-2 w-full"
        value={action}
        options={ACTION_OPTIONS}
        onChange={changeAction}
      />

      {action === 'drill' ? (
        <div className="mt-2 rounded-[6px] border border-[#edf0f3] bg-[#fafbfc] p-2.5">
          <div className="flex items-center gap-1.5 text-[10px] font-medium text-[#475467]">
            <GitBranch size={11} />
            下钻层级
          </div>
          <Select
            mode="multiple"
            size="small"
            className="mt-2 w-full"
            placeholder="选择下钻字段"
            value={drillFields}
            options={dimensionOptions}
            maxTagCount="responsive"
            onChange={(fields) => {
              const next = firstDimension
                ? [firstDimension, ...fields.filter((field) => field !== firstDimension)]
                : fields;
              patch({ drillFields: next });
            }}
          />
          <div className="mt-2 flex items-start gap-1.5 text-[9px] leading-4 text-[#98a2b3]">
            <ArrowRight size={10} className="mt-[3px] shrink-0" />
            第一层固定为当前图表主维度，后续按选择顺序进入下一层。至少配置 2 个维度才会触发下钻。
          </div>
        </div>
      ) : null}

      {action === 'dashboard' ? (
        <div className="mt-2 rounded-[6px] border border-[#edf0f3] bg-[#fafbfc] p-2.5">
          <div className="flex items-center gap-1.5 text-[10px] font-medium text-[#475467]">
            <LayoutDashboard size={11} />
            目标仪表盘
          </div>
          <Select
            size="small"
            showSearch
            optionFilterProp="label"
            className="mt-2 w-full"
            placeholder="选择已发布仪表盘"
            value={behavior.targetDashboardId}
            options={dashboardOptions}
            onChange={(targetDashboardId) => patch({ targetDashboardId })}
          />
          <div className="mt-2 text-[9px] leading-4 text-[#98a2b3]">
            跳转时会携带当前点击字段和值；目标仪表盘中绑定同名字段的全局筛选器会自动接收该值。
          </div>
        </div>
      ) : null}

      {action === 'yak' ? (
        <div className="mt-2 rounded-[6px] border border-[#edf0f3] bg-[#fafbfc] p-2.5">
          <div className="flex items-center gap-1.5 text-[10px] font-medium text-[#475467]">
            <Workflow size={11} />
            Yak 内部页面
          </div>
          <Select
            size="small"
            showSearch
            optionFilterProp="label"
            className="mt-2 w-full"
            placeholder="选择页面"
            value={behavior.targetPath}
            options={yakOptions}
            onChange={(targetPath) => patch({ targetPath })}
          />
          <div className="mt-2 text-[9px] text-[#667085]">查询参数名</div>
          <Input
            size="small"
            allowClear
            className="mt-1"
            value={behavior.queryParam || ''}
            placeholder="留空时使用当前字段名"
            onChange={(event) => patch({ queryParam: event.target.value || undefined })}
          />
          <div className="mt-2 text-[9px] leading-4 text-[#98a2b3]">
            例如点击 FAILED 后跳转工作流实例，可将参数名配置为 status，最终进入 /workflow/instances?status=FAILED。
          </div>
        </div>
      ) : null}
    </div>
  );
}
