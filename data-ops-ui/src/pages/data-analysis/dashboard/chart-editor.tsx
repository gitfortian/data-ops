import YakTab from '@/components/YakTab';
import type { AnalysisSpec } from '@/components/analysis/model';
import { Button } from 'antd';
import { MousePointerClick, Palette } from 'lucide-react';
import { ChartStyleConfig } from './config-style';
import { DashboardDirectCrossFilterEditor } from './direct-link-editor';
import { findDataset } from './helpers';
import { DashboardInteractionEditor } from './interaction-editor';
import type {
  AnalysisAsset,
  DashboardGlobalFilter,
  DashboardInlineAnalysisSpec,
  DashboardInteraction,
  DashboardWidget,
  PublishedDataset,
} from './model';
import { DashboardWidgetActionEditor } from './widget-action-editor';

interface ChartAppearanceConfigPanelProps {
  currentDashboardId: string;
  widget: DashboardWidget;
  widgets: DashboardWidget[];
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  updateInlineAnalysis: (patch: Partial<DashboardInlineAnalysisSpec>) => void;
  updateInteractions: (interactions: DashboardInteraction[]) => void;
  onDone: () => void;
}

/** Right-side inspector for how a chart is rendered and how the rendered marks behave. */
export function ChartAppearanceConfigPanel({
  currentDashboardId,
  widget,
  widgets,
  datasets,
  analyses,
  globalFilters,
  interactions,
  updateInlineAnalysis,
  updateInteractions,
  onDone,
}: ChartAppearanceConfigPanelProps) {
  if (widget.analysisId) {
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    return (
      <section className="chart-appearance-config-panel flex w-[320px] shrink-0 flex-col border-l border-[#e3e6ea] bg-white 2xl:w-[336px]">
        <InspectorHeader />
        <div className="flex min-h-0 flex-1 items-center justify-center px-6 text-center">
          <div>
            <Palette size={18} className="mx-auto text-[#b0b5bd]" />
            <div className="mt-2 text-[11px] font-medium text-[#667085]">共享图表为只读状态</div>
            <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">
              {analysis ? '先在左侧复制为可编辑图表，再调整渲染样式与交互。' : '当前共享图表已不可用。'}
            </div>
          </div>
        </div>
        <DoneFooter onDone={onDone} />
      </section>
    );
  }

  const spec = widget.inlineAnalysis;
  if (!spec) return null;
  const dataset = findDataset(datasets, spec.datasetId);
  if (!dataset) return null;

  const updateStyle = (patch: Partial<AnalysisSpec['style']>) =>
    updateInlineAnalysis({ style: { ...spec.style, ...patch, version: 1 } });

  const styleContent = (
    <div className="px-4 pb-5 pt-1">
      <div className="mb-3 rounded-[7px] bg-[#f8f9fa] px-2.5 py-2 text-[9px] leading-4 text-[#98a2b3]">
        这里只控制当前图表的视觉表现；字段、图表类型与分析逻辑统一在左侧完成。
      </div>
      <ChartStyleConfig spec={spec} onChange={updateStyle} />
    </div>
  );

  const interactionContent = (
    <div className="space-y-4 px-4 pb-5 pt-1">
      <DashboardDirectCrossFilterEditor
        widget={widget}
        widgets={widgets}
        spec={spec}
        dataset={dataset}
        datasets={datasets}
        analyses={analyses}
        onChange={(dashboardBehavior) => updateInlineAnalysis({ dashboardBehavior })}
      />
      <div className="border-t border-[#eceef1] pt-4">
        <DashboardInteractionEditor
          widget={widget}
          spec={spec}
          dataset={dataset}
          filters={globalFilters}
          interactions={interactions}
          onChange={updateInteractions}
        />
      </div>
      <div className="border-t border-[#eceef1] pt-4">
        <DashboardWidgetActionEditor
          currentDashboardId={currentDashboardId}
          spec={spec}
          dataset={dataset}
          onChange={(dashboardBehavior) => updateInlineAnalysis({ dashboardBehavior })}
        />
      </div>
    </div>
  );

  return (
    <section className="chart-appearance-config-panel flex w-[320px] shrink-0 flex-col border-l border-[#e3e6ea] bg-white 2xl:w-[336px]">
      <InspectorHeader />
      <div className="min-h-0 flex-1 overflow-y-auto">
        <YakTab
          size="small"
          defaultActiveKey="style"
          className="chart-appearance-tabs"
          tabBarStyle={{ margin: 0, padding: '0 16px' }}
          items={[
            {
              key: 'style',
              label: (
                <span className="flex items-center gap-1.5 text-[11px]">
                  <Palette size={12} />
                  样式
                </span>
              ),
              children: styleContent,
            },
            {
              key: 'interaction',
              label: (
                <span className="flex items-center gap-1.5 text-[11px]">
                  <MousePointerClick size={12} />
                  交互
                </span>
              ),
              children: interactionContent,
            },
          ]}
        />
      </div>
      <DoneFooter onDone={onDone} />
    </section>
  );
}

function InspectorHeader() {
  return (
    <div className="flex h-14 shrink-0 items-center border-b border-[#eceef1] px-4">
      <div>
        <div className="text-[13px] font-semibold text-[#344054]">渲染设置</div>
        <div className="mt-0.5 text-[9px] text-[#98a2b3]">外观样式与图表交互</div>
      </div>
    </div>
  );
}

function DoneFooter({ onDone }: { onDone: () => void }) {
  return (
    <div className="shrink-0 border-t border-[#eceef1] bg-[#fbfcfd] p-3">
      <Button block size="small" className="!h-8 !rounded-[7px]" onClick={onDone}>
        完成
      </Button>
    </div>
  );
}
