import type { ReactNode } from 'react';

export const DETAIL_TABLE_CLASS = [
  '[&_.ant-table-container]:!overflow-hidden',
  '[&_.ant-table-container]:!rounded-md',
  '[&_.ant-table-container]:!border',
  '[&_.ant-table-container]:!border-solid',
  '[&_.ant-table-container]:!border-[#eceef1]',
  '[&_.ant-table-thead>tr>th]:!h-10',
  '[&_.ant-table-thead>tr>th]:!bg-[#f7f7f8]',
  '[&_.ant-table-thead>tr>th]:!px-4',
  '[&_.ant-table-thead>tr>th]:!py-0',
  '[&_.ant-table-thead>tr>th]:!text-[12px]',
  '[&_.ant-table-thead>tr>th]:!font-medium',
  '[&_.ant-table-tbody>tr>td]:!px-4',
  '[&_.ant-table-tbody>tr>td]:!py-3',
  '[&_.ant-table-tbody>tr>td]:!text-[12px]',
].join(' ');

export function MetricTile({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
}) {
  return (
    <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
      <div className="text-[12px] leading-4 text-[#7c828c]">{label}</div>
      <div className="mt-2 text-[20px] font-semibold leading-7 tracking-[-0.02em] text-[#161823]">
        {value}
      </div>
      {hint ? (
        <div className="mt-1 text-[11px] text-[#9aa0aa]">{hint}</div>
      ) : null}
    </div>
  );
}

export function SectionCard({
  title,
  extra,
  children,
  className = '',
}: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`min-w-0 rounded-lg bg-white ${className}`}>
      <div className="flex min-h-[52px] items-center justify-between gap-4 px-5">
        <div className="text-[15px] font-semibold text-[#161823]">
          {title}
        </div>
        {extra}
      </div>
      {children}
    </section>
  );
}

export function DetailItem({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="min-w-0">
      <div className="text-[12px] leading-4 text-[#8a8f98]">{label}</div>
      <div className="mt-2 break-words text-[13px] leading-5 text-[#30343b]">
        {children}
      </div>
    </div>
  );
}
