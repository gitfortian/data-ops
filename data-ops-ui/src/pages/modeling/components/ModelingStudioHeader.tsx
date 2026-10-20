import { BookOpenText } from 'lucide-react';
import type { ReactNode } from 'react';
import './ModelingStudioHeader.less';

type StudioArea = 'catalog' | 'logical' | 'physical';

interface ModelingStudioHeaderProps {
  area: StudioArea;
  title: string;
  description: string;
  eyebrow?: string | null;
  context?: ReactNode;
  actions?: ReactNode;
}

/**
 * SiteLayout owns navigation and permission-filtered menus.
 * The modeling page only provides a business heading and contextual actions.
 */
export default function ModelingStudioHeader({
  area, title, description, eyebrow = '标准与建模 / 数仓建模', context, actions,
}: ModelingStudioHeaderProps) {
  return (
    <header className="modeling-studio-header" data-area={area}>
      <div className="modeling-studio-header-top">
        <div className="modeling-studio-heading">
          {eyebrow ? (
            <div className="modeling-studio-kicker">
              <BookOpenText size={14} aria-hidden />
              {eyebrow}
            </div>
          ) : null}
          <h1>{title}</h1>
          <p>{description}</p>
          {context ? <div className="modeling-studio-context">{context}</div> : null}
        </div>
        {actions ? <div className="modeling-studio-header-actions">{actions}</div> : null}
      </div>
    </header>
  );
}
