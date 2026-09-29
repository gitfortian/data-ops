import { productFeatures } from '@/config/productFeatures';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { isSecurityRoot } from '@/utils/security/permission';
import {
  getLocale,
  history,
  setLocale,
  useModel,
} from '@umijs/max';
import { Dropdown, type MenuProps } from 'antd';
import {
  Check,
  ChevronDown,
  FolderKanban,
  Languages,
  Settings,
} from 'lucide-react';

type SupportedLocale = 'zh-CN' | 'en-US';

const getSupportedLocale = (): SupportedLocale =>
  getLocale().toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US';

function LanguageSwitcher() {
  const currentLocale = getSupportedLocale();

  const switchLocale = (locale: SupportedLocale) => {
    if (locale === getLocale()) return;

    document.documentElement.dataset.yakLocale = locale;
    document.documentElement.lang = locale;
    setLocale(locale);
  };

  return (
    <Dropdown
      placement="bottomRight"
      trigger={['click']}
      menu={{
        selectable: true,
        selectedKeys: [currentLocale],
        items: [
          {
            key: 'zh-CN',
            label: '中文',
            onClick: () => switchLocale('zh-CN'),
          },
          {
            key: 'en-US',
            label: 'English',
            onClick: () => switchLocale('en-US'),
          },
        ],
      }}
    >
      <button
        type="button"
        aria-label="切换语言 / Switch language"
        title="切换语言 / Switch language"
        className="order-[-1] flex h-12 min-w-11 flex-col items-center justify-center border-0 bg-transparent px-2 text-[12px] text-[rgba(35,35,35,0.6)] transition-colors duration-150 hover:text-[rgba(35,35,35,0.9)]"
      >
        <span className="flex h-6 w-6 items-center justify-center text-[17px]">
          <Languages className="h-[17px] w-[17px]" strokeWidth={1.8} />
        </span>
        <span className="mt-0.5 whitespace-nowrap text-[10px] leading-3">
          {currentLocale === 'zh-CN' ? '中文' : 'EN'}
        </span>
      </button>
    </Dropdown>
  );
}

function ProjectSwitcher() {
  const { initialState } = useModel('@@initialState');
  const { projects, currentProject, selectProject } = useSecurityProject();
  const permissionCodes = initialState?.currentUser?.permissionCodes ?? [];
  const canManage = isSecurityRoot(permissionCodes);

  const items: MenuProps['items'] = [
    ...projects.map((project) => ({
      key: `project:${project.id}`,
      label: (
        <div className="flex min-w-[188px] items-center justify-between gap-3">
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#1c1f23]">
              {project.projectName}
            </div>
            {project.projectCode ? (
              <div className="mt-0.5 truncate text-[11px] text-slate-400">
                {project.projectCode}
              </div>
            ) : null}
          </div>
          {currentProject?.id === project.id ? (
            <Check className="h-4 w-4 shrink-0 text-[#fe2c55]" />
          ) : null}
        </div>
      ),
    })),
    ...(canManage
      ? [
          { type: 'divider' as const },
          {
            key: 'manage',
            icon: <Settings className="h-4 w-4" strokeWidth={1.8} />,
            label: '管理工作空间',
          },
        ]
      : []),
  ];

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'manage') {
      history.push('/system/projects');
      return;
    }

    if (!key.startsWith('project:')) return;
    const id = Number(key.slice('project:'.length));
    const project = projects.find((item) => item.id === id);
    if (!project || project.id === currentProject?.id) return;

    selectProject(project);

    // Project-scoped pages own local query state. Reload after persisting the
    // selected project so every mounted module re-enters with one consistent
    // X-YAK-SECURITY-PROJECT-ID instead of showing mixed old/new workspace data.
    window.location.reload();
  };

  if (!projects.length && !canManage) {
    return (
      <div className="mx-1 flex h-8 items-center gap-2 rounded-lg px-2 text-[12px] text-slate-400">
        <FolderKanban className="h-4 w-4" strokeWidth={1.8} />
        <span>暂无工作空间</span>
      </div>
    );
  }

  return (
    <Dropdown
      placement="bottomRight"
      trigger={['click']}
      menu={{ items, onClick: handleMenuClick }}
    >
      <button
        type="button"
        aria-label="切换工作空间"
        title="切换工作空间"
        className="mx-1 flex h-8 max-w-[220px] items-center gap-2 rounded-lg border border-[rgba(28,31,35,0.08)] bg-white px-2.5 text-[13px] text-[#1c1f23] transition-colors hover:bg-[#f7f7f8]"
      >
        <FolderKanban
          className="h-4 w-4 shrink-0 text-[rgba(22,24,35,0.55)]"
          strokeWidth={1.8}
        />
        <span className="min-w-0 flex-1 truncate text-left">
          {currentProject?.projectName ?? '暂无工作空间'}
        </span>
        <ChevronDown className="h-3.5 w-3.5 shrink-0 text-[rgba(22,24,35,0.4)]" />
      </button>
    </Dropdown>
  );
}

export default function SecurityProjectSwitcher() {
  return (
    <div className="contents">
      <LanguageSwitcher />
      {productFeatures.projectSpace ? <ProjectSwitcher /> : null}
    </div>
  );
}
