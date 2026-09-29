import { Bell, Braces, Cpu, LayoutTemplate, SlidersHorizontal } from 'lucide-react';
import { history, useLocation } from '@umijs/max';

import AlertSettingsPanel from './components/AlertSettingsPanel';
import ComputeEngineSettingsPanel from './components/ComputeEngineSettingsPanel';
import EditorSettingsPanel from './components/EditorSettingsPanel';
import EnvironmentSettingsPanel from './components/EnvironmentSettingsPanel';
import ScreenTemplateSettingsPanel from './components/ScreenTemplateSettingsPanel';

type SettingsTab = 'editor' | 'environment' | 'compute' | 'screen-template' | 'alert';

const tabs: { key: SettingsTab; label: string; icon: React.ReactNode }[] = [
  { key: 'editor', label: '编辑器设置', icon: <SlidersHorizontal size={15} strokeWidth={1.8} /> },
  { key: 'environment', label: '环境变量', icon: <Braces size={15} strokeWidth={1.8} /> },
  { key: 'compute', label: '计算引擎', icon: <Cpu size={15} strokeWidth={1.8} /> },
  { key: 'screen-template', label: '大屏模板', icon: <LayoutTemplate size={15} strokeWidth={1.8} /> },
  { key: 'alert', label: '告警设置', icon: <Bell size={15} strokeWidth={1.8} /> },
];

const SettingsPage = () => {
  const location = useLocation();

  const hash = location.hash.replace('#', '');
  const activeTab: SettingsTab = (tabs.some((t) => t.key === hash) ? hash : 'editor') as SettingsTab;

  const switchTab = (key: SettingsTab) => {
    history.replace(`${location.pathname}${location.search}#${key}`);
  };

  return (
    <div className="flex min-h-[calc(100vh-80px)] bg-white">
      <aside className="w-[196px] shrink-0 border-r border-[#eaecf0] bg-[#fafafa] px-3 py-5">
        <div className="px-3 pb-3 text-[12px] font-medium text-[#98a2b3]">设置</div>
        <div className="space-y-1">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                aria-current={isActive ? 'page' : undefined}
                onClick={() => switchTab(tab.key)}
                className={[
                  'flex h-10 w-full items-center gap-2.5 rounded-md border-0 px-3 text-left text-[13px] transition-colors',
                  isActive
                    ? 'bg-[#f0f1f3] font-semibold text-[#161823]'
                    : 'bg-transparent font-medium text-[rgba(22,24,35,0.55)] hover:bg-[#f5f5f6] hover:text-[#161823]',
                ].join(' ')}
              >
                {tab.icon}
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </aside>

      <section className="min-w-0 flex-1 overflow-auto">
        <div
          className={[
            'mx-auto w-full px-10 py-8 xl:px-14',
            activeTab === 'screen-template'
              ? 'max-w-[1220px]'
              : activeTab === 'compute'
                ? 'max-w-[1080px]'
                : 'max-w-[920px]',
          ].join(' ')}
        >
          {activeTab === 'editor' && <EditorSettingsPanel />}
          {activeTab === 'environment' && <EnvironmentSettingsPanel />}
          {activeTab === 'compute' && <ComputeEngineSettingsPanel />}
          {activeTab === 'screen-template' && <ScreenTemplateSettingsPanel />}
          {activeTab === 'alert' && <AlertSettingsPanel />}
        </div>
      </section>
    </div>
  );
};

export default SettingsPage;
