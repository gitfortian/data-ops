import { useAgentAvailability } from '@/contexts/AgentAvailabilityContext';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { history, useModel } from '@umijs/max';
import {
  ArrowRight, AlertTriangle, BarChart3, Bot, Database,
  Layers3, LockKeyhole, PackageCheck, ShieldCheck, Workflow,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { resolveHomeJourneyEntries, type HomeJourneyEntry, type HomeJourneyKey } from './homeJourneyEntries';

const JOURNEY_ICONS: Record<HomeJourneyKey, ReactNode> = {
  build: <Database size={19} strokeWidth={1.8} />,
  consume: <PackageCheck size={19} strokeWidth={1.8} />,
  recover: <AlertTriangle size={19} strokeWidth={1.8} />,
  metric: <BarChart3 size={18} strokeWidth={1.8} />,
  security: <ShieldCheck size={18} strokeWidth={1.8} />,
  semantic: <Layers3 size={18} strokeWidth={1.8} />,
  mdm: <Workflow size={18} strokeWidth={1.8} />,
  agent: <Bot size={18} strokeWidth={1.8} />,
};

const cardStyles: Record<'build' | 'consume' | 'recover', string> = {
  build: 'border-[#cce6e1] bg-[#f2faf8] hover:border-[#77b9a8]',
  consume: 'border-[#d8e5f6] bg-[#f5f9fe] hover:border-[#94b4da]',
  recover: 'border-[#f0e0d4] bg-[#fffaf5] hover:border-[#e0b18c]',
};

function JourneyButton({ entry }: { entry: HomeJourneyEntry }) {
  return (
    <button
      type="button"
      data-home-journey={entry.key}
      onClick={() => history.push(entry.path)}
      aria-label={`${entry.title}：${entry.action}`}
      className={`group flex min-w-0 flex-col rounded-2xl border p-4 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#16877d] sm:p-5 ${entry.primary ? cardStyles[entry.key as 'build' | 'consume' | 'recover'] : 'border-[#e5eaec] bg-white hover:border-[#bfd4d1]'}`}
    >
      <div className="flex w-full items-center justify-between gap-2">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl border border-[#dee8e7] bg-white/80 text-[#315b59]">
          {JOURNEY_ICONS[entry.key]}
        </span>
      </div>
      <strong className="mt-3 text-[15px] font-semibold leading-6 text-[#213b43]">
        {entry.title}
      </strong>
      <span className="mt-1 min-h-[42px] text-[12px] leading-[21px] text-[#647984]">
        {entry.description}
      </span>
      <span className="mt-4 inline-flex items-center gap-1.5 text-[12px] font-semibold text-[#13796e]">
        {entry.action} <ArrowRight size={14} aria-hidden="true" className="transition-transform group-hover:translate-x-0.5" />
      </span>
    </button>
  );
}

/**
 * Project-scoped task starting points. This is navigation/presentation only:
 * no new workflow state, no automatic model/TaskRevision creation, and no
 * attempt to treat missing Provider metrics as zero.
 */
export default function HomeTaskStarter() {
  const { currentProject } = useSecurityProject();
  const { initialState } = useModel('@@initialState');
  const { agentEnabled } = useAgentAvailability();
  const user = initialState?.currentUser;
  const entries = user && currentProject
    ? resolveHomeJourneyEntries(user.permissionCodes, user.menuCodes, { agentEnabled })
    : [];
  const primary = entries.filter((entry) => entry.primary);
  const secondary = entries.filter((entry) => !entry.primary);

  return (
    <section className="min-w-0 rounded-[22px] border border-[#e1e9e9] bg-white px-4 py-5 shadow-sm sm:px-6" aria-labelledby="home-task-title">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="mb-1 text-[11px] font-semibold tracking-[0.08em] text-[#188177]">当前项目 · {currentProject?.projectName ?? '尚未选择'}</p>
          <h2 id="home-task-title" className="m-0 text-[21px] font-semibold tracking-[-0.5px] text-[#223941]">
            开始工作
          </h2>
        </div>
      </header>

      {!currentProject ? (
        <div role="status" className="mt-5 flex items-start gap-2 rounded-xl border border-[#f0e4cf] bg-[#fffbf3] px-4 py-3 text-[12px] text-[#805f3a]">
          <LockKeyhole size={17} className="shrink-0" />
          暂无可用项目空间，请切换项目或联系管理员申请加入。
        </div>
      ) : primary.length ? (
        <div className="mt-5 grid grid-cols-1 gap-3 md:grid-cols-3">
          {primary.map((entry) => <JourneyButton key={entry.key} entry={entry} />)}
        </div>
      ) : (
        <div role="status" className="mt-5 rounded-xl border border-dashed border-[#d5e2e0] p-4 text-[12px] text-[#687c86]">
          {secondary.length > 0
            ? '可展开下方更多工作入口，选择已授权功能。'
            : '暂无可用的工作入口，可从侧栏查看已授权功能。'}
        </div>
      )}

      {currentProject && secondary.length > 0 ? (
        <details className="mt-4 rounded-xl border border-[#e8eeef] bg-[#fbfcfc]">
          <summary className="cursor-pointer px-4 py-3 text-[12px] font-semibold text-[#44646b] focus-visible:outline-2 focus-visible:outline-offset-2">
            更多工作入口（{secondary.length}）
          </summary>
          <div className="grid grid-cols-1 gap-3 px-3 pb-3 sm:grid-cols-2 xl:grid-cols-4">
            {secondary.map((entry) => <JourneyButton key={entry.key} entry={entry} />)}
          </div>
        </details>
      ) : null}
    </section>
  );
}
