import { YakEmpty } from '@/components/ui';
import type { ReactNode } from 'react';

/** 元数据中心各页统一的顶部标题栏：标题 + 说明 + 右侧操作区。 */
export const PageHeader = ({
  title,
  subtitle,
  extra,
}: {
  title: string;
  subtitle?: string;
  extra?: ReactNode;
}) => (
  <header className="flex flex-wrap items-center justify-between gap-4 pb-1">
    <div className="min-w-0">
      <div className="mb-1 text-[11px] font-semibold tracking-[0.12em] text-[#8993a5]">元数据中心</div>
      <h1 className="m-0 text-[24px] font-semibold leading-9 tracking-[-0.025em] text-[#202939]">{title}</h1>
      {subtitle ? <p className="mb-0 mt-1 max-w-[760px] text-[13px] leading-5 text-[#677389]">{subtitle}</p> : null}
    </div>
    {extra ? <div className="flex flex-wrap items-center gap-2">{extra}</div> : null}
  </header>
);

/**
 * 接口未交付时的占位：写清"这一页将提供什么"，而不是只放一个空图标。
 *
 * 菜单与路由先行注册（ticket 111），采集/目录/搜索/概览各自的接口按工单顺序补齐，
 * 补齐后由本页替换为真实视图，路由与权限码不再变动。
 */
export const PendingPanel = ({ description }: { description: ReactNode }) => (
  <div className="mt-4 rounded-xl border border-solid border-[#eceef2] bg-white">
    {/* YakEmpty 的 description 限宽 240px，长句改用 children 以保持可读宽度。 */}
    <YakEmpty title="功能开发中">
      <div className="max-w-[520px] text-[12px] leading-5 text-[#98a2b3]">{description}</div>
    </YakEmpty>
  </div>
);
