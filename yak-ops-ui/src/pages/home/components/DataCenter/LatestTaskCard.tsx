import YakOpsEmpty from "@/components/YakOpsEmpty";
import type { HomeLatestTask } from "@/services/home";
import { history, useIntl } from "@umijs/max";
import { ChevronRight, Layers2 } from "lucide-react";

import {
  formatDuration,
  statusKey,
  statusLabel,
  taskTypeLabel,
  type HomeStatusKey,
  type HomeTaskTypeKey,
} from "../../utils/homeDataCenter";

interface LatestTaskCardProps {
  task?: HomeLatestTask;
  loading: boolean;
  failed: boolean;
}

interface TaskMetricRowProps {
  label: string;
  value: React.ReactNode;
  danger?: boolean;
}

function TaskMetricRow({ label, value, danger = false }: TaskMetricRowProps) {
  return (
    <div className="flex h-6 items-center justify-between">
      <span className="text-[11px] font-medium text-white/90">{label}</span>
      <span
        className={`max-w-[84px] truncate text-[11px] font-semibold ${
          danger ? "text-[#ff8993]" : "text-white"
        }`}
        title={typeof value === "string" ? value : undefined}
      >
        {value}
      </span>
    </div>
  );
}

function LatestTaskContent({ task }: { task: HomeLatestTask }) {
  const intl = useIntl();

  const resolveStatus = (key: HomeStatusKey) =>
    intl.formatMessage({
      id: `pages.home.dataCenter.status.${key}`,
    });

  const resolveTaskType = (key: HomeTaskTypeKey) =>
    intl.formatMessage({
      id: `pages.home.dataCenter.taskType.${key}`,
    });

  const status = statusLabel(task.status, resolveStatus);

  const handleClick = () => {
    if (!task.detailPath) return;
    history.push(task.detailPath);
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      title={task.taskName}
      className="
        group
        relative
        h-[262px]
        w-full
        overflow-hidden
        rounded-[8px]
        bg-[#34383f]
        text-left
        outline-none
        transition-all
        duration-300
        hover:shadow-[0_8px_24px_rgba(31,35,41,0.16)]
        focus-visible:ring-2
        focus-visible:ring-[#d9e4ff]
        lg:w-[193px]
      "
    >
      {/* Yak 背景 */}
      <img
        src="/image/image.png"
        alt=""
        draggable={false}
        className="
          pointer-events-none
          absolute
          inset-0
          h-full
          w-full
          object-cover
          object-[58%_50%]
          transition-transform
          duration-500
          ease-out
          group-hover:scale-[1.035]
          group-focus:scale-[1.035]
        "
      />

      {/* 整体稍微压暗，避免背景过亮 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          inset-0
          bg-black/[0.05]
        "
      />

      {/* 顶部文字保护层 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          inset-x-0
          top-0
          h-[86px]
          bg-gradient-to-b
          from-black/45
          via-black/20
          to-transparent
        "
      />

      {/* 左侧文字区域再稍微压暗一点 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          left-0
          top-0
          h-[110px]
          w-[145px]
          bg-[radial-gradient(circle_at_0_0,rgba(0,0,0,0.30),transparent_72%)]
        "
      />

      {/* 顶部任务信息 */}
      <div className="absolute inset-x-0 top-0 z-20 flex items-start justify-between px-3 pt-3">
        <div className="min-w-0 pr-2 text-white">
          <div className="truncate text-[11px] font-semibold leading-[16px]">
            {taskTypeLabel(task.taskType, resolveTaskType)}
          </div>

          <div className="mt-[1px] text-[12px] font-semibold leading-[16px]">
            {formatDuration(task.durationMs)}
          </div>

          <div
            className="
              mt-[1px]
              max-w-[118px]
              truncate
              text-[10px]
              font-medium
              leading-[14px]
              text-white/90
            "
            title={task.taskName}
          >
            {task.taskName}
          </div>
        </div>

        <div
          className="
            mt-[1px]
            flex
            h-[20px]
            w-[20px]
            shrink-0
            items-center
            justify-center
            text-white
          "
        >
          <Layers2 size={15} strokeWidth={2.4} />
        </div>
      </div>

      {/* 底部数据区域 */}
      <div
        className="
          absolute
          inset-x-0
          bottom-0
          z-10
          h-[72px]
          overflow-hidden
          transition-[height]
          duration-300
          ease-out
          group-hover:h-[150px]
          group-focus:h-[150px]
        "
      >
        {/* 底部深色渐变 */}
        <div
          aria-hidden="true"
          className="
            pointer-events-none
            absolute
            inset-0
            bg-[linear-gradient(180deg,rgba(20,22,26,0)_0%,rgba(20,22,26,0.48)_24%,rgba(20,22,26,0.90)_100%)]
          "
        />

        <div className="relative z-10 px-3 pt-[10px]">
          <TaskMetricRow
            label={intl.formatMessage({
              id: "pages.home.dataCenter.latest.runCount",
            })}
            value={task.runCount}
          />

          <TaskMetricRow
            label={intl.formatMessage({
              id: "pages.home.dataCenter.latest.exceptionCount",
            })}
            value={task.exceptionCount}
            danger={task.exceptionCount > 0}
          />

          <div
            className="
              opacity-0
              transition-opacity
              duration-200
              group-hover:opacity-100
              group-focus:opacity-100
            "
          >
            <TaskMetricRow
              label={intl.formatMessage({
                id: "pages.home.dataCenter.latest.taskStatus",
              })}
              value={status}
              danger={statusKey(task.status) === "failed"}
            />

            <TaskMetricRow
              label={intl.formatMessage({
                id: "pages.home.dataCenter.latest.taskId",
              })}
              value={String(task.taskId)}
            />

            <div
              className="
                mt-[4px]
                flex
                h-[24px]
                items-center
                gap-[2px]
                text-[11px]
                font-semibold
                text-white
              "
            >
              <span>
                {intl.formatMessage({
                  id: "pages.home.dataCenter.latest.detail",
                })}
              </span>

              <ChevronRight
                size={14}
                strokeWidth={2}
                className="
                  transition-transform
                  duration-200
                  group-hover:translate-x-[2px]
                "
              />
            </div>
          </div>
        </div>
      </div>

      {/* 极轻的边缘 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          inset-0
          z-30
          rounded-[8px]
          ring-1
          ring-inset
          ring-black/[0.06]
        "
      />
    </button>
  );
}

function TaskLoadingCard() {
  const intl = useIntl();
  return (
    <div className="flex h-[262px] w-full items-center justify-center overflow-hidden rounded-[8px] bg-[#f4f5f6] lg:w-[176px]">
      <div className="flex flex-col items-center gap-2">
        <div className="h-5 w-5 animate-spin rounded-full border-2 border-[#d9dce1] border-t-[#8b8f97]" />
        <span className="text-[11px] text-[#8b8f97]">
          {intl.formatMessage({ id: "pages.home.dataCenter.latest.loading" })}
        </span>
      </div>
    </div>
  );
}

function TaskFailedCard() {
  const intl = useIntl();
  return (
    <div className="flex h-[240px] w-full items-center justify-center rounded-[8px] bg-[#f7f8fa] px-4 text-center lg:w-[176px]">
      <span className="text-[11px] text-[#9ca0a8]">
        {intl.formatMessage({ id: "pages.home.dataCenter.latest.failed" })}
      </span>
    </div>
  );
}

function TaskEmptyCard() {
  const intl = useIntl();
  return (
    <div className="flex h-[240px] w-full items-center justify-center overflow-hidden rounded-[8px] bg-[#f7f8fa] lg:w-[176px]">
      <YakOpsEmpty
        width={136}
        height={100}
        title={intl.formatMessage({ id: "pages.home.dataCenter.latest.empty" })}
        showCaption
      />
    </div>
  );
}

export function LatestTaskCard({ task, loading, failed }: LatestTaskCardProps) {
  const intl = useIntl();
  return (
    <aside className="w-full shrink-0 self-start lg:w-[218px] lg:border-r lg:border-[#eef0f2] lg:pr-[23px]">
      <div className="mb-2 flex h-5 items-center">
        <span className="text-[13px] font-semibold leading-5 text-[#272a31]">
          {intl.formatMessage({ id: "pages.home.dataCenter.latest.title" })}
        </span>
      </div>
      {task ? (
        <LatestTaskContent task={task} />
      ) : loading ? (
        <TaskLoadingCard />
      ) : failed ? (
        <TaskFailedCard />
      ) : (
        <TaskEmptyCard />
      )}
    </aside>
  );
}
