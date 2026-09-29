import type { DevelopmentSqlDialect } from '@/services/data-development';
import { useIntl } from '@umijs/max';
import { Popover } from 'antd';
import {
  ChevronRight,
  Code2,
  Database,
  FolderPlus,
  Network,
  TerminalSquare,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';

import JavaIcon from '@/components/data-development/icons/JavaIcon';
import PythonIcon from '@/components/data-development/icons/PythonIcon';
import { DATA_DEVELOPMENT_SQL_DATABASE_PROFILES } from '../sqlDatabaseProfiles';
import type { DevelopmentNodeType } from '../types';

interface DevelopmentCreateMenuProps {
  children: ReactNode;
  onCreateDirectory: () => void;
  onCreateNode: (
    type: DevelopmentNodeType,
    sqlDialect?: DevelopmentSqlDialect,
  ) => void;
}

type CreateMenuLevel = 'node' | 'database' | 'general';

const PANEL_WIDTH = 168;
const PANEL_GAP = 0;

const MenuPanel = ({ children, className = '' }: { children: ReactNode; className?: string }) => (
  <div
    className={`rounded-[7px] border border-[#e1e4e8] bg-white p-1 shadow-[0_8px_28px_rgba(16,24,40,0.12)] ${className}`}
    style={{ width: PANEL_WIDTH }}
  >
    {children}
  </div>
);

const MenuItem = ({
  label,
  icon,
  arrow = false,
  active = false,
  onMouseEnter,
  onClick,
}: {
  label: ReactNode;
  icon?: ReactNode;
  arrow?: boolean;
  active?: boolean;
  onMouseEnter?: () => void;
  onClick?: () => void;
}) => (
  <button
    type="button"
    onMouseEnter={onMouseEnter}
    onClick={onClick}
    className={[
      'flex h-8 w-full items-center gap-2 rounded-[4px] px-2 text-left text-[12px] text-[#30323b] outline-none transition-colors',
      active ? 'bg-[#f2f3f5]' : 'hover:bg-[#f5f5f6]',
    ].join(' ')}
  >
    <span className="flex h-4 w-4 shrink-0 items-center justify-center text-[#667085]">
      {icon}
    </span>
    <span className="min-w-0 flex-1 truncate">{label}</span>
    {arrow ? <ChevronRight size={13} strokeWidth={1.8} className="shrink-0 text-[#98a2b3]" /> : null}
  </button>
);

const DevelopmentCreateMenu = ({
  children,
  onCreateDirectory,
  onCreateNode,
}: DevelopmentCreateMenuProps) => {
  const intl = useIntl();
  const [open, setOpen] = useState(false);
  const [level, setLevel] = useState<CreateMenuLevel>();
  const chinese = intl.locale.toLowerCase().startsWith('zh');
  const databaseLabel = chinese ? '数据库' : 'Database';
  const generalLabel = chinese ? '通用' : 'General';

  const closeAnd = (action: () => void) => {
    action();
    setOpen(false);
    setLevel(undefined);
  };

  const content = (
    <div className="relative">
      <MenuPanel>
        <MenuItem
          label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.createNode' })}
          icon={<Code2 size={14} strokeWidth={1.8} />}
          arrow
          active={Boolean(level)}
          onMouseEnter={() => setLevel('node')}
        />
        <MenuItem
          label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.createDirectory' })}
          icon={<FolderPlus size={14} strokeWidth={1.8} />}
          onMouseEnter={() => setLevel(undefined)}
          onClick={() => closeAnd(onCreateDirectory)}
        />
      </MenuPanel>

      {level ? (
        <div
          className="absolute top-0"
          style={{ left: PANEL_WIDTH + PANEL_GAP }}
        >
          <MenuPanel>
            <MenuItem
              label={databaseLabel}
              icon={<Database size={14} strokeWidth={1.8} />}
              arrow
              active={level === 'database'}
              onMouseEnter={() => setLevel('database')}
            />
            <MenuItem
              label={generalLabel}
              icon={<Code2 size={14} strokeWidth={1.8} />}
              arrow
              active={level === 'general'}
              onMouseEnter={() => setLevel('general')}
            />
            <div className="my-1 h-px bg-[#eef0f2]" />
            <MenuItem
              label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.datasetNode' })}
              icon={<Database size={14} strokeWidth={1.8} />}
              onClick={() => closeAnd(() => onCreateNode('DATASET'))}
            />
            <MenuItem
              label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.dataServiceNode' })}
              icon={<Network size={14} strokeWidth={1.8} />}
              onClick={() => closeAnd(() => onCreateNode('DATA_SERVICE'))}
            />
          </MenuPanel>
        </div>
      ) : null}

      {level === 'database' ? (
        <div
          className="absolute top-0"
          style={{ left: (PANEL_WIDTH + PANEL_GAP) * 2 }}
        >
          <MenuPanel className="max-h-[440px] overflow-y-auto">
            {DATA_DEVELOPMENT_SQL_DATABASE_PROFILES.map((profile) => (
              <MenuItem
                key={profile.dialect}
                label={profile.label}
                icon={<Database size={13} strokeWidth={1.7} />}
                onClick={() =>
                  closeAnd(() => onCreateNode('SQL', profile.dialect))
                }
              />
            ))}
          </MenuPanel>
        </div>
      ) : null}

      {level === 'general' ? (
        <div
          className="absolute top-0"
          style={{ left: (PANEL_WIDTH + PANEL_GAP) * 2 }}
        >
          <MenuPanel>
            <MenuItem
              label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.shellNode' })}
              icon={<TerminalSquare size={14} strokeWidth={1.8} />}
              onClick={() => closeAnd(() => onCreateNode('SHELL'))}
            />
            <MenuItem
              label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.pythonNode' })}
              icon={<PythonIcon size={14} />}
              onClick={() => closeAnd(() => onCreateNode('PYTHON'))}
            />
            <MenuItem
              label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.javaNode' })}
              icon={<JavaIcon size={14} />}
              onClick={() => closeAnd(() => onCreateNode('JAVA'))}
            />
          </MenuPanel>
        </div>
      ) : null}
    </div>
  );

  return (
    <>
      <Popover
        trigger="click"
        placement="bottomRight"
        arrow={false}
        open={open}
        onOpenChange={(nextOpen) => {
          setOpen(nextOpen);
          if (!nextOpen) setLevel(undefined);
        }}
        overlayClassName="development-create-menu-popover"
        content={content}
      >
        {children}
      </Popover>
      <style>{`
        .development-create-menu-popover .ant-popover-inner {
          padding: 0;
          overflow: visible;
          background: transparent;
          box-shadow: none;
        }
        .development-create-menu-popover .ant-popover-inner-content {
          padding: 0;
          overflow: visible;
        }
      `}</style>
    </>
  );
};

export default DevelopmentCreateMenu;
