import YakButton from '@/components/YakButton';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { createDevelopmentNode } from '@/services/data-development/legacy';
import type { DevelopmentNodeType } from '@/services/data-development';
import {
  API_SUCCESS_CODE,
  extractErrorMessage,
  type ApiResponse,
} from '@/services/http/response';
import { createWorkflowDefinition } from '@/services/workflow/definitions';
import { BRAND_THEME } from '@/styles/brand';
import {
  CodeOutlined,
  DatabaseOutlined,
  ExclamationCircleOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { history, request, useLocation } from '@umijs/max';
import {
  ConfigProvider,
  Form,
  Input,
  message,
  type InputProps,
} from 'antd';
import type { TextAreaProps } from 'antd/es/input/TextArea';
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type {
  AnimationEvent as ReactAnimationEvent,
  FocusEventHandler,
  ReactNode,
} from 'react';

type CreateType =
  | 'offline'
  | 'realtime'
  | 'development'
  | 'workflow';

type OfflineMode = 'GUIDE_SINGLE' | 'GUIDE_MULTI';

type RealtimeEditorMode = 'wizard' | 'yaml';

type TabMotionDirection = 'forward' | 'backward';

type PanelMotionPhase = 'idle' | 'leaving' | 'entering';

interface QuickCreateFormValues {
  name: string;
}

interface CreateTab {
  key: CreateType;
  label: string;
  title: string;
  buttonText: string;
  maxLength: number;
}

interface ChoiceOption<T extends string> {
  value: T;
  label: string;
  description?: string;
  icon?: ReactNode;
}

interface TabIndicatorPosition {
  left: number;
  width: number;
  ready: boolean;
}

type FloatingInputProps = Omit<
  InputProps,
  'onFocus' | 'onBlur' | 'placeholder'
> & {
  label: string;
  textarea?: boolean;
  rows?: number;
  autoSize?: TextAreaProps['autoSize'];
  onFocus?: FocusEventHandler<HTMLInputElement | HTMLTextAreaElement>;
  onBlur?: FocusEventHandler<HTMLInputElement | HTMLTextAreaElement>;
};

const CREATE_TABS: CreateTab[] = [
  {
    key: 'offline',
    label: '离线同步',
    title: '创建离线同步任务',
    buttonText: '创建并进入配置',
    maxLength: 64,
  },
  {
    key: 'realtime',
    label: '实时同步',
    title: '创建实时同步任务',
    buttonText: '创建并进入编辑器',
    maxLength: 200,
  },
  {
    key: 'development',
    label: '数据开发',
    title: '创建开发节点',
    buttonText: '创建并打开编辑器',
    maxLength: 128,
  },
  {
    key: 'workflow',
    label: '工作流',
    title: '创建工作流',
    buttonText: '创建并进入编排',
    maxLength: 100,
  },
];

const OFFLINE_MODES: ChoiceOption<OfflineMode>[] = [
  {
    value: 'GUIDE_SINGLE',
    label: '单表同步',
    description: '一张来源表同步到一张目标表',
    icon: <TableOutlined />,
  },
  {
    value: 'GUIDE_MULTI',
    label: '多表同步',
    description: '批量选择多张表完成同步',
    icon: <DatabaseOutlined />,
  },
];

const REALTIME_EDITOR_MODES: ChoiceOption<RealtimeEditorMode>[] = [
  {
    value: 'wizard',
    label: '向导模式',
    description: '通过可视化步骤完成同步配置',
  },
  {
    value: 'yaml',
    label: 'YAML 模式',
    description: '直接使用 YAML 编辑完整配置',
    icon: <CodeOutlined />,
  },
];

const DEVELOPMENT_TYPES: ChoiceOption<DevelopmentNodeType>[] = [
  {
    value: 'SQL',
    label: 'SQL',
  },
  {
    value: 'PYTHON',
    label: 'Python',
  },
  {
    value: 'SHELL',
    label: 'Shell',
  },
  {
    value: 'JAVA',
    label: 'Java',
  },
  {
    value: 'DATASET',
    label: '数据集',
  },
  {
    value: 'DATA_SERVICE',
    label: '数据服务',
  },
];

const PAGE_FONT =
  "'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif";

const FORM_ITEM_CLASS_NAME =
  '!mb-5 [&_.ant-form-item-explain]:!pt-1.5 [&_.ant-form-item-explain-error]:!text-[12px] [&_.ant-form-item-explain-error]:!leading-[18px] [&_.ant-form-item-explain-error]:!text-[#b42318]';

/**
 * Tab、顶部说明栏与表单区域的切换动画。
 *
 * - 下划线使用带轻微回弹的水平过渡；
 * - 顶部说明栏和中间表单同步离场、同步入场；
 * - 旧内容先向切换方向的反方向淡出；
 * - 新内容从目标 Tab 所在方向滑入，并轻微回摆归位；
 * - 系统开启“减少动态效果”时，将动画压缩到 1ms。
 */
const CREATE_PAGE_MOTION_STYLES = `
  @keyframes yakCreatePanelLeaveLeft {
    from {
      opacity: 1;
      transform: translate3d(0, 0, 0) scale(1);
    }

    to {
      opacity: 0;
      transform: translate3d(-10px, 0, 0) scale(0.998);
    }
  }

  @keyframes yakCreatePanelLeaveRight {
    from {
      opacity: 1;
      transform: translate3d(0, 0, 0) scale(1);
    }

    to {
      opacity: 0;
      transform: translate3d(10px, 0, 0) scale(0.998);
    }
  }

  @keyframes yakCreatePanelEnterFromRight {
    0% {
      opacity: 0;
      transform: translate3d(18px, 0, 0) scale(0.997);
    }

    58% {
      opacity: 1;
      transform: translate3d(-3px, 0, 0) scale(1);
    }

    78% {
      opacity: 1;
      transform: translate3d(1.25px, 0, 0) scale(1);
    }

    100% {
      opacity: 1;
      transform: translate3d(0, 0, 0) scale(1);
    }
  }

  @keyframes yakCreatePanelEnterFromLeft {
    0% {
      opacity: 0;
      transform: translate3d(-18px, 0, 0) scale(0.997);
    }

    58% {
      opacity: 1;
      transform: translate3d(3px, 0, 0) scale(1);
    }

    78% {
      opacity: 1;
      transform: translate3d(-1.25px, 0, 0) scale(1);
    }

    100% {
      opacity: 1;
      transform: translate3d(0, 0, 0) scale(1);
    }
  }

  .yak-create-tab-indicator {
    will-change: width, transform;
    transition:
      width 380ms cubic-bezier(0.22, 1, 0.36, 1),
      transform 420ms cubic-bezier(0.34, 1.32, 0.64, 1),
      opacity 120ms ease-out;
  }

  .yak-create-panel-motion {
    backface-visibility: hidden;
    transform: translate3d(0, 0, 0);
    transform-origin: 50% 45%;
    will-change: opacity, transform;
  }

  .yak-create-guide-motion {
    backface-visibility: hidden;
    transform: translate3d(0, 0, 0);
    transform-origin: 50% 50%;
    will-change: opacity, transform;
  }

  .yak-create-panel-leave-left {
    animation:
      yakCreatePanelLeaveLeft
      130ms
      cubic-bezier(0.4, 0, 1, 1)
      both;
  }

  .yak-create-panel-leave-right {
    animation:
      yakCreatePanelLeaveRight
      130ms
      cubic-bezier(0.4, 0, 1, 1)
      both;
  }

  .yak-create-panel-enter-right {
    animation:
      yakCreatePanelEnterFromRight
      360ms
      cubic-bezier(0.22, 1, 0.36, 1)
      both;
  }

  .yak-create-panel-enter-left {
    animation:
      yakCreatePanelEnterFromLeft
      360ms
      cubic-bezier(0.22, 1, 0.36, 1)
      both;
  }

  @media (prefers-reduced-motion: reduce) {
    .yak-create-tab-indicator {
      transition-duration: 1ms !important;
    }

    .yak-create-panel-leave-left,
    .yak-create-panel-leave-right,
    .yak-create-panel-enter-right,
    .yak-create-panel-enter-left {
      animation-duration: 1ms !important;
    }
  }
`;

/**
 * Quick Create 只负责创建最小草稿。
 *
 * 离线同步和实时同步使用轻量接口，避免本页面依赖数据源、
 * 运行环境、Connector 等完整业务配置。
 */
const quickCreateApi = {
  offline: (payload: {
    jobName: string;
    mode: OfflineMode;
  }) =>
    request<ApiResponse<string | number>>(
      '/api/v1/job/batch-definition/quick-create',
      {
        method: 'POST',
        data: payload,
      },
    ),

  realtime: (payload: { name: string }) =>
    request<ApiResponse<number>>(
      '/api/v1/realtime-sync/quick-create',
      {
        method: 'POST',
        data: payload,
      },
    ),
};

const normalizeCreateType = (
  value?: string | null,
): CreateType =>
  CREATE_TABS.some((item) => item.key === value)
    ? (value as CreateType)
    : 'offline';

const unwrapApiResponse = <T,>(
  response: ApiResponse<T>,
  fallback: string,
): T => {
  if (response.code !== API_SUCCESS_CODE) {
    throw new Error(
      extractErrorMessage(response, fallback),
    );
  }

  if (
    response.data === undefined ||
    response.data === null
  ) {
    throw new Error(fallback);
  }

  return response.data;
};

/**
 * 同时支持普通 Input 和 TextArea 的浮动标签控件。
 *
 * 当前快速创建只使用名称 Input；后续页面新增备注、描述等 TextArea 时，
 * 直接传 textarea 即可保持和登录页一致的交互与视觉。
 */
function FloatingInput({
  label,
  textarea = false,
  rows = 4,
  autoSize,
  onBlur,
  onFocus,
  value,
  className,
  ...inputProps
}: FloatingInputProps) {
  const [focused, setFocused] =
    useState(false);

  const { status } =
    Form.Item.useStatus();

  const floating =
    focused ||
    String(value ?? '').length > 0;

  const hasError = status === 'error';

  const handleFocus: FocusEventHandler<
    HTMLInputElement | HTMLTextAreaElement
  > = (event) => {
    setFocused(true);
    onFocus?.(event);
  };

  const handleBlur: FocusEventHandler<
    HTMLInputElement | HTMLTextAreaElement
  > = (event) => {
    setFocused(false);
    onBlur?.(event);
  };

  const borderClassName = hasError
    ? '!border-[#d92d20] hover:!border-[#d92d20] focus:!border-[#d92d20] focus-within:!border-[#d92d20]'
    : '!border-[#dededb] hover:!border-[#bdbdb8] focus:!border-[#171717] focus-within:!border-[#171717]';

  const controlClassName = textarea
    ? [
        '!min-h-[116px] !rounded-[22px] !bg-white !px-4 !pb-3 !pt-4',
        '!text-[15px] !leading-6 !shadow-none',
        borderClassName,
        className,
      ]
        .filter(Boolean)
        .join(' ')
    : [
        '!h-11 !rounded-full !bg-white !px-4 !text-[15px] !shadow-none',
        borderClassName,
        className,
      ]
        .filter(Boolean)
        .join(' ');

  const commonProps = {
    ...inputProps,
    value,
    className: controlClassName,
    placeholder: '',
    onFocus: handleFocus,
    onBlur: handleBlur,
  };

  return (
    <div className="relative">
      {textarea ? (
        <Input.TextArea
          {...(commonProps as TextAreaProps)}
          rows={rows}
          autoSize={autoSize}
        />
      ) : (
        <Input
          {...(commonProps as InputProps)}
        />
      )}

      <label
        htmlFor={inputProps.id}
        className={[
          'pointer-events-none absolute left-4 z-10 bg-white px-1',
          'transition-all duration-200 ease-out',
          floating
            ? 'top-0 -translate-y-1/2 text-[12px] font-medium text-[#333]'
            : textarea
              ? 'top-[22px] -translate-y-1/2 text-[15px] text-[#aaa]'
              : 'top-1/2 -translate-y-1/2 text-[15px] text-[#aaa]',
        ].join(' ')}
      >
        {label}
      </label>
    </div>
  );
}

function ValidationMessage({
  children,
}: {
  children: string;
}) {
  return (
    <span
      className="inline-flex h-[18px] items-center gap-1.5 align-middle leading-[18px]"
      style={{
        marginBottom: 8,
      }}
    >
      <ExclamationCircleOutlined className="flex shrink-0 items-center text-[12px] leading-none [&_svg]:block" />

      <span className="leading-[18px]">
        {children}
      </span>
    </span>
  );
}

function ChoiceGrid<T extends string>({
  value,
  options,
  columns = 2,
  compact = false,
  onChange,
}: {
  value: T;
  options: ChoiceOption<T>[];
  columns?: 2 | 3;
  compact?: boolean;
  onChange: (value: T) => void;
}) {
  return (
    <div
      className={[
        'grid gap-2.5',
        columns === 3
          ? 'grid-cols-3'
          : 'grid-cols-2',
      ].join(' ')}
    >
      {options.map((item) => {
        const selected =
          value === item.value;

        return (
          <button
            key={item.value}
            type="button"
            onClick={() =>
              onChange(item.value)
            }
            className={[
              'group relative border bg-white text-left transition-all duration-150',
              compact
                ? 'h-11 rounded-full px-4'
                : 'min-h-[74px] rounded-[18px] px-4 py-3.5',
              selected
                ? 'border-[#171717] shadow-[0_0_0_1px_#171717]'
                : 'border-[#dededb] hover:border-[#bdbdb8]',
            ].join(' ')}
          >
            <div
              className={[
                'flex h-full items-center',
                compact
                  ? 'justify-center'
                  : 'gap-3',
              ].join(' ')}
            >
              {!compact && item.icon ? (
                <span
                  className={[
                    'flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-[15px]',
                    selected
                      ? 'bg-[#171717] text-white'
                      : 'bg-[#f1f1ee] text-[#74746f]',
                  ].join(' ')}
                >
                  {item.icon}
                </span>
              ) : null}

              <span
                className={
                  compact
                    ? 'text-center'
                    : 'min-w-0 pr-5'
                }
              >
                <span
                  className={[
                    'block text-[13px] font-medium',
                    selected
                      ? 'text-[#171717]'
                      : 'text-[#3f3f3b]',
                  ].join(' ')}
                >
                  {item.label}
                </span>

                {!compact &&
                item.description ? (
                  <span className="mt-1 block text-[11px] leading-[17px] text-[#999994]">
                    {
                      item.description
                    }
                  </span>
                ) : null}
              </span>
            </div>

            {!compact ? (
              <span
                className={[
                  'absolute right-3 top-3 flex h-4 w-4 items-center justify-center rounded-full border',
                  selected
                    ? 'border-[#171717]'
                    : 'border-[#cfcfca]',
                ].join(' ')}
              >
                {selected ? (
                  <span className="h-2 w-2 rounded-full bg-[#171717]" />
                ) : null}
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}

function QuickCreateGuideIcon() {
  return (
    <svg
      width="50"
      height="46"
      viewBox="0 0 50 46"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M4.5 11.5C4.5 8.46243 6.96243 6 10 6H19.0503C20.4296 6 21.7523 6.54797 22.7277 7.52332L25.2044 10H39.5C42.5376 10 45 12.4624 45 15.5V18H4.5V11.5Z"
        fill="#D5E5FC"
      />

      <path
        d="M6.19006 16.6202C6.70786 14.7216 8.4316 13.4048 10.3995 13.4048H42.2453C45.1495 13.4048 47.247 16.1834 46.4538 18.9772L40.8764 38.6224C40.3439 40.4979 38.6311 41.7917 36.6815 41.7917H4.68009C1.7732 41.7917 -0.32492 39.0082 0.473174 36.2129L6.19006 16.6202Z"
        fill="#BDD5F8"
      />

      <path
        d="M7.79688 18.2041H43.9268L38.8545 36.0956C38.5684 37.1047 37.6478 37.8021 36.599 37.8021H4.6875L7.79688 18.2041Z"
        fill="#C9DDFC"
      />
    </svg>
  );
}

function EditorGuideIcon() {
  return (
    <svg
      width="50"
      height="46"
      viewBox="0 0 50 46"
      fill="none"
      aria-hidden="true"
    >
      <rect
        x="4"
        y="7"
        width="38"
        height="35"
        rx="7"
        fill="#D4E4FD"
      />

      <path
        d="M15.5 20.5H30.5M30.5 20.5L26.75 16.75M30.5 20.5L26.75 24.25"
        stroke="#86A8D9"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      <path
        d="M30.5 29H15.5M15.5 29L19.25 25.25M15.5 29L19.25 32.75"
        stroke="#86A8D9"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      <path
        d="M42.5 2.5C42.9581 5.67343 45.3266 8.04189 48.5 8.5C45.3266 8.95811 42.9581 11.3266 42.5 14.5C42.0419 11.3266 39.6734 8.95811 36.5 8.5C39.6734 8.04189 42.0419 5.67343 42.5 2.5Z"
        fill="#AFC8F2"
      />
    </svg>
  );
}

function DraftGuideIcon() {
  return (
    <svg
      width="50"
      height="46"
      viewBox="0 0 50 46"
      fill="none"
      aria-hidden="true"
    >
      <rect
        x="13"
        y="4"
        width="31"
        height="31"
        rx="7"
        fill="#D5E5FC"
      />

      <rect
        x="5"
        y="17"
        width="23"
        height="25"
        rx="5.5"
        fill="#ABC7F0"
      />

      <path
        d="M11.5 29.5L15.25 33.25L22 25.75"
        stroke="white"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

interface CreateGuideItem {
  key: string;
  title: string;
  description: string;
  icon: ReactNode;
}

/**
 * 顶部说明区会与当前 Tab 一起切换。
 *
 * 文案只保留当前创建场景真正需要表达的三步，避免信息过多；
 * 同时让这一栏的左右位移动画有明确的内容切换感。
 */
const CREATE_GUIDE_ITEMS: Record<
  CreateType,
  readonly CreateGuideItem[]
> = {
  offline: [
    {
      key: 'quick',
      title: '快速创建',
      description:
        '填写任务名称并选择同步方式',
      icon: <QuickCreateGuideIcon />,
    },
    {
      key: 'editor',
      title: '进入配置',
      description:
        '继续配置数据源、表和字段映射',
      icon: <EditorGuideIcon />,
    },
    {
      key: 'draft',
      title: '草稿状态',
      description:
        '不会自动发布或启动任务',
      icon: <DraftGuideIcon />,
    },
  ],

  realtime: [
    {
      key: 'quick',
      title: '快速创建',
      description:
        '填写任务名称并选择编辑方式',
      icon: <QuickCreateGuideIcon />,
    },
    {
      key: 'editor',
      title: '进入编辑器',
      description:
        '继续配置运行环境和 CDC 参数',
      icon: <EditorGuideIcon />,
    },
    {
      key: 'draft',
      title: '草稿状态',
      description:
        '不会自动发布或启动任务',
      icon: <DraftGuideIcon />,
    },
  ],

  development: [
    {
      key: 'quick',
      title: '创建节点',
      description:
        '填写节点名称并选择开发类型',
      icon: <QuickCreateGuideIcon />,
    },
    {
      key: 'editor',
      title: '打开编辑器',
      description:
        '继续编写和调试节点内容',
      icon: <EditorGuideIcon />,
    },
    {
      key: 'draft',
      title: '草稿状态',
      description:
        '保存后再发布或加入调度',
      icon: <DraftGuideIcon />,
    },
  ],

  workflow: [
    {
      key: 'quick',
      title: '创建工作流',
      description:
        '先创建一个空白工作流',
      icon: <QuickCreateGuideIcon />,
    },
    {
      key: 'editor',
      title: '进入编排',
      description:
        '继续添加任务和依赖关系',
      icon: <EditorGuideIcon />,
    },
    {
      key: 'draft',
      title: '草稿状态',
      description:
        '不会自动发布或启动调度',
      icon: <DraftGuideIcon />,
    },
  ],
};

function CreateGuide({
  type,
  motionClassName,
  transitioning,
}: {
  type: CreateType;
  motionClassName: string;
  transitioning: boolean;
}) {
  const items =
    CREATE_GUIDE_ITEMS[type];

  return (
    <div className="overflow-hidden bg-white">
      <div
        key={type}
        aria-busy={transitioning}
        className={[
          'yak-create-guide-motion grid h-[108px] grid-cols-3 px-8',
          motionClassName,
          transitioning
            ? 'pointer-events-none'
            : '',
        ]
          .filter(Boolean)
          .join(' ')}
        style={{
          fontFamily: PAGE_FONT,
        }}
      >
        {items.map((item, index) => (
          <div
            key={item.key}
            className="relative flex h-full items-center pl-[72px] 2xl:pl-[96px]"
          >
            <div className="flex min-w-0 items-center gap-[14px]">
              <span className="flex h-[46px] w-[50px] shrink-0 items-center justify-center">
                {item.icon}
              </span>

              <div className="min-w-0 pt-px">
                <div className="text-[13px] font-semibold leading-[18px] text-[#161823]">
                  {item.title}
                </div>

                <div className="mt-1 max-w-[240px] text-[11px] font-normal leading-[17px] text-[#8a9099]">
                  {
                    item.description
                  }
                </div>
              </div>
            </div>

            {index <
            items.length - 1 ? (
              <span className="absolute right-0 top-1/2 h-[56px] w-px -translate-y-1/2 bg-[#e8e9ec]" />
            ) : null}
          </div>
        ))}
      </div>
    </div>
  );
}

export default function UnifiedCreatePage() {
  const location = useLocation();

  const { currentProject } =
    useSecurityProject();

  const [form] =
    Form.useForm<QuickCreateFormValues>();

  const activeType = useMemo(
    () =>
      normalizeCreateType(
        new URLSearchParams(
          location.search,
        ).get('type'),
      ),
    [location.search],
  );

  const [
    submitting,
    setSubmitting,
  ] = useState(false);

  const [
    offlineMode,
    setOfflineMode,
  ] =
    useState<OfflineMode>(
      'GUIDE_SINGLE',
    );

  const [
    realtimeEditorMode,
    setRealtimeEditorMode,
  ] =
    useState<RealtimeEditorMode>(
      'wizard',
    );

  const [
    developmentType,
    setDevelopmentType,
  ] =
    useState<DevelopmentNodeType>(
      'SQL',
    );

  /**
   * activeType 控制顶部 Tab 和 URL。
   *
   * displayedType 控制当前真正渲染的说明栏与表单。
   * 两者短暂分离，才能先播放旧内容离场，
   * 再切换内容并播放新内容入场动画。
   */
  const [
    displayedType,
    setDisplayedType,
  ] =
    useState<CreateType>(
      activeType,
    );

  const [
    motionDirection,
    setMotionDirection,
  ] =
    useState<TabMotionDirection>(
      'forward',
    );

  const [
    motionPhase,
    setMotionPhase,
  ] =
    useState<PanelMotionPhase>(
      'idle',
    );

  const targetTypeRef =
    useRef<CreateType>(activeType);

  const handledActiveTypeRef =
    useRef<CreateType>(activeType);

  const tabListRef =
    useRef<HTMLDivElement | null>(
      null,
    );

  const tabButtonRefs = useRef<
    Record<
      CreateType,
      HTMLButtonElement | null
    >
  >({
    offline: null,
    realtime: null,
    development: null,
    workflow: null,
  });

  const [
    tabIndicatorPosition,
    setTabIndicatorPosition,
  ] =
    useState<TabIndicatorPosition>(
      {
        left: 0,
        width: 0,
        ready: false,
      },
    );

  const displayedTab = useMemo(
    () =>
      CREATE_TABS.find(
        (item) =>
          item.key ===
          displayedType,
      ) || CREATE_TABS[0],
    [displayedType],
  );

  const fieldLabel =
    displayedType ===
    'development'
      ? '节点名称'
      : displayedType ===
          'workflow'
        ? '工作流名称'
        : '任务名称';

  const syncTabIndicator =
    useCallback(() => {
      const tabList =
        tabListRef.current;

      const activeButton =
        tabButtonRefs.current[
          activeType
        ];

      if (
        !tabList ||
        !activeButton
      ) {
        return;
      }

      const nextPosition: TabIndicatorPosition =
        {
          left:
            activeButton.offsetLeft,
          width:
            activeButton.offsetWidth,
          ready: true,
        };

      setTabIndicatorPosition(
        (current) => {
          const positionUnchanged =
            current.ready ===
              nextPosition.ready &&
            Math.abs(
              current.left -
                nextPosition.left,
            ) < 0.5 &&
            Math.abs(
              current.width -
                nextPosition.width,
            ) < 0.5;

          return positionUnchanged
            ? current
            : nextPosition;
        },
      );
    }, [activeType]);

  useLayoutEffect(() => {
    syncTabIndicator();
  }, [syncTabIndicator]);

  useEffect(() => {
    if (
      typeof window ===
      'undefined'
    ) {
      return undefined;
    }

    window.addEventListener(
      'resize',
      syncTabIndicator,
    );

    let resizeObserver:
      | ResizeObserver
      | undefined;

    if (
      typeof ResizeObserver !==
      'undefined'
    ) {
      resizeObserver =
        new ResizeObserver(
          syncTabIndicator,
        );

      if (tabListRef.current) {
        resizeObserver.observe(
          tabListRef.current,
        );
      }

      Object.values(
        tabButtonRefs.current,
      ).forEach((button) => {
        if (button) {
          resizeObserver?.observe(
            button,
          );
        }
      });
    }

    if (
      typeof document !==
        'undefined' &&
      'fonts' in document
    ) {
      void document.fonts.ready.then(
        syncTabIndicator,
      );
    }

    return () => {
      window.removeEventListener(
        'resize',
        syncTabIndicator,
      );

      resizeObserver?.disconnect();
    };
  }, [syncTabIndicator]);

  const beginPanelTransition =
    useCallback(
      (
        nextType: CreateType,
        fromType: CreateType,
      ) => {
        const currentIndex =
          CREATE_TABS.findIndex(
            (item) =>
              item.key === fromType,
          );

        const nextIndex =
          CREATE_TABS.findIndex(
            (item) =>
              item.key === nextType,
          );

        targetTypeRef.current =
          nextType;

        setMotionDirection(
          nextIndex >= currentIndex
            ? 'forward'
            : 'backward',
        );

        setMotionPhase('leaving');
      },
      [],
    );

  /**
   * 浏览器前进、后退或外部直接修改 query 时，
   * 也复用同一套动画。
   */
  useEffect(() => {
    if (
      handledActiveTypeRef.current ===
      activeType
    ) {
      return;
    }

    const previousType =
      handledActiveTypeRef.current;

    handledActiveTypeRef.current =
      activeType;

    beginPanelTransition(
      activeType,
      previousType,
    );
  }, [
    activeType,
    beginPanelTransition,
  ]);

  const switchType = (
    type: CreateType,
  ) => {
    if (type === activeType) {
      return;
    }

    beginPanelTransition(
      type,
      activeType,
    );

    handledActiveTypeRef.current =
      type;

    history.replace(
      `/create?type=${type}`,
    );
  };

  const handlePanelAnimationEnd = (
    event: ReactAnimationEvent<HTMLDivElement>,
  ) => {
    if (
      event.target !==
      event.currentTarget
    ) {
      return;
    }

    if (
      motionPhase === 'leaving'
    ) {
      /*
       * 说明栏和表单都已经进入离场尾帧。
       * 此时切换 displayedType，不会看到内容瞬间跳变。
       */
      form.resetFields();

      setDisplayedType(
        targetTypeRef.current,
      );

      setMotionPhase('entering');

      return;
    }

    if (
      motionPhase === 'entering'
    ) {
      setMotionPhase('idle');
    }
  };

  const submit = async () => {
    try {
      const values =
        await form.validateFields();

      const name =
        values.name.trim();

      setSubmitting(true);

      if (
        displayedType === 'offline'
      ) {
        const response =
          await quickCreateApi.offline(
            {
              jobName: name,
              mode: offlineMode,
            },
          );

        const id =
          unwrapApiResponse(
            response,
            '创建离线同步任务失败',
          );

        message.success(
          '任务草稿已创建，请继续完成同步配置',
        );

        history.push(
          offlineMode ===
            'GUIDE_MULTI'
            ? `/sync/batch-link-up/${encodeURIComponent(
                String(id),
              )}/config/multi?scene=create`
            : `/sync/batch-link-up/${encodeURIComponent(
                String(id),
              )}/config/single?scene=create`,
        );

        return;
      }

      if (
        displayedType ===
        'realtime'
      ) {
        const response =
          await quickCreateApi.realtime(
            {
              name,
            },
          );

        const id =
          unwrapApiResponse(
            response,
            '创建实时同步任务失败',
          );

        message.success(
          '任务草稿已创建，请继续完成实时同步配置',
        );

        history.push(
          `/sync/realtime/${encodeURIComponent(
            String(id),
          )}/detail?scene=create&editor=${realtimeEditorMode}`,
        );

        return;
      }

      if (
        displayedType ===
        'development'
      ) {
        const response =
          await createDevelopmentNode(
            {
              name,
              type: developmentType,
              projectId:
                currentProject?.id
                  ? String(
                      currentProject.id,
                    )
                  : undefined,
            },
          );

        const created =
          unwrapApiResponse(
            response,
            '创建数据开发节点失败',
          );

        message.success(
          '开发节点已创建，正在打开编辑器',
        );

        history.push(
          `/data-development?nodeId=${encodeURIComponent(
            String(created.id),
          )}&scene=create`,
        );

        return;
      }

      const created =
        await createWorkflowDefinition(
          {
            name,
          },
        );

      if (!created?.id) {
        throw new Error(
          '工作流创建成功但未返回 ID',
        );
      }

      message.success(
        '工作流草稿已创建，请继续完成任务编排',
      );

      history.push(
        `/workflow/definition/${encodeURIComponent(
          String(created.id),
        )}?scene=create`,
      );
    } catch (error: unknown) {
      const formError =
        error as {
          errorFields?: unknown[];
        };

      if (
        formError?.errorFields
      ) {
        return;
      }

      message.error(
        error instanceof Error
          ? error.message
          : '创建失败，请稍后重试',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const renderExtraOptions =
    () => {
      if (
        displayedType ===
        'offline'
      ) {
        return (
          <div className="mt-1">
            <div className="mb-2.5 text-[12px] font-medium text-[#555550]">
              同步方式
            </div>

            <ChoiceGrid
              value={offlineMode}
              options={
                OFFLINE_MODES
              }
              onChange={
                setOfflineMode
              }
            />
          </div>
        );
      }

      if (
        displayedType ===
        'realtime'
      ) {
        return (
          <div className="mt-1">
            <div className="mb-2.5 text-[12px] font-medium text-[#555550]">
              编辑方式
            </div>

            <ChoiceGrid
              value={
                realtimeEditorMode
              }
              options={
                REALTIME_EDITOR_MODES
              }
              onChange={
                setRealtimeEditorMode
              }
            />
          </div>
        );
      }

      if (
        displayedType ===
        'development'
      ) {
        return (
          <div className="mt-1">
            <div className="mb-2.5 text-[12px] font-medium text-[#555550]">
              节点类型
            </div>

            <ChoiceGrid
              compact
              columns={3}
              value={
                developmentType
              }
              options={
                DEVELOPMENT_TYPES
              }
              onChange={
                setDevelopmentType
              }
            />
          </div>
        );
      }

      return null;
    };

  /**
   * 说明栏和表单共用同一个运动类。
   *
   * 因此两块内容的离场方向、进入方向、时长和回摆节奏
   * 都能保持完全一致。
   */
  const contentMotionClassName =
    motionPhase === 'leaving'
      ? motionDirection ===
        'forward'
        ? 'yak-create-panel-leave-left'
        : 'yak-create-panel-leave-right'
      : motionPhase === 'entering'
        ? motionDirection ===
          'forward'
          ? 'yak-create-panel-enter-right'
          : 'yak-create-panel-enter-left'
        : '';

  return (
    <ConfigProvider
      theme={BRAND_THEME}
    >
      <style>
        {CREATE_PAGE_MOTION_STYLES}
      </style>

      <div
        className="min-h-[calc(100vh-64px)] bg-white text-[#161823]"
        style={{
          fontFamily: PAGE_FONT,
        }}
      >
        {/* 顶部 Tab */}
        <div
          className="border-b border-[#f1f1f2] bg-white pr-8"
          style={{
            marginLeft: 24,
          }}
        >
          <div
            ref={tabListRef}
            role="tablist"
            aria-label="快速创建类型"
            className="relative flex h-[66px] items-end gap-8"
          >
            {CREATE_TABS.map(
              (item) => {
                const active =
                  item.key ===
                  activeType;

                return (
                  <button
                    key={item.key}
                    ref={(button) => {
                      tabButtonRefs.current[
                        item.key
                      ] = button;
                    }}
                    id={`quick-create-tab-${item.key}`}
                    type="button"
                    role="tab"
                    aria-selected={
                      active
                    }
                    aria-controls="quick-create-panel"
                    tabIndex={
                      active ? 0 : -1
                    }
                    onClick={() =>
                      switchType(
                        item.key,
                      )
                    }
                    className={[
                      'relative z-10 flex h-[66px] items-end border-0 bg-transparent px-0 pb-[9px]',
                      'text-[18px] leading-6 tracking-0 text-[#161823]',
                      'transition-colors duration-150',
                      active
                        ? 'font-semibold'
                        : 'font-normal hover:text-black',
                    ].join(' ')}
                  >
                    {item.label}
                  </button>
                );
              },
            )}

            <span
              aria-hidden="true"
              className="yak-create-tab-indicator pointer-events-none absolute bottom-[-1px] left-0 z-20 h-[3px] rounded-[1px] bg-[#161823]"
              style={{
                width:
                  tabIndicatorPosition.width,
                opacity:
                  tabIndicatorPosition.ready
                    ? 1
                    : 0,
                transform: `translate3d(${tabIndicatorPosition.left}px, 0, 0)`,
              }}
            />
          </div>
        </div>

        {/* 与表单同步切换的顶部说明栏 */}
        <CreateGuide
          type={displayedType}
          motionClassName={
            contentMotionClassName
          }
          transitioning={
            motionPhase !== 'idle'
          }
        />

        {/* 中间表单区域 */}
        <main className="flex min-h-[600px] justify-center overflow-x-hidden bg-white px-6 py-12">
          <div className="w-full max-w-[780px]">
            <div
              id="quick-create-panel"
              role="tabpanel"
              aria-labelledby={`quick-create-tab-${activeType}`}
              aria-busy={
                motionPhase !== 'idle'
              }
              onAnimationEnd={
                handlePanelAnimationEnd
              }
              className={[
                'yak-create-panel-motion h-fit w-full rounded-[28px] bg-[#f9f9fa] px-7 py-8 sm:px-10 sm:py-10',
                contentMotionClassName,
                motionPhase !== 'idle'
                  ? 'pointer-events-none'
                  : '',
              ]
                .filter(Boolean)
                .join(' ')}
            >
              <h1 className="mb-8 mt-0 text-center text-[22px] font-semibold leading-8 tracking-[-0.2px] text-[#171717]">
                {
                  displayedTab.title
                }
              </h1>

              <Form
                key={displayedType}
                form={form}
                layout="vertical"
                requiredMark={false}
                onFinish={submit}
              >
                <Form.Item
                  className={
                    FORM_ITEM_CLASS_NAME
                  }
                  name="name"
                  rules={[
                    {
                      required: true,
                      whitespace: true,
                      message: (
                        <ValidationMessage>
                          {`请输入${fieldLabel}`}
                        </ValidationMessage>
                      ),
                    },
                    {
                      max:
                        displayedTab.maxLength,
                      message: (
                        <ValidationMessage>
                          {`${fieldLabel}不能超过 ${displayedTab.maxLength} 个字符`}
                        </ValidationMessage>
                      ),
                    },
                  ]}
                >
                  <FloatingInput
                    id="quick-create-name"
                    label={fieldLabel}
                    autoFocus
                    autoComplete="off"
                    maxLength={
                      displayedTab.maxLength
                    }
                  />
                </Form.Item>

                {renderExtraOptions()}

                <YakButton
                  block
                  effect="glass"
                  type="primary"
                  htmlType="submit"
                  loading={
                    submitting
                  }
                  className="!mt-8 !h-11 !rounded-full !border-[#171717] !bg-[#171717] !font-medium !text-white !shadow-none hover:!border-[#292929] hover:!bg-[#292929]"
                >
                  {
                    displayedTab.buttonText
                  }
                </YakButton>
              </Form>
            </div>
          </div>
        </main>
      </div>
    </ConfigProvider>
  );
}