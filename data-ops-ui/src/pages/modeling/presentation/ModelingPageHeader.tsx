import type { ReactNode } from 'react';

export default function ModelingPageHeader({ title, description, actions, compact = false }: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  compact?: boolean;
}) {
  const Heading = compact ? 'h2' : 'h1';
  return <header className={`modeling-page-header${compact ? ' modeling-section-header' : ''}`}>
    <div className="modeling-page-heading">
      <Heading className="modeling-page-title">{title}</Heading>
      {description && <div className="modeling-page-description">{description}</div>}
    </div>
    {actions && <div className="modeling-page-actions">{actions}</div>}
  </header>;
}
