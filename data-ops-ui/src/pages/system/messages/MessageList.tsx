import YakButton from "@/components/YakButton";
import YakTab from "@/components/YakTab";
import { useSecurityProject } from "@/contexts/SecurityProjectContext";
import {
  getMessageDetail,
  markMessageRead,
  notifyMessageCountChanged,
  pageMessages,
  safeMessageActionPath,
  type MessageDetail,
  type MessageLevel,
  type MessageStatus,
  type SecurityMessage,
} from "@/services/security/messages";
import { satisfiesPermissionRequirement } from "@/utils/security/permission";
import { history, useModel } from "@umijs/max";
import {
  Alert,
  DatePicker,
  Drawer,
  Empty,
  message,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from "antd";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import { ChevronLeft } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const LATEST_MESSAGE_LIMIT = 100;

const MESSAGE_TYPE_LABELS: Record<string, string> = {
  SYSTEM: "系统",
  SECURITY: "安全",
  TASK: "任务",
  QUALITY: "质量",
};

const MESSAGE_TYPE_TABS = [
  {
    key: "ALL",
    label: "全部",
  },
  ...Object.entries(MESSAGE_TYPE_LABELS).map(([key, label]) => ({
    key,
    label,
  })),
];

const MESSAGE_LEVEL_LABELS: Record<MessageLevel, string> = {
  INFO: "信息",
  SUCCESS: "成功",
  WARNING: "警告",
  ERROR: "错误",
};

const messageTypeLabel = (value?: string) =>
  (value && MESSAGE_TYPE_LABELS[value]) || value || "消息";

const formatMessageTime = (value?: string | number | null) => {
  if (value === undefined || value === null || value === "") return "-";

  const parsed = dayjs(value);

  return parsed.isValid()
    ? parsed.format("YYYY-MM-DD HH:mm:ss")
    : String(value);
};

const messageTimestamp = (value?: string | number | null) => {
  if (value === undefined || value === null || value === "") return 0;

  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.valueOf() : 0;
};

const operationLogIdOf = (item?: SecurityMessage) =>
  item?.operationLogId ?? item?.oplogId;

type MessageDateRange = [Dayjs | null, Dayjs | null] | null;

export default function MessageList() {
  const { initialState } = useModel("@@initialState");
  const { projects } = useSecurityProject();

  const canReadLogs = satisfiesPermissionRequirement(
    initialState?.currentUser?.permissionCodes,
    {
      mode: "one",
      permission: "security:operation-log:read",
    }
  );

  const [items, setItems] = useState<SecurityMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<MessageDetail>();
  const [status, setStatus] = useState<MessageStatus>();
  const [type, setType] = useState<string>();
  const [dateRange, setDateRange] = useState<MessageDateRange>(null);

  const requestSequence = useRef(0);

  const projectNameById = useMemo(
    () =>
      new Map(
        projects.map((project) => [String(project.id), project.projectName])
      ),
    [projects]
  );

  const ownershipLabel = useCallback(
    (item?: SecurityMessage) => {
      if (item?.projectId === undefined || item.projectId === null) {
        return "系统";
      }

      const projectName = projectNameById.get(String(item.projectId));

      return projectName ? `项目 · ${projectName}` : `项目 #${item.projectId}`;
    },
    [projectNameById]
  );

  const loadMessages = useCallback(async () => {
    const sequence = ++requestSequence.current;

    setLoading(true);

    try {
      const result = await pageMessages({
        pageNum: 1,
        pageSize: LATEST_MESSAGE_LIMIT,
        status,
        type,
        startTime: dateRange?.[0]?.startOf("day").valueOf(),
        endTime: dateRange?.[1]?.endOf("day").valueOf(),
      });

      if (sequence !== requestSequence.current) return;

      const records = [...(result.records ?? [])]
        .sort(
          (left, right) =>
            messageTimestamp(right.createTime) -
            messageTimestamp(left.createTime)
        )
        .slice(0, LATEST_MESSAGE_LIMIT);

      setItems(records);
    } catch {
      if (sequence === requestSequence.current) {
        message.error("消息加载失败");
      }
    } finally {
      if (sequence === requestSequence.current) {
        setLoading(false);
      }
    }
  }, [dateRange, status, type]);

  useEffect(() => {
    void loadMessages();
  }, [loadMessages]);

  const openDetail = async (row: SecurityMessage) => {
    setDetail({ ...row });
    setDetailLoading(true);

    try {
      if (row.status === "UNREAD") {
        await markMessageRead(row.id);

        setItems((current) =>
          current.map((item) =>
            item.id === row.id
              ? {
                  ...item,
                  status: "READ",
                }
              : item
          )
        );

        notifyMessageCountChanged();
      }

      const nextDetail = await getMessageDetail(row.id);

      setDetail(
        row.status === "UNREAD"
          ? {
              ...nextDetail,
              status: "READ",
            }
          : nextDetail
      );
    } catch {
      message.error("消息详情加载失败");
    } finally {
      setDetailLoading(false);
    }
  };

  const handleTypeChange = (key: string) => {
    setType(key === "ALL" ? undefined : key);
  };

  const detailActionPath = safeMessageActionPath(detail?.actionPath);
  const detailOperationLogId = operationLogIdOf(detail);

  return (
    <div className="min-h-[calc(100vh-64px)] bg-white px-10 pb-10 pt-7 max-[860px]:px-5 max-[860px]:pt-5">
      <div className="flex h-5 items-center gap-2 text-[12px]">
        <button
          type="button"
          className="
      inline-flex h-5 cursor-pointer items-center gap-0.5
      border-0 bg-transparent p-0
      text-[#667085] transition-colors
      hover:text-[#161823]
    "
          onClick={() => history.push("/home")}
        >
          <ChevronLeft size={14} strokeWidth={1.8} className="shrink-0" />
          <span className="leading-none">首页</span>
        </button>

        <span className="inline-flex h-5 items-center text-[#d0d5dd]">/</span>

        <span className="inline-flex h-5 items-center font-medium text-[#161823]">
          通知
        </span>
      </div>

      <h1 className="mb-0 mt-5 text-[26px] font-semibold leading-9 tracking-[-0.45px] text-[#161823]">
        通知
      </h1>

      <div className="mt-7">
        <YakTab
          activeKey={type || "ALL"}
          items={MESSAGE_TYPE_TABS}
          onChange={handleTypeChange}
          tabBarExtraContent={{
            right: (
              <div className="flex items-center gap-2 pb-2 max-[860px]:hidden">
                <Select<MessageStatus>
                  allowClear
                  variant="filled"
                  className="w-[120px]"
                  placeholder="全部状态"
                  value={status}
                  options={[
                    {
                      label: "未读",
                      value: "UNREAD",
                    },
                    {
                      label: "已读",
                      value: "READ",
                    },
                  ]}
                  onChange={(value) => setStatus(value)}
                />

                <DatePicker.RangePicker
                  allowClear
                  variant="filled"
                  className="w-[260px]"
                  value={dateRange}
                  placeholder={["开始日期", "结束日期"]}
                  onChange={(value) => setDateRange(value as MessageDateRange)}
                />
              </div>
            ),
          }}
        />

        <div className="hidden gap-2 border-b border-[#eceef2] pb-3 max-[860px]:flex">
          <Select<MessageStatus>
            allowClear
            variant="filled"
            className="w-[120px]"
            placeholder="全部状态"
            value={status}
            options={[
              {
                label: "未读",
                value: "UNREAD",
              },
              {
                label: "已读",
                value: "READ",
              },
            ]}
            onChange={(value) => setStatus(value)}
          />

          <DatePicker.RangePicker
            allowClear
            variant="filled"
            className="min-w-0 flex-1"
            value={dateRange}
            placeholder={["开始日期", "结束日期"]}
            onChange={(value) => setDateRange(value as MessageDateRange)}
          />
        </div>
      </div>

      <Spin spinning={loading}>
        <div className="min-h-[420px] bg-white">
          {!loading && items.length === 0 ? (
            <div className="flex min-h-[420px] items-center justify-center">
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无通知"
              />
            </div>
          ) : (
            items.map((item) => {
              const unread = item.status === "UNREAD";

              return (
                <button
                  key={item.id}
                  type="button"
                  className="group block w-full border-0 border-b border-solid border-[rgba(22,24,35,0.08)] bg-white px-0 py-3 text-left transition-colors duration-150 hover:bg-[#fafafa]"
                  onClick={() => void openDetail(item)}
                >
                  <div className="flex min-w-0 items-start justify-between gap-8 px-0">
                    <div className="flex min-w-0 items-center gap-2.5">
                      <span
                        aria-hidden="true"
                        className={[
                          "h-1.5 w-1.5 shrink-0 rounded-full transition-colors",
                          unread ? "bg-[#fe2c55]" : "bg-transparent",
                        ].join(" ")}
                      />

                      <div
                        className={[
                          "min-w-0 truncate text-[15px] leading-6 text-[#161823]",
                          unread ? "font-semibold" : "font-medium",
                        ].join(" ")}
                      >
                        {item.title}
                      </div>
                    </div>

                    <time className="shrink-0 pt-0.5 text-[12px] leading-5 text-[rgba(22,24,35,0.46)]">
                      {formatMessageTime(item.createTime)}
                    </time>
                  </div>

                  <div className="mt-2 pl-4 text-[14px] leading-7 text-[rgba(22,24,35,0.68)]">
                    <p className="m-0 line-clamp-2">
                      {item.summary || "暂无摘要"}
                    </p>
                  </div>
                </button>
              );
            })
          )}
        </div>
      </Spin>

      <Drawer
        title={detail?.title ?? "通知详情"}
        width={680}
        open={Boolean(detail)}
        footer={null}
        destroyOnClose
        onClose={() => setDetail(undefined)}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <Space direction="vertical" size="middle" className="w-full pt-2">
              <Space wrap>
                <Tag>{messageTypeLabel(detail.type)}</Tag>

                {detail.level ? (
                  <Tag>{MESSAGE_LEVEL_LABELS[detail.level]}</Tag>
                ) : null}

                <Tag>{ownershipLabel(detail)}</Tag>

                <Typography.Text type="secondary">
                  {formatMessageTime(detail.createTime)}
                </Typography.Text>
              </Space>

              <Typography.Paragraph className="whitespace-pre-wrap break-words !text-[14px] !leading-7">
                {detail.content ?? detail.summary ?? "-"}
              </Typography.Paragraph>

              {detailActionPath ? (
                <YakButton
                  type="primary"
                  onClick={() => history.push(detailActionPath)}
                >
                  前往处理
                </YakButton>
              ) : null}

              {detailOperationLogId &&
                (canReadLogs ? (
                  <YakButton
                    onClick={() =>
                      history.push(
                        `/system/oplogs?messageLogId=${encodeURIComponent(
                          detailOperationLogId
                        )}`
                      )
                    }
                  >
                    查看关联操作日志
                  </YakButton>
                ) : (
                  <Alert
                    type="warning"
                    showIcon
                    message="关联日志不可访问"
                    description="当前身份没有操作日志查看权限。"
                  />
                ))}
            </Space>
          ) : null}
        </Spin>
      </Drawer>
    </div>
  );
}
