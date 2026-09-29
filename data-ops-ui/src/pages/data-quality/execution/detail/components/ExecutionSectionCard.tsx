import type { ReactNode } from 'react';

export const ExecutionSectionCard = ({
  title,
  extra,
  children,
  className = '',
}: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section className={`min-w-0 rounded-lg bg-white ${className}`}>
    <div className="flex min-h-[52px] items-center justify-between gap-4 px-5">
      <div className="text-[15px] font-semibold text-[#161823]">{title}</div>
      {extra}
    </div>
    {children}
  </section>
);

export const ExecutionMetricTile = ({
  label,
  value,
  hint,
  valueClassName = '',
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  valueClassName?: string;
}) => (
  <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
    <div className="text-[12px] leading-4 text-[#7c828c]">{label}</div>
    <div
      className={`mt-2 text-[20px] font-semibold leading-7 tracking-[-0.02em] text-[#161823] ${valueClassName}`}
    >
      {value}
    </div>
    {hint ? (
      <div className="mt-1 text-[11px] leading-4 text-[#9aa0aa]">{hint}</div>
    ) : null}
  </div>
);

export const ExecutionInfoItem = ({
  label,
  value,
}: {
  label: string;
  value: ReactNode;
}) => (
  <div className="min-w-0">
    <div className="text-[12px] leading-4 text-[#8a8f98]">{label}</div>
    <div className="mt-2 truncate text-[14px] font-medium leading-5 text-[#161823]">
      {value || '--'}
    </div>
  </div>
);
