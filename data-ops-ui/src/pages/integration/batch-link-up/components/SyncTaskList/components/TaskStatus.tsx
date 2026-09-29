import runningWebp from '@/assets/gif/running.webp';
import {
  YakButton,
  YakStatusIcon,
  type YakStatus,
} from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Popover, message } from 'antd';

interface TaskStatusProps {
  status?: string;
  errorMessage?: string;
}

interface StatusMeta {
  messageId: string;
  yakStatus: YakStatus;
  animated?: boolean;
}

const STATUS_META: Record<string, StatusMeta> = {
  IDLE: {
    messageId: 'pages.batchLinkUp.status.idle',
    yakStatus: 'pending',
  },
  CREATED: {
    messageId: 'pages.batchLinkUp.status.created',
    yakStatus: 'pending',
  },
  SUBMITTED: {
    messageId: 'pages.batchLinkUp.status.submitted',
    yakStatus: 'pending',
    animated: true,
  },
  QUEUED: {
    messageId: 'pages.batchLinkUp.status.queued',
    yakStatus: 'pending',
    animated: true,
  },
  RUNNING: {
    messageId: 'pages.batchLinkUp.status.running',
    yakStatus: 'running',
    animated: true,
  },
  SUCCEEDED: {
    messageId: 'pages.batchLinkUp.status.succeeded',
    yakStatus: 'success',
  },
  FAILED: {
    messageId: 'pages.batchLinkUp.status.failed',
    yakStatus: 'failed',
  },
  PAUSED: {
    messageId: 'pages.batchLinkUp.status.paused',
    yakStatus: 'paused',
  },
  CANCELED: {
    messageId: 'pages.batchLinkUp.status.canceled',
    yakStatus: 'canceled',
  },
  LOST: {
    messageId: 'pages.batchLinkUp.status.lost',
    yakStatus: 'warning',
  },
};

const STATUS_ALIASES: Record<string, string> = {
  FINISHED: 'SUCCEEDED',
  COMPLETED: 'SUCCEEDED',
  SUCCESS: 'SUCCEEDED',
  CANCELLED: 'CANCELED',
  STOPPED: 'CANCELED',
  PENDING: 'QUEUED',
  WAITING: 'QUEUED',
  NOT_STARTED: 'IDLE',
  NONE: 'IDLE',
};

const normalizeStatus = (value?: string) => {
  const normalized = String(value || '')
    .trim()
    .toUpperCase();

  return STATUS_ALIASES[normalized] || normalized || 'IDLE';
};

const TaskStatus = ({ status, errorMessage }: TaskStatusProps) => {
  const intl = useIntl();

  const normalized = normalizeStatus(status);
  const meta = STATUS_META[normalized];

  const label = meta
    ? intl.formatMessage({ id: meta.messageId })
    : normalized;

  const yakStatus: YakStatus = meta?.yakStatus || 'unknown';

  const isRunning = normalized === 'RUNNING';

const statusContent = isRunning ? (
  <span
    className="relative inline-flex h-[100px] min-w-[72px] items-center justify-center"
    data-offline-sync-status={normalized}
    title={label}
  >
    <span className="relative inline-flex">

      {/* 上层：真正清晰的 Running 插画 */}
      <img
        src={runningWebp}
        alt=""
        draggable={false}
        className="
          relative
          z-10
          block
          h-[88px]
          w-auto
          max-w-none
          select-none
          rounded-[14px]
          object-contain
        "
        style={{
          WebkitMaskImage:
            'radial-gradient(ellipse 88% 94% at 50% 50%, #000 58%, rgba(0,0,0,.96) 70%, rgba(0,0,0,.72) 82%, rgba(0,0,0,.28) 92%, transparent 100%)',
          maskImage:
            'radial-gradient(ellipse 88% 94% at 50% 50%, #000 58%, rgba(0,0,0,.96) 70%, rgba(0,0,0,.72) 82%, rgba(0,0,0,.28) 92%, transparent 100%)',
          WebkitMaskRepeat: 'no-repeat',
          maskRepeat: 'no-repeat',
          WebkitMaskSize: '100% 100%',
          maskSize: '100% 100%',
        }}
      />
    </span>
  </span>
) : (
  <span
    className="inline-flex min-w-[78px] items-center justify-center gap-1.5 whitespace-nowrap text-[12px] font-medium leading-5 text-[#475467]"
    data-offline-sync-status={normalized}
  >
    <YakStatusIcon
      status={yakStatus}
      size={17}
      animated={Boolean(meta?.animated)}
    />

    <span>{label}</span>
  </span>
);

  if (
    !errorMessage ||
    (normalized !== 'FAILED' && normalized !== 'LOST')
  ) {
    return statusContent;
  }

  const copyError = async () => {
    try {
      await navigator.clipboard.writeText(errorMessage);

      message.success(
        intl.formatMessage({
          id: 'pages.batchLinkUp.status.copyErrorSuccess',
        }),
      );
    } catch {
      message.error(
        intl.formatMessage({
          id: 'pages.batchLinkUp.status.copyErrorFailed',
        }),
      );
    }
  };

  return (
    <Popover
      placement="right"
      trigger="hover"
      content={
        <div className="w-[440px]">
          <div
            className="
              max-h-[240px]
              overflow-auto
              whitespace-pre-wrap
              break-words
              rounded-md
              bg-[#101828]
              p-3
              font-mono
              text-xs
              leading-5
              text-[#fda29b]
            "
          >
            {errorMessage}
          </div>

          <div className="mt-2 flex justify-end">
            <YakButton size="small" onClick={copyError}>
              {intl.formatMessage({
                id: 'pages.batchLinkUp.status.copyError',
              })}
            </YakButton>
          </div>
        </div>
      }
    >
      <span className="inline-flex cursor-help rounded-md transition-colors hover:bg-[#fff7f6]">
        {statusContent}
      </span>
    </Popover>
  );
};

export default TaskStatus;