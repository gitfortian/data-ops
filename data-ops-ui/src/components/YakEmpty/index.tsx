import { Folder } from 'lucide-react';
import type { ReactNode } from 'react';

interface YakEmptyProps {
  title?: ReactNode;
  description?: ReactNode;
  compact?: boolean;
  className?: string;
  children?: ReactNode;
  icon?: ReactNode;
}

const YakEmpty = ({
  title = '暂无数据',
  description,
  compact = false,
  className = '',
  children,
  icon,
}: YakEmptyProps) => (
  <div
    className={[
      'flex w-full flex-col items-center justify-center px-5 text-center',
      compact ? 'min-h-[140px] py-6' : 'min-h-[240px] py-10',
      className,
    ].join(' ')}
  >
    <div
      className={[
        'flex items-center justify-center rounded-xl bg-[#f6f7f8] text-[#c4c9d1]',
        compact ? 'h-10 w-10' : 'h-12 w-12',
      ].join(' ')}
    >
      {icon ?? <Folder size={compact ? 20 : 24} strokeWidth={1.4} />}
    </div>
    <div className="mt-3 text-[14px] font-medium text-[#344054]">{title}</div>
    {description ? (
      <div className="mt-1 max-w-[360px] text-[13px] leading-5 text-[#667085]">
        {description}
      </div>
    ) : null}
    {children ? <div className="mt-3">{children}</div> : null}
  </div>
);

export default YakEmpty;
