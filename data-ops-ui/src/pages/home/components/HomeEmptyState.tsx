import type { LucideIcon } from 'lucide-react';

interface HomeEmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  size?: 'small' | 'medium';
  className?: string;
}

const SIZE_STYLES = {
  small: {
    iconBox: 'h-8 w-8 rounded-[9px]',
    iconSize: 15,
    title: 'text-[10px] leading-4',
    description: 'text-[9px] leading-4',
    gap: 'gap-2',
  },
  medium: {
    iconBox: 'h-9 w-9 rounded-[10px]',
    iconSize: 17,
    title: 'text-[11px] leading-5',
    description: 'text-[10px] leading-4',
    gap: 'gap-2.5',
  },
} as const;

/**
 * 首页轻量空状态。
 *
 * 小型列表、图表和侧栏区域使用图标 + 文案，避免同一屏重复出现
 * YakOpsEmpty 品牌插画。大型主画布仍保留 YakOpsEmpty。
 */
export function HomeEmptyState({
  icon: Icon,
  title,
  description,
  size = 'medium',
  className = '',
}: HomeEmptyStateProps) {
  const styles = SIZE_STYLES[size];

  return (
    <div
      className={`flex flex-col items-center justify-center text-center ${styles.gap} ${className}`}
    >
      <span
        className={`flex shrink-0 items-center justify-center bg-[#f4f5f7] text-[#a4a9b2] ${styles.iconBox}`}
      >
        <Icon size={styles.iconSize} strokeWidth={1.7} />
      </span>

      <div>
        <div className={`font-medium text-[#858b95] ${styles.title}`}>
          {title}
        </div>
        {description ? (
          <div className={`mt-0.5 text-[#a7abb3] ${styles.description}`}>
            {description}
          </div>
        ) : null}
      </div>
    </div>
  );
}
