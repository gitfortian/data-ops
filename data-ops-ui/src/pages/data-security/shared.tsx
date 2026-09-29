import type { ReactNode } from 'react';

/** 数据安全各页统一的顶部标题栏：标题 + 说明 + 右侧操作区。 */
export const PageHeader = ({
  title,
  subtitle,
  extra,
}: {
  title: string;
  subtitle?: string;
  extra?: ReactNode;
}) => (
  <div className="flex flex-wrap items-center justify-between gap-3">
    <div>
      <div className="text-[20px] font-semibold leading-7">{title}</div>
      {subtitle ? <div className="mt-1 text-[13px] text-[#667085]">{subtitle}</div> : null}
    </div>
    {extra ? <div className="flex items-center gap-2">{extra}</div> : null}
  </div>
);

/** 简洁统计卡片：标签 + 数值 + 说明/跳转。 */
export const StatCard = ({
  label,
  value,
  hint,
  accent = '#2f6bff',
  onClick,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  accent?: string;
  onClick?: () => void;
}) => (
  <div
    onClick={onClick}
    className={`rounded-xl border border-solid border-[#eceef2] bg-white px-4 py-3.5 ${
      onClick ? 'cursor-pointer transition hover:border-[var(--yak-brand-color)] hover:shadow-sm' : ''
    }`}
  >
    <div className="text-[13px] text-[#667085]">{label}</div>
    <div className="mt-1.5 text-[26px] font-semibold leading-8" style={{ color: accent }}>
      {value}
    </div>
    {hint ? <div className="mt-1 text-[12px] text-[#98a2b3]">{hint}</div> : null}
  </div>
);

export const LEVEL_RANK_COLORS = [
  '#52c41a',
  '#1677ff',
  '#faad14',
  '#fa8c16',
  '#f5222d',
];

/** 按序位取色（0 基）。 */
export const rankColor = (rank?: number) =>
  LEVEL_RANK_COLORS[Math.max(0, (rank ?? 1) - 1) % LEVEL_RANK_COLORS.length];
