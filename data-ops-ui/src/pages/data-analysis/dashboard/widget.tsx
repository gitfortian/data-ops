import {
  AnalysisPreview,
  buildDatasetQueryPayload,
  canQueryAnalysis,
} from '@/components/analysis/AnalysisPreview';
import { analysisQueryCacheKey } from '@/components/analysis/query-runtime';
import { Empty, Tooltip } from 'antd';
import {
  ChevronRight,
  Copy,
  GripVertical,
  Pencil,
  RotateCcw,
  Trash2,
  X,
} from 'lucide-react';
import { useEffect, useMemo } from 'react';
import type {
  AnalysisAsset,
  AnalysisSelection,
  AnalysisThemeTokens,
  DashboardDrillStep,
  DashboardFilter,
  DashboardInlineAnalysisSpec,
  DashboardWidget,
  PublishedDataset,
} from './model';
import { registerDashboardPerformanceQuery } from './performance-runtime';

export function WidgetShell({
  widget,
  analysis,
  runtimeSpec,
  dataset,
  runtimeFilters,
  analysisTheme,
  drillPath,
  activeSelection,
  selected,
  preview,
  onSelect,
  onEdit,
  onDataSelect,
  onClearSelection,
  onDrillBack,
  onDuplicate,
  onDelete,
}: {
  widget: DashboardWidget;
  analysis?: AnalysisAsset;
  runtimeSpec?: DashboardInlineAnalysisSpec | AnalysisAsset;
  dataset?: PublishedDataset;
  runtimeFilters: DashboardFilter[];
  analysisTheme?: AnalysisThemeTokens;
  drillPath: DashboardDrillStep[];
  activeSelection?: AnalysisSelection;
  selected: boolean;
  preview: boolean;
  onSelect: () => void;
  onEdit: () => void;
  onDataSelect: (selection: AnalysisSelection) => void;
  onClearSelection: () => void;
  onDrillBack: (depth: number) => void;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  const spec = runtimeSpec ?? (widget.analysisId ? analysis : widget.inlineAnalysis);
  const title = widget.analysisId
    ? analysis?.name ?? '历史图表'
    : widget.title ?? '未命名图表';
  const performanceQueryKey = useMemo(() => {
    if (!spec || !dataset || !canQueryAnalysis(spec)) return undefined;
    return analysisQueryCacheKey(
      dataset,
      buildDatasetQueryPayload(spec, dataset, runtimeFilters),
    );
  }, [dataset, runtimeFilters, spec]);

  useEffect(() => {
    if (!dataset || !performanceQueryKey) return;
    registerDashboardPerformanceQuery({
      widgetId: widget.id,
      widgetName: title,
      datasetId: dataset.id,
      queryKey: performanceQueryKey,
    });
  }, [dataset, performanceQueryKey, title, widget.id]);

  return (
    <div
      className={[
        'group dashboard-widget relative h-full min-h-0 overflow-visible',
        preview ? 'dashboard-widget--preview' : 'dashboard-widget--editable',
        !preview && selected ? 'dashboard-widget--selected' : '',
        preview && activeSelection ? 'dashboard-widget--active' : '',
      ].join(' ')}
    >
      <div
        onMouseDown={onSelect}
        className="dashboard-widget__surface relative flex h-full min-h-0 flex-col overflow-hidden"
        style={{ backgroundColor: 'var(--dashboard-component-bg)' }}
      >
        <div className="dashboard-widget__drag-handle flex h-10 shrink-0 cursor-move items-center px-3.5">
          {!preview ? (
            <GripVertical
              size={13}
              className={[
                'mr-1.5 transition-opacity duration-150',
                selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100',
              ].join(' ')}
              style={{ color: 'var(--dashboard-component-muted)' }}
            />
          ) : null}
          <span
            className="min-w-0 flex-1 truncate text-[12px] font-semibold"
            style={{ color: 'var(--dashboard-component-text)' }}
          >
            {title}
          </span>

          {preview && activeSelection ? (
            <button
              type="button"
              title={activeSelection.label}
              className="ml-2 flex max-w-[190px] shrink-0 items-center gap-1 rounded-[5px] border border-[var(--yak-brand-color-border)] bg-[var(--yak-brand-color-soft)] px-1.5 py-1 text-[9px]"
              style={{ color: 'var(--dashboard-component-text)' }}
              onMouseDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onClearSelection();
              }}
            >
              <span className="truncate">已选 · {String(activeSelection.value)}</span>
              <X size={9} className="shrink-0" />
            </button>
          ) : null}
        </div>

        {drillPath.length ? (
          <div
            className="mx-3 flex h-7 shrink-0 items-center gap-0.5 rounded-[6px] px-2 text-[9px]"
            style={{
              backgroundColor: 'var(--dashboard-component-subtle-bg)',
              color: 'var(--dashboard-component-muted)',
            }}
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              className="flex h-5 items-center gap-1 rounded-[4px] border-0 bg-transparent px-1"
              style={{ color: 'var(--dashboard-component-muted)' }}
              onClick={(event) => {
                event.stopPropagation();
                onDrillBack(0);
              }}
            >
              <RotateCcw size={9} />
              全部
            </button>
            {drillPath.map((step, index) => (
              <span key={`${step.field}-${index}`} className="flex min-w-0 items-center gap-0.5">
                <ChevronRight size={9} className="shrink-0 opacity-60" />
                <button
                  type="button"
                  className="max-w-[120px] truncate rounded-[4px] border-0 bg-transparent px-1 py-0.5"
                  style={{ color: 'var(--dashboard-component-text)' }}
                  title={step.label}
                  onClick={(event) => {
                    event.stopPropagation();
                    onDrillBack(index + 1);
                  }}
                >
                  {step.label}
                </button>
              </span>
            ))}
          </div>
        ) : null}

        <div
          className="min-h-0 flex-1 overflow-hidden px-1 pb-1"
          style={{ backgroundColor: 'var(--dashboard-component-bg)' }}
        >
          {spec ? (
            <AnalysisPreview
              spec={spec}
              dataset={dataset}
              runtimeFilters={runtimeFilters}
              theme={analysisTheme}
              onSelect={onDataSelect}
            />
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="图表数据来源已失效"
              className="mt-8"
            />
          )}
        </div>
      </div>

      {!preview && selected ? (
        <div
          className="absolute left-full top-0 z-30 ml-3 flex w-8 flex-col overflow-hidden rounded-[7px] border border-[#e4e7ec] bg-white shadow-[0_6px_18px_rgba(16,24,40,.12)]"
          onMouseDown={(event) => event.stopPropagation()}
        >
          <Tooltip title="编辑图表" placement="right">
            <button
              type="button"
              aria-label="编辑图表"
              onMouseDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onEdit();
              }}
              className="flex h-8 w-8 items-center justify-center border-0 bg-transparent text-[#667085] transition-colors hover:bg-[var(--yak-brand-color-soft)] hover:text-[var(--yak-brand-color)]"
            >
              <Pencil size={14} />
            </button>
          </Tooltip>

          <div className="h-px bg-[#eef0f3]" />

          <Tooltip title="复制组件" placement="right">
            <button
              type="button"
              aria-label="复制组件"
              onMouseDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onDuplicate();
              }}
              className="flex h-8 w-8 items-center justify-center border-0 bg-transparent text-[#667085] transition-colors hover:bg-[#f5f6f7] hover:text-[#344054]"
            >
              <Copy size={14} />
            </button>
          </Tooltip>

          <div className="h-px bg-[#eef0f3]" />

          <Tooltip title="删除组件" placement="right">
            <button
              type="button"
              aria-label="删除组件"
              onMouseDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onDelete();
              }}
              className="flex h-8 w-8 items-center justify-center border-0 bg-transparent text-[#98a2b3] transition-colors hover:bg-[rgba(254,44,85,.06)] hover:text-[var(--yak-brand-color)]"
            >
              <Trash2 size={14} />
            </button>
          </Tooltip>
        </div>
      ) : null}

      <style>{`
        .dashboard-widget__surface {
          outline: 1px solid transparent;
          outline-offset: 0;
          transition: outline-color 120ms ease, background-color 160ms ease;
        }
        .dashboard-widget--editable:not(.dashboard-widget--selected):hover .dashboard-widget__surface {
          outline-color: #9aa7b8;
          outline-style: dashed;
        }
        .dashboard-widget--selected .dashboard-widget__surface {
          outline-color: var(--yak-brand-color);
          outline-style: solid;
        }
        .dashboard-widget--preview.dashboard-widget--active .dashboard-widget__surface {
          outline-color: var(--yak-brand-color);
          outline-style: solid;
        }
        .react-grid-item:has(> .dashboard-widget--selected) {
          z-index: 20;
        }
        .dashboard-grid-canvas .react-grid-item.react-grid-placeholder {
          border-radius: 0 !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle {
          width: 18px !important;
          height: 18px !important;
          background-image: none !important;
          opacity: 0;
          transition: opacity 120ms ease;
          z-index: 25;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-n {
          top: -9px !important;
          left: 50% !important;
          margin-left: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-s {
          bottom: -9px !important;
          left: 50% !important;
          margin-left: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-e {
          right: -9px !important;
          top: 50% !important;
          margin-top: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-w {
          left: -9px !important;
          top: 50% !important;
          margin-top: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-ne {
          right: -9px !important;
          top: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-nw {
          left: -9px !important;
          top: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-se {
          right: -9px !important;
          bottom: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle-sw {
          left: -9px !important;
          bottom: -9px !important;
        }
        .dashboard-grid-canvas .react-grid-item:has(> .dashboard-widget--selected) > .react-resizable-handle {
          opacity: 1;
        }
        .dashboard-grid-canvas .react-grid-item > .react-resizable-handle::after {
          content: '' !important;
          position: absolute !important;
          left: 6px !important;
          top: 6px !important;
          width: 6px !important;
          height: 6px !important;
          border: 0 !important;
          border-radius: 9999px !important;
          background: var(--yak-brand-color) !important;
          transform: none !important;
        }
      `}</style>
    </div>
  );
}
