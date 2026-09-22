import type { ScreenComponent } from '../model';
import { ScreenNetworkMap, ScreenTicker } from '../PremiumVisuals';
import { ScreenChart, type ScreenChartComponent } from './chart-renderer';
import { alpha, ScreenComponentFrame } from './frame';
import {
  defineScreenComponentRenderer,
  type ScreenComponentRendererDefinition,
  type ScreenComponentInteraction,
  type TypedScreenComponentRendererProps,
} from './renderer-registry';

const formatValue = (value: string | number) => {
  if (typeof value !== 'number') return value;
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: Number.isInteger(value) ? 0 : 2,
  }).format(value);
};

function MetricRenderer({
  component,
  theme,
  ...interaction
}: TypedScreenComponentRendererProps<'metric'>) {
  const data = component.data;
  const direction = data?.trendDirection ?? 'flat';
  const trendColor = direction === 'up'
    ? '#5cf2b5'
    : direction === 'down'
      ? '#ff668f'
      : theme.mutedTextColor;
  const trendPrefix = direction === 'up' ? '↑' : direction === 'down' ? '↓' : '→';
  const accentColor = component.style?.accentColor ?? theme.primaryColor;

  return (
    <ScreenComponentFrame component={component} theme={theme} {...interaction}>
      <div className="flex h-full min-h-0 flex-col justify-center">
        <div className="flex items-end gap-2">
          <span
            className="text-[36px] font-semibold leading-none tracking-[-0.04em]"
            style={{
              color: component.style?.valueColor ?? theme.textColor,
              textShadow: component.style?.glow
                ? `0 0 16px ${alpha(accentColor, '66')}`
                : undefined,
            }}
          >
            {formatValue(data?.value ?? '--')}
          </span>
          {data?.unit ? (
            <span className="pb-1 text-[12px]" style={{ color: theme.mutedTextColor }}>
              {data.unit}
            </span>
          ) : null}
        </div>
        {typeof data?.trend === 'number' || data?.trendLabel ? (
          <div className="mt-3 flex items-center gap-2 text-[11px]">
            {typeof data?.trend === 'number' ? (
              <span className="font-medium" style={{ color: trendColor }}>
                {trendPrefix} {Math.abs(data.trend)}%
              </span>
            ) : null}
            {data?.trendLabel ? (
              <span style={{ color: theme.mutedTextColor }}>{data.trendLabel}</span>
            ) : null}
          </div>
        ) : null}
      </div>
    </ScreenComponentFrame>
  );
}

function ChartRenderer({
  component,
  theme,
  ...interaction
}: {
  component: ScreenChartComponent;
  theme: TypedScreenComponentRendererProps<'line'>['theme'];
} & ScreenComponentInteraction) {
  return (
    <ScreenComponentFrame component={component} theme={theme} {...interaction}>
      <ScreenChart component={component} theme={theme} />
    </ScreenComponentFrame>
  );
}

function TableRenderer({
  component,
  theme,
  ...interaction
}: TypedScreenComponentRendererProps<'table'>) {
  const data = component.data;
  return (
    <ScreenComponentFrame component={component} theme={theme} {...interaction}>
      {!data ? (
        <div
          className="flex h-full items-center justify-center text-[13px]"
          style={{ color: theme.mutedTextColor }}
        >
          暂无预览数据
        </div>
      ) : (
        <div className="h-full overflow-hidden">
          <table className="w-full table-fixed border-collapse text-[12px]">
            <thead>
              <tr style={{ color: theme.mutedTextColor }}>
                {data.columns.map((column) => (
                  <th
                    key={column.key}
                    className="border-b px-3 py-2.5 font-medium"
                    style={{
                      width: column.width,
                      textAlign: column.align ?? 'left',
                      borderColor: theme.panelBorderColor,
                    }}
                  >
                    {column.title}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {data.columns.map((column) => (
                    <td
                      key={column.key}
                      className="truncate border-b px-3 py-3"
                      style={{
                        textAlign: column.align ?? 'left',
                        borderColor: alpha(theme.panelBorderColor, '80'),
                        color: theme.textColor,
                      }}
                    >
                      {String(row[column.key] ?? '')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ScreenComponentFrame>
  );
}

function MapRenderer({
  component,
  theme,
  ...interaction
}: TypedScreenComponentRendererProps<'map'>) {
  return (
    <ScreenComponentFrame component={component} theme={theme} {...interaction}>
      <ScreenNetworkMap component={component} theme={theme} />
    </ScreenComponentFrame>
  );
}

function TickerRenderer({
  component,
  theme,
  ...interaction
}: TypedScreenComponentRendererProps<'ticker'>) {
  return (
    <ScreenComponentFrame component={component} theme={theme} {...interaction}>
      <ScreenTicker component={component} theme={theme} />
    </ScreenComponentFrame>
  );
}

function TextRenderer({
  component,
  theme,
  ...interaction
}: TypedScreenComponentRendererProps<'text'>) {
  return (
    <ScreenComponentFrame component={component} theme={theme} {...interaction}>
      <div
        className="flex h-full items-center"
        style={{
          color: component.style?.color ?? theme.textColor,
          fontSize: component.style?.fontSize ?? 16,
          fontWeight: component.style?.fontWeight,
          letterSpacing: component.style?.letterSpacing,
          justifyContent: component.style?.textAlign === 'center'
            ? 'center'
            : component.style?.textAlign === 'right'
              ? 'flex-end'
              : 'flex-start',
          textAlign: component.style?.textAlign,
          whiteSpace: 'pre-wrap',
          textShadow: component.style?.glow
            ? `0 0 18px ${alpha(component.style?.accentColor ?? theme.primaryColor, '66')}`
            : undefined,
        }}
      >
        {component.data?.content ?? ''}
      </div>
    </ScreenComponentFrame>
  );
}

export const builtinScreenComponentRenderers: ScreenComponentRendererDefinition[] = [
  defineScreenComponentRenderer('metric', MetricRenderer),
  defineScreenComponentRenderer('line', (props) => <ChartRenderer {...props} />),
  defineScreenComponentRenderer('bar', (props) => <ChartRenderer {...props} />),
  defineScreenComponentRenderer('pie', (props) => <ChartRenderer {...props} />),
  defineScreenComponentRenderer('table', TableRenderer),
  defineScreenComponentRenderer('map', MapRenderer),
  defineScreenComponentRenderer('ticker', TickerRenderer),
  defineScreenComponentRenderer('text', TextRenderer),
];

export const supportedScreenRendererTypes: ScreenComponent['type'][] =
  builtinScreenComponentRenderers.map((definition) => definition.type);
