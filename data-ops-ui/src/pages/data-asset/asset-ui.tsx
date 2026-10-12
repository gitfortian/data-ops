import type { ReactNode } from 'react';

/**
 * Asset workspace presentation only. Sources of truth remain the Asset,
 * Metadata and Modeling owners; these components must not infer business state.
 */
export const AssetPageHeader = ({
  title, description, extra, eyebrow = 'DATA ASSET · 数据资产',
}: { title: ReactNode; description?: ReactNode; extra?: ReactNode; eyebrow?: string }) => (
  <header className="flex flex-wrap items-start justify-between gap-4 pb-1">
    <div className="min-w-0 flex-1">
      <div className="text-[11px] font-semibold tracking-[0.12em] text-[#8994a5]">{eyebrow}</div>
      <h1 className="m-0 mt-1 text-[24px] font-semibold leading-9 tracking-[-0.025em] text-[#222f43]">{title}</h1>
      {description ? <p className="mb-0 mt-1 max-w-[800px] text-[13px] leading-6 text-[#718097]">{description}</p> : null}
    </div>
    {extra ? <div className="flex flex-wrap items-center justify-end gap-2">{extra}</div> : null}
  </header>
);

export const AssetPanel = ({
  title, description, action, children, className = '', bodyClassName = '',
}: {
  title?: ReactNode; description?: ReactNode; action?: ReactNode; children: ReactNode;
  className?: string; bodyClassName?: string;
}) => (
  <section className={'min-w-0 overflow-hidden rounded-2xl border border-solid border-[#e7ebf1] bg-white shadow-[0_3px_16px_rgba(23,36,57,0.025)] ' + className}>
    {title || description || action ? (
      <div className="flex flex-wrap items-start justify-between gap-3 border-0 border-b border-solid border-[#edf0f4] px-5 py-4">
        <div className="min-w-0">
          {title ? <h2 className="m-0 text-[15px] font-semibold leading-6 text-[#2a394e]">{title}</h2> : null}
          {description ? <p className="mb-0 mt-1 text-[12px] leading-5 text-[#8491a4]">{description}</p> : null}
        </div>
        {action}
      </div>
    ) : null}
    <div className={'min-w-0 ' + (bodyClassName || 'p-5')}>{children}</div>
  </section>
);

export const AssetMetric = ({
  label, value, helper, icon, emphasis = 'default',
}: {
  label: string; value: ReactNode; helper?: ReactNode; icon?: ReactNode;
  emphasis?: 'default' | 'warning' | 'success';
}) => {
  const color = emphasis === 'warning' ? 'text-[#aa6532]' : emphasis === 'success' ? 'text-[#28785d]' : 'text-[#233348]';
  const iconTone = emphasis === 'warning'
    ? 'bg-[#fff5e9] text-[#bd7a3a]'
    : emphasis === 'success' ? 'bg-[#eaf7f1] text-[#318369]' : 'bg-[#eef4fa] text-[#5c7fa8]';
  return (
    <section className="min-w-0 rounded-2xl border border-solid border-[#e7ebf1] bg-white px-5 py-4 shadow-[0_3px_16px_rgba(23,36,57,0.025)]">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[13px] font-medium text-[#728097]">{label}</span>
        {icon ? <span className={'flex h-9 w-9 items-center justify-center rounded-xl ' + iconTone}>{icon}</span> : null}
      </div>
      <div className={'mt-2 text-[30px] font-semibold leading-10 tracking-tight tabular-nums ' + color}>{value}</div>
      {helper ? <p className="mb-0 mt-1 min-h-5 text-[12px] leading-5 text-[#94a0b0]">{helper}</p> : null}
    </section>
  );
};

export const AssetWorkspace = ({ children }: { children: ReactNode }) => (
  <main className="min-h-[calc(100dvh-64px)] bg-[#f7f8fb] px-6 pb-8 pt-6 text-[#2a3546] max-md:px-4">
    {children}
  </main>
);
