import type { ReactNode } from 'react';
import { Button } from 'antd';
import { ArrowRightOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';

export const ASSET_SURFACE = 'min-h-[calc(100dvh-64px)] bg-[#f7f8fb] px-6 pb-8 pt-6 text-[#253145] max-md:px-4';
export const ASSET_PANEL = 'rounded-2xl border border-solid border-[#e6eaf0] bg-white shadow-[0_4px_20px_rgba(15,23,42,0.025)]';

export const AssetWorkspaceHeading = ({
  title, description, actions, eyebrow = '资产中心',
}: {
  title: ReactNode;
  description: ReactNode;
  actions?: ReactNode;
  eyebrow?: string;
}) => (
  <header className="flex flex-wrap items-start justify-between gap-4 pb-1">
    <div className="min-w-0">
      <div className="mb-1 text-[11px] font-semibold tracking-[0.12em] text-[#8993a5]">{eyebrow}</div>
      <h1 className="m-0 text-[24px] font-semibold leading-9 tracking-[-0.025em] text-[#202939]">{title}</h1>
      <p className="mb-0 mt-1 max-w-[760px] text-[13px] leading-5 text-[#677389]">{description}</p>
    </div>
    {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
  </header>
);

export const AssetWorkspacePanel = ({
  title, hint, action, children, className = '',
}: {
  title: ReactNode;
  hint?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section className={ASSET_PANEL + ' min-w-0 p-5 ' + className}>
    <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0">
        <h2 className="m-0 text-[16px] font-semibold leading-6 text-[#253145]">{title}</h2>
        {hint ? <p className="mb-0 mt-1 text-[12px] leading-5 text-[#8190a4]">{hint}</p> : null}
      </div>
      {action}
    </div>
    {children}
  </section>
);

export const AssetWorkspaceLink = ({ path, children }: { path: string; children: ReactNode }) => (
  <Button type="link" size="small" onClick={() => history.push(path)}>
    {children} <ArrowRightOutlined />
  </Button>
);
