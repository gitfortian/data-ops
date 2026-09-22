import type { ScreenMapComponent, ScreenMapPoint, ScreenTheme, ScreenTickerComponent } from './model';

export const SCREEN_MOTION_CSS = `
@keyframes yak-screen-scan {
  0% { transform: translateY(-18%); opacity: 0; }
  15% { opacity: .65; }
  82% { opacity: .28; }
  100% { transform: translateY(118%); opacity: 0; }
}
@keyframes yak-screen-pulse {
  0%, 100% { opacity: .45; transform: scale(.96); }
  50% { opacity: 1; transform: scale(1.035); }
}
@keyframes yak-screen-marquee {
  from { transform: translate3d(0, 0, 0); }
  to { transform: translate3d(-50%, 0, 0); }
}
`;

const TONE_COLORS = {
  primary: '#46d9ff',
  success: '#5cf2b5',
  warning: '#ffc866',
  danger: '#ff668f',
} as const;

const toneColor = (tone: ScreenMapPoint['tone'] | undefined, primary: string) => (
  tone ? TONE_COLORS[tone] : primary
);

const routePath = (from: ScreenMapPoint, to: ScreenMapPoint) => {
  const midpointX = (from.x + to.x) / 2;
  const midpointY = (from.y + to.y) / 2;
  const span = Math.sqrt(((to.x - from.x) ** 2) + ((to.y - from.y) ** 2));
  const lift = Math.max(5, Math.min(16, span * 0.22));
  return `M ${from.x} ${from.y} Q ${midpointX} ${midpointY - lift} ${to.x} ${to.y}`;
};

export function ScreenNetworkMap({
  component,
  theme,
}: {
  component: ScreenMapComponent;
  theme: ScreenTheme;
}) {
  const data = component.data;
  if (!data) {
    return (
      <div className="flex h-full items-center justify-center text-[13px]" style={{ color: theme.mutedTextColor }}>
        暂无地图预览数据
      </div>
    );
  }

  const pointMap = new Map(data.points.map((point) => [point.id, point]));
  const prefix = `yak-map-${component.id.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
  const center = pointMap.get(data.centerId ?? '') ?? data.points[0];
  const outline = data.outline.map(([x, y]) => `${x},${y}`).join(' ');

  return (
    <div className="relative h-full w-full overflow-hidden">
      <div
        className="pointer-events-none absolute inset-0 opacity-70"
        style={{
          background: `radial-gradient(circle at 50% 52%, ${theme.primaryColor}20 0%, transparent 46%), linear-gradient(180deg, rgba(10,35,55,.1), rgba(3,12,24,.18))`,
        }}
      />
      {component.options?.scan !== false ? (
        <div
          className="pointer-events-none absolute left-[5%] right-[5%] top-0 h-[16%]"
          style={{
            animation: 'yak-screen-scan 5.8s linear infinite',
            background: `linear-gradient(180deg, transparent 0%, ${theme.primaryColor}0d 42%, ${theme.primaryColor}42 86%, transparent 100%)`,
            boxShadow: `0 14px 32px ${theme.primaryColor}12`,
          }}
        />
      ) : null}

      <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full" preserveAspectRatio="xMidYMid meet">
        <defs>
          <pattern id={`${prefix}-grid`} width="5" height="5" patternUnits="userSpaceOnUse">
            <path d="M 5 0 L 0 0 0 5" fill="none" stroke={theme.primaryColor} strokeOpacity="0.055" strokeWidth="0.18" />
          </pattern>
          <linearGradient id={`${prefix}-fill`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={theme.primaryColor} stopOpacity="0.21" />
            <stop offset="58%" stopColor={theme.primaryColor} stopOpacity="0.075" />
            <stop offset="100%" stopColor="#6d7dff" stopOpacity="0.15" />
          </linearGradient>
          <linearGradient id={`${prefix}-route`} x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor={theme.primaryColor} stopOpacity="0.08" />
            <stop offset="52%" stopColor={theme.primaryColor} stopOpacity="0.9" />
            <stop offset="100%" stopColor="#86f7ff" stopOpacity="0.12" />
          </linearGradient>
          <filter id={`${prefix}-glow`} x="-80%" y="-80%" width="260%" height="260%">
            <feGaussianBlur stdDeviation="1.2" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <clipPath id={`${prefix}-clip`}>
            <polygon points={outline} />
          </clipPath>
        </defs>

        <g clipPath={`url(#${prefix}-clip)`}>
          <rect x="0" y="0" width="100" height="100" fill={`url(#${prefix}-grid)`} />
          <polygon points={outline} fill={`url(#${prefix}-fill)`} />
          {Array.from({ length: 8 }, (_, index) => (
            <line
              key={index}
              x1="5"
              y1={18 + (index * 9)}
              x2="95"
              y2={13 + (index * 9)}
              stroke={theme.primaryColor}
              strokeOpacity="0.035"
              strokeWidth="0.18"
            />
          ))}
        </g>

        <polygon
          points={outline}
          fill="none"
          stroke={theme.primaryColor}
          strokeOpacity="0.72"
          strokeWidth="0.62"
          vectorEffect="non-scaling-stroke"
          filter={`url(#${prefix}-glow)`}
        />
        <polygon
          points={outline}
          fill="none"
          stroke="#a8f4ff"
          strokeOpacity="0.14"
          strokeWidth="1.8"
          vectorEffect="non-scaling-stroke"
        />

        {center && component.options?.pulse !== false ? (
          <g>
            {[7, 12, 18].map((radius, index) => (
              <circle
                key={radius}
                cx={center.x}
                cy={center.y}
                r={radius}
                fill="none"
                stroke={theme.primaryColor}
                strokeOpacity={0.15 - (index * 0.03)}
                strokeWidth="0.34"
              >
                <animate attributeName="r" values={`${radius * 0.72};${radius}`} dur={`${2.6 + index * 0.45}s`} repeatCount="indefinite" />
                <animate attributeName="opacity" values="0.52;0" dur={`${2.6 + index * 0.45}s`} repeatCount="indefinite" />
              </circle>
            ))}
          </g>
        ) : null}

        {component.options?.showRoutes === false ? null : data.routes.map((route, index) => {
          const from = pointMap.get(route.from);
          const to = pointMap.get(route.to);
          if (!from || !to) return null;
          const path = routePath(from, to);
          const duration = 2.2 + ((index % 4) * 0.38);
          return (
            <g key={`${route.from}-${route.to}-${index}`}>
              <path
                d={path}
                fill="none"
                stroke={`url(#${prefix}-route)`}
                strokeOpacity={0.46 + Math.min(0.38, (route.intensity ?? 0.5) * 0.26)}
                strokeWidth={0.42 + Math.min(0.38, (route.intensity ?? 0.5) * 0.18)}
                strokeDasharray="2.1 2.5"
                vectorEffect="non-scaling-stroke"
              >
                <animate attributeName="stroke-dashoffset" from="9.2" to="0" dur={`${duration}s`} repeatCount="indefinite" />
              </path>
              <circle r="0.72" fill="#d7fbff" filter={`url(#${prefix}-glow)`}>
                <animateMotion path={path} dur={`${duration + 0.7}s`} repeatCount="indefinite" />
              </circle>
            </g>
          );
        })}

        {data.points.map((point, index) => {
          const color = toneColor(point.tone, theme.primaryColor);
          const isCenter = point.id === (data.centerId ?? data.points[0]?.id);
          return (
            <g key={point.id} filter={`url(#${prefix}-glow)`}>
              <circle cx={point.x} cy={point.y} r={isCenter ? 1.48 : 1.02} fill={color} />
              <circle cx={point.x} cy={point.y} r={isCenter ? 2.2 : 1.65} fill="none" stroke={color} strokeOpacity="0.64" strokeWidth="0.32">
                <animate attributeName="r" values={isCenter ? '2.2;5.8' : '1.7;4.1'} dur={`${2.05 + ((index % 3) * 0.3)}s`} repeatCount="indefinite" />
                <animate attributeName="opacity" values="0.78;0" dur={`${2.05 + ((index % 3) * 0.3)}s`} repeatCount="indefinite" />
              </circle>
              {component.options?.showLabels === false ? null : (
                <text
                  x={point.x + 1.8}
                  y={point.y - 1.7}
                  fill={isCenter ? '#effdff' : '#9dc8da'}
                  fontSize={isCenter ? 2.45 : 2.08}
                  fontWeight={isCenter ? 700 : 500}
                  dominantBaseline="middle"
                >
                  {point.name}
                </text>
              )}
            </g>
          );
        })}
      </svg>

      <div className="pointer-events-none absolute bottom-3 left-4 flex items-center gap-4 text-[10px] tracking-[0.12em]" style={{ color: theme.mutedTextColor }}>
        <span className="flex items-center gap-1.5"><i className="h-1.5 w-1.5 rounded-full bg-[#46d9ff] shadow-[0_0_8px_#46d9ff]" /> ACTIVE NODE</span>
        <span className="flex items-center gap-1.5"><i className="h-px w-5 bg-[#46d9ff]/60" /> DATA FLOW</span>
      </div>
    </div>
  );
}

export function ScreenTicker({
  component,
  theme,
}: {
  component: ScreenTickerComponent;
  theme: ScreenTheme;
}) {
  const items = component.data?.items ?? [];
  if (!items.length) return null;
  const speed = Math.max(12, component.options?.speed ?? 28);
  const repeated = [...items, ...items];

  return (
    <div className="relative flex h-full items-center overflow-hidden">
      <div
        className="pointer-events-none absolute inset-y-0 left-0 z-10 w-24"
        style={{ background: `linear-gradient(90deg, ${component.style?.background ?? theme.panelBackground}, transparent)` }}
      />
      <div
        className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24"
        style={{ background: `linear-gradient(270deg, ${component.style?.background ?? theme.panelBackground}, transparent)` }}
      />
      <div
        className="flex w-max min-w-max items-center"
        style={{ animation: `yak-screen-marquee ${speed}s linear infinite` }}
      >
        {repeated.map((item, index) => {
          const color = item.tone ? TONE_COLORS[item.tone] : theme.primaryColor;
          return (
            <div
              key={`${item.label}-${index}`}
              className="mx-2 flex h-[58px] min-w-[210px] items-center gap-3 rounded-[8px] border px-4"
              style={{
                borderColor: `${color}24`,
                background: `linear-gradient(90deg, ${color}10, rgba(4,17,31,.16))`,
              }}
            >
              <span className="relative flex h-2.5 w-2.5 shrink-0 items-center justify-center">
                <span className="absolute h-2.5 w-2.5 rounded-full opacity-20" style={{ background: color, animation: 'yak-screen-pulse 1.8s ease-in-out infinite' }} />
                <span className="relative h-1.5 w-1.5 rounded-full" style={{ background: color, boxShadow: `0 0 10px ${color}` }} />
              </span>
              <div className="min-w-0">
                <div className="truncate text-[11px] tracking-[0.06em]" style={{ color: theme.mutedTextColor }}>{item.label}</div>
                <div className="mt-1 flex items-baseline gap-2">
                  <span className="text-[17px] font-semibold" style={{ color }}>{item.value}</span>
                  {item.meta ? <span className="text-[10px]" style={{ color: theme.mutedTextColor }}>{item.meta}</span> : null}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
