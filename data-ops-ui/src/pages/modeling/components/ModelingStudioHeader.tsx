import { history } from '@umijs/max';
import { ArrowRight, BookOpenText, Database, GitBranch, LayoutDashboard, Workflow } from 'lucide-react';
import type { ReactNode } from 'react';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import './ModelingStudioHeader.less';

type StudioArea = 'catalog' | 'logical' | 'physical';

interface ModelingStudioHeaderProps {
  area: StudioArea;
  title: string;
  description: string;
  eyebrow?: string;
  context?: ReactNode;
  actions?: ReactNode;
  onNavigate?: (path: string) => void;
}

/**
 * Shared production chrome for manual modeling. Routes point to the actual,
 * owner-authorized application workspaces; they never imply a stage is complete.
 */
export default function ModelingStudioHeader({
  area, title, description, eyebrow = '数据建设 / 数仓建模',
  context, actions, onNavigate,
}: ModelingStudioHeaderProps) {
  const { can } = usePermissionAccess();
  const navigate = (path: string) => {
    if (onNavigate) onNavigate(path);
    else history.push(path);
  };
  const places = [
    ...(can('semantic:read') ? [{
      key: 'process', label: '业务过程', description: '明确业务粒度', path: '/semantic/processes', icon: Workflow,
    }] : []),
    { key: 'logical', label: '逻辑建模', description: '定义实体与关系', path: '/modeling/logical', icon: GitBranch },
    { key: 'physical', label: '物理建模', description: '设计模型与映射', path: '/modeling', icon: Database },
    { key: 'mainline', label: '分层主线', description: '按过程核对各层', path: '/modeling/mainline', icon: LayoutDashboard },
  ];
  const active = area === 'catalog' ? 'physical' : area;

  return (
    <header className="modeling-studio-header">
      <div className="modeling-studio-header-top">
        <div className="modeling-studio-heading">
          <div className="modeling-studio-kicker"><BookOpenText size={14} aria-hidden />{eyebrow}</div>
          <h1>{title}</h1>
          <p>{description}</p>
          {context ? <div className="modeling-studio-context">{context}</div> : null}
        </div>
        {actions ? <div className="modeling-studio-header-actions">{actions}</div> : null}
      </div>
      <nav className="modeling-studio-path" aria-label="数仓建模工作区导航">
        {places.map((place) => {
          const Icon = place.icon;
          const isActive = active === place.key;
          return (
            <button key={place.key} type="button" className={'modeling-studio-path-item' + (isActive ? ' is-active' : '')}
              aria-current={isActive ? 'page' : undefined}
              onClick={() => navigate(place.path)}>
              <span className="modeling-studio-path-icon"><Icon size={17} aria-hidden /></span>
              <span className="modeling-studio-path-copy"><strong>{place.label}</strong><small>{place.description}</small></span>
              <ArrowRight size={15} className="modeling-studio-path-arrow" aria-hidden />
            </button>
          );
        })}
      </nav>
    </header>
  );
}
