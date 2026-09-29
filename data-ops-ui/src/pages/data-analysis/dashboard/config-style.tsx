import type {
  AnalysisDataLabelPosition,
  AnalysisLegendPosition,
  AnalysisMetricAlign,
  AnalysisMetricValueSize,
  AnalysisSpec,
  AnalysisTableDensity,
  AnalysisVisualConfig,
} from '@/components/analysis/model';
import { ANALYSIS_PALETTES, resolveAnalysisStyle } from '@/components/analysis/style';
import { InputNumber, Select, Slider, Switch } from 'antd';

export function ChartStyleConfig({
  spec,
  onChange,
}: {
  spec: AnalysisSpec;
  onChange: (patch: Partial<AnalysisVisualConfig>) => void;
}) {
  const style = resolveAnalysisStyle(spec.style);
  const hasCartesianAxes = ['bar', 'stackedBar', 'line', 'area', 'scatter'].includes(spec.type);
  const hasCategoricalAxis = ['bar', 'stackedBar', 'line', 'area'].includes(spec.type);
  const supportsLegend = [
    'bar',
    'stackedBar',
    'line',
    'area',
    'pie',
    'scatter',
    'radar',
    'funnel',
  ].includes(spec.type);
  const supportsLabels = [
    'bar',
    'stackedBar',
    'line',
    'area',
    'pie',
    'scatter',
    'funnel',
    'treemap',
  ].includes(spec.type);
  const supportsPalette = !['metric', 'table'].includes(spec.type);
  const supportsLabelPosition = supportsLabels && spec.type !== 'treemap';
  const radialLabelPosition = spec.type === 'pie' || spec.type === 'funnel';
  const activeLabelPosition: AnalysisDataLabelPosition = radialLabelPosition
    ? style.dataLabelPosition === 'inside' ? 'inside' : 'outside'
    : style.dataLabelPosition === 'inside' ? 'inside' : 'top';

  return (
    <>
      <div className="chart-style-config space-y-5 pb-1 text-[12px] font-medium text-[#344054]">
        {supportsPalette ? (
          <StyleGroup title="配色">
            <div className="grid grid-cols-5 gap-1.5">
              {(Object.entries(ANALYSIS_PALETTES) as Array<
                [keyof typeof ANALYSIS_PALETTES, (typeof ANALYSIS_PALETTES)[keyof typeof ANALYSIS_PALETTES]]
              >).map(([key, palette]) => {
                const active = style.palette === key;
                return (
                  <button
                    key={key}
                    type="button"
                    title={palette.label}
                    onClick={() => onChange({ palette: key })}
                    className={[
                      'rounded-[7px] border px-1.5 py-2 transition-[background-color,border-color]',
                      active
                        ? 'border-[#bfc6d0] bg-[#f4f5f7]'
                        : 'border-[#e4e7ec] bg-white hover:border-[#cfd4dc] hover:bg-[#fafbfc]',
                    ].join(' ')}
                  >
                    <div className="flex h-3.5 overflow-hidden rounded-[3px]">
                      {palette.colors.slice(0, 4).map((color) => (
                        <span key={color} className="flex-1" style={{ backgroundColor: color }} />
                      ))}
                    </div>
                  </button>
                );
              })}
            </div>
          </StyleGroup>
        ) : null}

        {supportsLegend || supportsLabels ? (
          <StyleGroup title="展示">
            <div className="space-y-3.5">
              {supportsLegend ? (
                <>
                  <ToggleRow
                    label="显示图例"
                    checked={style.showLegend}
                    onChange={(showLegend) => onChange({ showLegend })}
                  />
                  {style.showLegend ? (
                    <ControlRow label="图例位置">
                      <Select
                        size="small"
                        className="chart-style-control w-[120px]"
                        value={style.legendPosition}
                        options={[
                          { label: '顶部', value: 'top' },
                          { label: '右侧', value: 'right' },
                          { label: '底部', value: 'bottom' },
                        ]}
                        onChange={(legendPosition: AnalysisLegendPosition) => onChange({ legendPosition })}
                      />
                    </ControlRow>
                  ) : null}
                </>
              ) : null}
              {supportsLabels ? (
                <>
                  <ToggleRow
                    label={spec.type === 'treemap' ? '显示数值' : '显示数据标签'}
                    checked={style.showDataLabels}
                    onChange={(showDataLabels) => onChange({ showDataLabels })}
                  />
                  {style.showDataLabels && supportsLabelPosition ? (
                    <ControlRow label="标签位置">
                      <Select
                        size="small"
                        className="chart-style-control w-[120px]"
                        value={activeLabelPosition}
                        options={radialLabelPosition
                          ? [
                            { label: '外侧', value: 'outside' },
                            { label: '内部', value: 'inside' },
                          ]
                          : [
                            { label: '顶部', value: 'top' },
                            { label: '内部', value: 'inside' },
                          ]}
                        onChange={(dataLabelPosition: AnalysisDataLabelPosition) => onChange({ dataLabelPosition })}
                      />
                    </ControlRow>
                  ) : null}
                </>
              ) : null}
            </div>
          </StyleGroup>
        ) : null}

        {hasCartesianAxes ? (
          <StyleGroup title="坐标轴">
            <div className="space-y-3.5">
              <ToggleRow
                label="显示网格线"
                checked={style.showGrid}
                onChange={(showGrid) => onChange({ showGrid })}
              />
              {hasCategoricalAxis ? (
                <ControlRow label="标签旋转">
                  <Select
                    size="small"
                    className="chart-style-control w-[120px]"
                    value={style.axisLabelRotation}
                    options={[
                      { label: '不旋转', value: 0 },
                      { label: '30°', value: 30 },
                      { label: '45°', value: 45 },
                    ]}
                    onChange={(axisLabelRotation: 0 | 30 | 45) => onChange({ axisLabelRotation })}
                  />
                </ControlRow>
              ) : null}
            </div>
          </StyleGroup>
        ) : null}

        {spec.type === 'bar' || spec.type === 'stackedBar' ? (
          <StyleGroup title={spec.type === 'stackedBar' ? '堆叠柱形' : '柱形'}>
            <div className="space-y-3.5">
              <NumberRow
                label="最大宽度"
                value={style.barMaxWidth}
                min={12}
                max={72}
                suffix="px"
                onChange={(barMaxWidth) => onChange({ barMaxWidth })}
              />
              <NumberRow
                label="圆角"
                value={style.barRadius}
                min={0}
                max={16}
                suffix="px"
                onChange={(barRadius) => onChange({ barRadius })}
              />
            </div>
          </StyleGroup>
        ) : null}

        {spec.type === 'line' || spec.type === 'area' ? (
          <StyleGroup title={spec.type === 'area' ? '面积线' : '折线'}>
            <div className="space-y-3.5">
              <ToggleRow
                label="平滑曲线"
                checked={style.smooth}
                onChange={(smooth) => onChange({ smooth })}
              />
              <NumberRow
                label="线宽"
                value={style.lineWidth}
                min={1}
                max={6}
                suffix="px"
                onChange={(lineWidth) => onChange({ lineWidth })}
              />
              <NumberRow
                label="数据点"
                value={style.symbolSize}
                min={0}
                max={14}
                suffix="px"
                onChange={(symbolSize) => onChange({ symbolSize })}
              />
            </div>
          </StyleGroup>
        ) : null}

        {spec.type === 'scatter' ? (
          <StyleGroup title="散点">
            <NumberRow
              label="点大小"
              value={style.symbolSize}
              min={0}
              max={14}
              suffix="px"
              onChange={(symbolSize) => onChange({ symbolSize })}
            />
          </StyleGroup>
        ) : null}

        {spec.type === 'pie' ? (
          <StyleGroup title="饼图">
            <div>
              <div className="mb-2 flex items-center justify-between text-[12px] text-[#344054]">
                <span>内径</span>
                <span className="tabular-nums font-normal text-[#667085]">{style.pieInnerRadius}%</span>
              </div>
              <Slider
                min={0}
                max={64}
                step={2}
                value={style.pieInnerRadius}
                onChange={(pieInnerRadius) => onChange({ pieInnerRadius })}
              />
            </div>
          </StyleGroup>
        ) : null}

        {spec.type === 'metric' ? (
          <StyleGroup title="指标卡">
            <div className="space-y-3.5">
              <ControlRow label="对齐方式">
                <Select
                  size="small"
                  className="chart-style-control w-[120px]"
                  value={style.metricAlign}
                  options={[
                    { label: '左对齐', value: 'left' },
                    { label: '居中', value: 'center' },
                    { label: '右对齐', value: 'right' },
                  ]}
                  onChange={(metricAlign: AnalysisMetricAlign) => onChange({ metricAlign })}
                />
              </ControlRow>
              <ControlRow label="数值字号">
                <Select
                  size="small"
                  className="chart-style-control w-[120px]"
                  value={style.metricValueSize}
                  options={[
                    { label: '紧凑', value: 'sm' },
                    { label: '标准', value: 'md' },
                    { label: '突出', value: 'lg' },
                  ]}
                  onChange={(metricValueSize: AnalysisMetricValueSize) => onChange({ metricValueSize })}
                />
              </ControlRow>
              <ToggleRow
                label="显示数据来源"
                checked={style.showMetricMeta}
                onChange={(showMetricMeta) => onChange({ showMetricMeta })}
              />
            </div>
          </StyleGroup>
        ) : null}

        {spec.type === 'table' ? (
          <StyleGroup title="表格">
            <div className="space-y-3.5">
              <ControlRow label="行高密度">
                <Select
                  size="small"
                  className="chart-style-control w-[120px]"
                  value={style.tableDensity}
                  options={[
                    { label: '紧凑', value: 'compact' },
                    { label: '标准', value: 'comfortable' },
                    { label: '宽松', value: 'relaxed' },
                  ]}
                  onChange={(tableDensity: AnalysisTableDensity) => onChange({ tableDensity })}
                />
              </ControlRow>
              <ToggleRow
                label="斑马纹"
                checked={style.stripedRows}
                onChange={(stripedRows) => onChange({ stripedRows })}
              />
            </div>
          </StyleGroup>
        ) : null}
      </div>

      <style>{`
        /* Phase 1 keeps interaction data compatibility, but does not expose interaction editing. */
        .chart-appearance-config-panel > div:first-child {
          height: 52px !important;
        }
        .chart-appearance-config-panel > div:first-child > div > div:first-child {
          font-size: 14px !important;
          font-weight: 600 !important;
          color: #161823 !important;
        }
        .chart-appearance-config-panel > div:first-child > div > div:last-child:not(:first-child) {
          display: none !important;
        }
        .chart-appearance-tabs .ant-tabs-nav {
          display: none !important;
        }
        .chart-appearance-tabs .ant-tabs-tabpane-active > div > div:first-child {
          display: none !important;
        }
        .chart-appearance-tabs .ant-tabs-tabpane-active > div {
          padding-top: 16px !important;
        }
        .chart-appearance-config-panel .ant-select-selection-item,
        .chart-appearance-config-panel .ant-select-selection-placeholder,
        .chart-appearance-config-panel .ant-input-number-input,
        .chart-appearance-config-panel .ant-input-number-group-addon {
          font-size: 12px !important;
          color: #344054 !important;
        }
        .chart-appearance-config-panel .ant-select-selector,
        .chart-appearance-config-panel .ant-input-number {
          min-height: 30px !important;
        }
        .chart-appearance-config-panel > div:last-child .ant-btn {
          font-size: 12px !important;
          color: #344054 !important;
        }
      `}</style>
    </>
  );
}

function StyleGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="border-b border-[#eceef1] pb-5 last:border-b-0 last:pb-0">
      <div className="mb-3 text-[12px] font-semibold text-[#161823]">{title}</div>
      {children}
    </div>
  );
}

function ToggleRow({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex min-h-8 items-center justify-between gap-3 text-[#344054]">
      <span>{label}</span>
      <Switch size="small" checked={checked} onChange={onChange} />
    </label>
  );
}

function ControlRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-8 items-center justify-between gap-3 text-[#344054]">
      <span>{label}</span>
      {children}
    </div>
  );
}

function NumberRow({
  label,
  value,
  min,
  max,
  suffix,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  suffix: string;
  onChange: (value: number) => void;
}) {
  return (
    <ControlRow label={label}>
      <InputNumber
        size="small"
        className="w-[120px]"
        min={min}
        max={max}
        value={value}
        addonAfter={suffix}
        onChange={(next) => {
          if (typeof next === 'number') onChange(next);
        }}
      />
    </ControlRow>
  );
}
