import type { ReactNode } from 'react';

interface SystemManagementPageProps {
  title: string;
  titleId: string;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
}

/**
 * Shared page frame for the visible System Management workspaces.
 *
 * The frame owns the common viewport background, padding and heading rhythm so
 * individual pages can focus on their business composition.
 */
export default function SystemManagementPage({
  title,
  titleId,
  icon,
  children,
  className,
}: SystemManagementPageProps) {
  return (
    <section
      aria-labelledby={titleId}
      className={[
        'box-border flex flex-col bg-slate-50/50 p-6',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="mb-4 flex shrink-0 items-center gap-2">
        {icon}
        <h1
          id={titleId}
          className="m-0 text-[18px] font-semibold text-[#282828]"
        >
          {title}
        </h1>
      </div>
      {children}
    </section>
  );
}
