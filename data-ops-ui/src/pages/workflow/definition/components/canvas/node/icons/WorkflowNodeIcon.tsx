import JavaIcon from '@/components/data-development/icons/JavaIcon';
import PythonIcon from '@/components/data-development/icons/PythonIcon';
import { TerminalSquare } from 'lucide-react';
import type { ComponentType, CSSProperties, SVGProps } from 'react';
import SyncNodeIcon from './SyncNodeIcon';

interface WorkflowNodeIconProps {
  taskType?: string;
  size?: 'xs' | 'sm' | 'md';
}

interface NodeIconMeta {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
}

type IconSize = NonNullable<WorkflowNodeIconProps['size']>;

/**
 * Default workflow node icon.
 */
const DefaultNodeIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    {...props}
  >
    <rect
      x="5"
      y="5"
      width="5"
      height="5"
      rx="1.5"
      stroke="currentColor"
      strokeWidth="1.6"
    />

    <rect
      x="14"
      y="14"
      width="5"
      height="5"
      rx="1.5"
      stroke="currentColor"
      strokeWidth="1.6"
    />

    <path
      d="M10 7.5h3.5a3 3 0 0 1 3 3V14"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
    />
  </svg>
);

/**
 * Data quality icon glyph.
 */
const QualityEndGlyph = (props: SVGProps<SVGSVGElement>) => (
  <svg
    viewBox="0 0 14 14"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    {...props}
  >
    <path
      fillRule="evenodd"
      clipRule="evenodd"
      d="M6.67315 1.18094C6.87691 1.0639 7.12769 1.06475 7.33067 1.18315L10.8307 3.22481C11.0323 3.34242 11.1562 3.55826 11.1562 3.79167C11.1562 4.02507 11.0323 4.24091 10.8307 4.35852L7.65625 6.21026V9.91667C7.65625 10.2791 7.36244 10.5729 7 10.5729C6.63756 10.5729 6.34375 10.2791 6.34375 9.91667V5.84577C6.34361 5.83788 6.34361 5.83 6.34375 5.82213V1.75C6.34375 1.51502 6.46939 1.29797 6.67315 1.18094ZM7.65625 4.69078L9.19758 3.79167L7.65625 2.89256V4.69078ZM5.31099 8.25466C5.37977 8.61051 5.14704 8.95473 4.79119 9.0235C3.97285 9.18165 3.32667 9.41764 2.90374 9.67762C2.45323 9.95454 2.40625 10.1564 2.40625 10.2086C2.40625 10.2448 2.42254 10.3508 2.60674 10.5202C2.79151 10.6901 3.09509 10.8732 3.52555 11.0406C4.38229 11.3738 5.61047 11.594 7 11.594C8.38954 11.594 9.61773 11.3738 10.4745 11.0406C10.9049 10.8732 11.2085 10.6901 11.3933 10.5202C11.5775 10.3508 11.5938 10.2448 11.5938 10.2086C11.5938 10.1564 11.5468 9.95454 11.0963 9.67762C10.6733 9.41764 10.0271 9.18165 9.20881 9.0235C8.85296 8.95473 8.62023 8.61051 8.68901 8.25465C8.75778 7.8988 9.102 7.66608 9.45786 7.73485C10.3682 7.91077 11.1803 8.18867 11.7836 8.55947C12.3592 8.91331 12.9062 9.45912 12.9062 10.2086C12.9062 10.7361 12.6287 11.1672 12.2816 11.4864C11.935 11.805 11.4698 11.805 11.4698 11.805C10.9502 12.2639 9.90679 12.6696 8.50997 12.9065C7 13.1434 5.49004 12.9065 4.09322 12.6696C3.04983 12.2639 2.53023 12.0618 2.06497 11.805C1.7184 11.4864 1.37128 10.7361 1.09375 10.2086C1.09375 9.45913 1.64077 8.91332 2.21642 8.55947C2.81966 8.18867 3.63181 7.91077 4.54215 7.73485C4.898 7.66608 5.24222 7.8988 5.31099 8.25466Z"
      fill="currentColor"
    />
  </svg>
);

const DEFAULT_ICON_META: NodeIconMeta = {
  icon: DefaultNodeIcon,
};

const NODE_ICON_META: Record<string, NodeIconMeta> = {
  SYNC: {
    icon: SyncNodeIcon,
  },
};

/**
 * Container physical size.
 *
 * Use explicit pixel sizes instead of relying only on h-* / w-* so that
 * workflow icons cannot be compressed by flex layouts or inherited styles.
 */
const CONTAINER_SIZE: Record<IconSize, number> = {
  xs: 16,
  sm: 20,
  md: 24,
};

/**
 * Inner glyph size.
 */
const ICON_SIZE: Record<IconSize, number> = {
  xs: 12,
  sm: 14,
  md: 16,
};

const CONTAINER_RADIUS: Record<IconSize, string> = {
  xs: 'rounded-[5px]',
  sm: 'rounded-md',
  md: 'rounded-lg',
};

const CONTAINER_SHADOW: Record<IconSize, string> = {
  xs: 'shadow-[0_1px_2px_rgba(16,24,40,.05)]',
  sm: 'shadow-[0_1px_2px_rgba(16,24,40,.05)]',
  md: 'shadow-[0_4px_8px_-2px_rgba(16,24,40,.10),0_2px_4px_-2px_rgba(16,24,40,.06)]',
};

/**
 * Brand icons sometimes have different intrinsic SVG proportions.
 */
const BRAND_ICON_SIZE: Record<IconSize, number> = {
  xs: 11,
  sm: 13,
  md: 15,
};

/**
 * SQL is text rather than an SVG icon, so its visual size needs its own scale.
 */
const SQL_TEXT_SIZE: Record<IconSize, string> = {
  xs: 'text-[7px]',
  sm: 'text-[8px]',
  md: 'text-[9px]',
};

const DATA_DEVELOPMENT_TASK_TYPES = new Set([
  'SQL',
  'SHELL',
  'PYTHON',
  'JAVA',
]);

const DATA_QUALITY_TASK_TYPES = new Set([
  'QUALITY',
  'DATA_QUALITY',
]);

/**
 * Hard-lock container width and height.
 *
 * flexBasis / minWidth / minHeight prevent parent flex layouts from
 * compressing the icon into a pill or flat rectangle.
 */
const getContainerStyle = (size: IconSize): CSSProperties => {
  const value = CONTAINER_SIZE[size];

  return {
    width: value,
    height: value,

    minWidth: value,
    minHeight: value,

    maxWidth: value,
    maxHeight: value,

    flexBasis: value,
    flexGrow: 0,
    flexShrink: 0,
  };
};

const getSvgStyle = (size: IconSize): CSSProperties => {
  const value = ICON_SIZE[size];

  return {
    width: value,
    height: value,

    minWidth: value,
    minHeight: value,

    maxWidth: value,
    maxHeight: value,

    display: 'block',
    flexShrink: 0,
  };
};

const DevelopmentTaskGlyph = ({
  taskType,
  size,
}: {
  taskType: string;
  size: IconSize;
}) => {
  if (taskType === 'SQL') {
    return (
      <span
        className={[
          'block whitespace-nowrap font-bold leading-none',
          'tracking-[-0.06em]',
          SQL_TEXT_SIZE[size],
        ].join(' ')}
      >
        SQL
      </span>
    );
  }

  if (taskType === 'JAVA') {
    return (
      <span className="flex items-center justify-center leading-none">
        <JavaIcon size={BRAND_ICON_SIZE[size]} />
      </span>
    );
  }

  if (taskType === 'PYTHON') {
    return (
      <span className="flex items-center justify-center leading-none">
        <PythonIcon size={BRAND_ICON_SIZE[size]} />
      </span>
    );
  }

  return (
    <TerminalSquare
      size={BRAND_ICON_SIZE[size]}
      strokeWidth={1.8}
      aria-hidden="true"
      className="block shrink-0"
    />
  );
};

const developmentContainerClassName = (taskType: string) => {
  if (taskType === 'SHELL') {
    return 'border-[#d9ddff] bg-[#f4f5ff] text-[#6172f3]';
  }

  return 'border-[#e4e7ec] bg-white text-[#344054]';
};

const WorkflowNodeIcon = ({
  taskType,
  size = 'md',
}: WorkflowNodeIconProps) => {
  const normalizedTaskType = (taskType || '').toUpperCase();

  const meta =
    NODE_ICON_META[normalizedTaskType]
    || DEFAULT_ICON_META;

  const Icon = meta.icon;

  /**
   * Offline / realtime sync.
   */
  if (normalizedTaskType === 'SYNC') {
    return (
      <span
        style={getContainerStyle(size)}
        className={[
          'box-border flex items-center justify-center',
          'overflow-hidden',
          'border-[0.5px] border-white/[0.02]',
          'bg-[#6172f3] text-white',
          CONTAINER_RADIUS[size],
          CONTAINER_SHADOW[size],
        ].join(' ')}
      >
        <Icon
          style={getSvgStyle(size)}
          aria-hidden="true"
        />
      </span>
    );
  }

  /**
   * Data quality.
   */
  if (DATA_QUALITY_TASK_TYPES.has(normalizedTaskType)) {
    return (
      <span
        style={getContainerStyle(size)}
        className={[
          'box-border flex items-center justify-center',
          'overflow-hidden',
          'border-[0.5px] border-white/[0.02]',
          'bg-[#f79009] text-white',
          CONTAINER_RADIUS[size],
          CONTAINER_SHADOW[size],
        ].join(' ')}
      >
        <QualityEndGlyph
          style={getSvgStyle(size)}
          aria-hidden="true"
        />
      </span>
    );
  }

  /**
   * SQL.
   *
   * SQL uses a colored container, but the physical size is exactly the same
   * as all other workflow icons.
   */
  if (normalizedTaskType === 'SQL') {
    return (
      <span
        style={getContainerStyle(size)}
        className={[
          'box-border flex items-center justify-center',
          'overflow-hidden',
          'bg-[#f79009] text-white',
          CONTAINER_RADIUS[size],
          CONTAINER_SHADOW[size],
        ].join(' ')}
      >
        <DevelopmentTaskGlyph
          taskType={normalizedTaskType}
          size={size}
        />
      </span>
    );
  }

  /**
   * Shell / Java / Python.
   */
  if (DATA_DEVELOPMENT_TASK_TYPES.has(normalizedTaskType)) {
    return (
      <span
        style={getContainerStyle(size)}
        className={[
          'box-border flex items-center justify-center',
          'overflow-hidden',
          'border-[0.5px]',
          CONTAINER_RADIUS[size],
          CONTAINER_SHADOW[size],
          developmentContainerClassName(normalizedTaskType),
        ].join(' ')}
      >
        <DevelopmentTaskGlyph
          taskType={normalizedTaskType}
          size={size}
        />
      </span>
    );
  }

  /**
   * Fallback workflow node.
   *
   * Previously this branch used a different size system:
   *
   * sm = 28px
   * md = 36px
   *
   * That caused identical `size` props to render differently depending on
   * taskType. All branches now share the same physical size table.
   */
  return (
    <span
      style={getContainerStyle(size)}
      className={[
        'box-border flex items-center justify-center',
        'overflow-hidden',
        'bg-[#6172f3] text-white',
        CONTAINER_RADIUS[size],
        CONTAINER_SHADOW[size],
      ].join(' ')}
    >
      <Icon
        style={getSvgStyle(size)}
        aria-hidden="true"
      />
    </span>
  );
};

export default WorkflowNodeIcon;