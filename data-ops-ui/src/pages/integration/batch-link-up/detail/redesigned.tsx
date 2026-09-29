import YakTab from '@/components/YakTab';
import {
  ArrowLeftOutlined,
  CopyOutlined,
  FileTextOutlined,
  ReloadOutlined,
  SyncOutlined,
  TableOutlined,
} from "@ant-design/icons";
import { history, useLocation, useParams } from "@umijs/max";
import {
  Button,
  ConfigProvider,
  Empty,
  message,
  Select,
  Spin,
  Table,
  Tag,
  Tooltip,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { API_SUCCESS_CODE } from "@/services/http/response";
import { BRAND_THEME } from "@/styles/brand";

import {
  batchJobInstanceApi,
  linkupJobDefinitionApi,
  linkupJobInstanceApi,
  type OfflineJobDefinitionVO,
} from "../api";

type InstanceRecord = Record<string, any>;
type DetailTabKey = "overview" | "log" | "sync" | "config" | "structure";

interface TableMetricRecord extends Record<string, any> {
  __key: string;
}

interface SqlRecord {
  key: string;
  title: string;
  tableName?: string;
  sql: string;
}

const RUNNING_STATUS = new Set([
  "INITIALIZING",
  "CREATED",
  "SUBMITTED",
  "QUEUED",
  "PENDING",
  "SCHEDULED",
  "RUNNING",
  "FAILING",
  "CANCELING",
]);

const SUCCESS_STATUS = new Set([
  "FINISHED",
  "COMPLETED",
  "SUCCESS",
  "SUCCEEDED",
]);

const FAILED_STATUS = new Set([
  "FAILED",
  "ERROR",
  "CANCELED",
  "CANCELLED",
  "KILLED",
  "STOPPED",
]);

const firstValue = <T,>(...values: T[]): T | undefined =>
  values.find(
    (value) => value !== undefined && value !== null && String(value) !== ""
  );

const normalizeStatus = (value?: unknown) =>
  String(value || "UNKNOWN")
    .trim()
    .toUpperCase();

const getInstanceStatus = (record?: InstanceRecord | null) =>
  normalizeStatus(firstValue(record?.jobStatus, record?.status));

const statusMeta = (status?: unknown) => {
  const normalized = normalizeStatus(status);

  if (RUNNING_STATUS.has(normalized)) {
    return { label: "运行中", color: "processing" as const };
  }
  if (SUCCESS_STATUS.has(normalized)) {
    return { label: "已完成", color: "success" as const };
  }
  if (FAILED_STATUS.has(normalized)) {
    return {
      label: normalized === "STOPPED" ? "已停止" : "失败",
      color: "error" as const,
    };
  }
  if (normalized === "PAUSED") {
    return { label: "已暂停", color: "warning" as const };
  }
  return {
    label: normalized === "UNKNOWN" ? "未知" : normalized,
    color: "default" as const,
  };
};

const toNumber = (value: unknown) => {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
};

const formatNumber = (value: unknown) => toNumber(value).toLocaleString();

const formatDateTime = (value?: unknown) => {
  if (!value) return "-";
  const parsed = dayjs(String(value));
  return parsed.isValid()
    ? parsed.format("YYYY-MM-DD HH:mm:ss")
    : String(value);
};

const formatDuration = (value?: unknown) => {
  const milliseconds = toNumber(value);
  if (!milliseconds) return "-";
  if (milliseconds < 1000) return `${milliseconds} ms`;

  const seconds = Math.floor(milliseconds / 1000);
  if (seconds < 60) return `${seconds} 秒`;

  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  if (minutes < 60) return `${minutes} 分 ${restSeconds} 秒`;

  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  return `${hours} 小时 ${restMinutes} 分`;
};

const formatBytes = (value?: unknown) => {
  const bytes = toNumber(value);
  if (!bytes) return "0 B";

  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1
  );
  const result = bytes / 1024 ** index;
  return `${result >= 100 ? result.toFixed(0) : result.toFixed(2)} ${
    units[index]
  }`;
};

const normalizePayload = (response: any) => response?.data ?? response;

const normalizeInstanceList = (response: any): InstanceRecord[] => {
  const data = normalizePayload(response);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.bizData)) return data.bizData;
  if (Array.isArray(data?.records)) return data.records;
  if (Array.isArray(data?.list)) return data.list;
  return [];
};

const normalizeTableMetrics = (response: any): TableMetricRecord[] => {
  const data = normalizePayload(response);
  const list = Array.isArray(data)
    ? data
    : Array.isArray(data?.bizData)
    ? data.bizData
    : Array.isArray(data?.records)
    ? data.records
    : Array.isArray(data?.list)
    ? data.list
    : [];

  return list.map((item: InstanceRecord, index: number) => ({
    ...item,
    __key: String(
      firstValue(
        item?.id,
        item?.tableId,
        `${firstValue(
          item?.sourceTable,
          item?.sourceTableName,
          "source"
        )}-${firstValue(
          item?.sinkTable,
          item?.targetTable,
          item?.sinkTableName,
          "sink"
        )}-${index}`
      )
    ),
  }));
};

const stringifyConfig = (value: unknown) => {
  if (value === undefined || value === null || value === "") return "";
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
};

const formatLogContent = (response: any) => {
  const data = normalizePayload(response);
  if (!data) return "";
  if (typeof data === "string") return data;
  if (Array.isArray(data?.logs)) return formatLogContent(data.logs);

  if (Array.isArray(data)) {
    return data
      .map((item) => {
        if (typeof item === "string") return item;
        const header = [
          item?.node ? `# Node: ${item.node}` : "",
          item?.logName ? `# File: ${item.logName}` : "",
          item?.logLink ? `# Link: ${item.logLink}` : "",
        ]
          .filter(Boolean)
          .join("\n");
        const content = firstValue(
          item?.content,
          item?.logContent,
          item?.log,
          item?.message,
          item?.data
        );
        return [
          header,
          content ? String(content) : JSON.stringify(item, null, 2),
        ]
          .filter(Boolean)
          .join("\n\n");
      })
      .filter(Boolean)
      .join("\n\n");
  }

  const content = firstValue(
    data?.content,
    data?.logContent,
    data?.log,
    data?.message
  );
  return content ? String(content) : JSON.stringify(data, null, 2);
};

const copyText = async (value: unknown, successText = "已复制") => {
  const text = String(value ?? "");
  if (!text) return;

  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
    }
    message.success(successText);
  } catch {
    message.error("复制失败，请手动复制");
  }
};

const MetricTile = ({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
}) => (
  <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
    <div className="text-[12px] leading-4 text-[#7c828c]">{label}</div>
    <div className="mt-2 text-[20px] font-semibold leading-7 tracking-[-0.02em] text-[#161823]">
      {value}
    </div>
    {hint ? (
      <div className="mt-1 text-[11px] text-[#9aa0aa]">{hint}</div>
    ) : null}
  </div>
);

const SectionCard = ({
  title,
  extra,
  children,
  className = "",
}: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section className={`min-w-0 rounded-lg  bg-white ${className}`}>
    <div className="flex min-h-[52px] items-center justify-between gap-4  px-5">
      <div className="text-[15px] font-semibold text-[#161823]">{title}</div>
      {extra}
    </div>
    {children}
  </section>
);

export default function BatchLinkUpExecutionDetailPage() {
  const routeParams = useParams<{ id?: string }>();
  const location = useLocation();
  const taskId = routeParams.id ? decodeURIComponent(routeParams.id) : "";

  const queryParams = useMemo(
    () => new URLSearchParams(location.search),
    [location.search]
  );
  const requestedInstanceId = queryParams.get("instanceId") || "";
  const requestedTab = queryParams.get("tab");
  const initialTab: DetailTabKey =
    requestedTab === "log" ||
    requestedTab === "sync" ||
    requestedTab === "config" ||
    requestedTab === "structure"
      ? requestedTab
      : "overview";

  const [definition, setDefinition] = useState<OfflineJobDefinitionVO | null>(
    null
  );
  const [instances, setInstances] = useState<InstanceRecord[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState("");
  const [instanceDetail, setInstanceDetail] = useState<InstanceRecord | null>(
    null
  );
  const [tableMetrics, setTableMetrics] = useState<TableMetricRecord[]>([]);
  const [logContent, setLogContent] = useState("");

  const [pageLoading, setPageLoading] = useState(true);
  const [instanceLoading, setInstanceLoading] = useState(false);
  const [logLoading, setLogLoading] = useState(false);
  const [metricsLoading, setMetricsLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<DetailTabKey>(initialTab);

  const updateRouteState = useCallback(
    (instanceId?: string, tab?: DetailTabKey) => {
      if (!taskId) return;
      const params = new URLSearchParams();
      if (instanceId) params.set("instanceId", instanceId);
      if (tab && tab !== "log") params.set("tab", tab);
      const search = params.toString();
      history.replace(
        `/sync/batch-link-up/${encodeURIComponent(taskId)}/detail${
          search ? `?${search}` : ""
        }`
      );
    },
    [taskId]
  );

  const loadPage = useCallback(async () => {
    if (!taskId) return;

    setPageLoading(true);
    try {
      const [definitionResponse, instanceResponse] = await Promise.all([
        linkupJobDefinitionApi.selectById(taskId),
        linkupJobInstanceApi.page({
          pageNum: 1,
          pageSize: 100,
          jobDefinitionId: taskId,
        }),
      ]);

      if (
        definitionResponse?.code !== API_SUCCESS_CODE ||
        !definitionResponse?.data
      ) {
        throw new Error(definitionResponse?.message || "获取离线同步任务失败");
      }

      setDefinition(definitionResponse.data);
      const nextInstances = normalizeInstanceList(instanceResponse);
      setInstances(nextInstances);

      const nextSelectedId = String(
        requestedInstanceId ||
          selectedInstanceId ||
          firstValue(nextInstances[0]?.id, nextInstances[0]?.instanceId) ||
          ""
      );
      setSelectedInstanceId(nextSelectedId);

      if (nextSelectedId && nextSelectedId !== requestedInstanceId) {
        updateRouteState(nextSelectedId, activeTab);
      }
    } catch (error: any) {
      message.error(error?.message || "获取离线同步详情失败");
      setDefinition(null);
      setInstances([]);
      setSelectedInstanceId("");
    } finally {
      setPageLoading(false);
    }
  }, [
    activeTab,
    requestedInstanceId,
    selectedInstanceId,
    taskId,
    updateRouteState,
  ]);

  useEffect(() => {
    if (!taskId) {
      history.replace("/sync/batch-link-up");
      return;
    }
    void loadPage();
    // 仅在任务 ID 变化时加载任务和实例，实例切换在前端完成。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId]);

  useEffect(() => {
    if (requestedInstanceId && requestedInstanceId !== selectedInstanceId) {
      setSelectedInstanceId(requestedInstanceId);
    }
  }, [requestedInstanceId, selectedInstanceId]);

  useEffect(() => {
    if (requestedTab === "metrics" || requestedTab === "overview") {
      setActiveTab("overview");
      return;
    }
    if (
      requestedTab === "log" ||
      requestedTab === "sync" ||
      requestedTab === "config" ||
      requestedTab === "structure"
    ) {
      setActiveTab(requestedTab);
    }
  }, [requestedTab]);

  const loadInstanceDetail = useCallback(async () => {
    if (!selectedInstanceId) {
      setInstanceDetail(null);
      return;
    }

    setInstanceLoading(true);
    try {
      const response = await linkupJobInstanceApi.selectById(
        selectedInstanceId
      );
      if (response?.code === API_SUCCESS_CODE && response?.data) {
        const detail = response.data;
        setInstanceDetail(detail);
        setInstances((previous) => {
          const detailId = String(
            firstValue(detail?.id, detail?.instanceId) || ""
          );
          if (!detailId) return previous;
          const exists = previous.some(
            (item) =>
              String(firstValue(item?.id, item?.instanceId) || "") === detailId
          );
          return exists ? previous : [detail, ...previous];
        });
        return;
      }

      const fallback = instances.find(
        (item) =>
          String(firstValue(item?.id, item?.instanceId)) === selectedInstanceId
      );
      setInstanceDetail(fallback || null);
    } catch {
      const fallback = instances.find(
        (item) =>
          String(firstValue(item?.id, item?.instanceId)) === selectedInstanceId
      );
      setInstanceDetail(fallback || null);
    } finally {
      setInstanceLoading(false);
    }
  }, [instances, selectedInstanceId]);

  useEffect(() => {
    void loadInstanceDetail();
  }, [loadInstanceDetail]);

  const loadLog = useCallback(async () => {
    if (!selectedInstanceId) {
      setLogContent("");
      return;
    }

    setLogLoading(true);
    try {
      const response = await linkupJobInstanceApi.getLog(selectedInstanceId);
      if (response?.code !== API_SUCCESS_CODE) {
        setLogContent(response?.message || "日志加载失败");
        return;
      }
      setLogContent(formatLogContent(response) || "当前实例暂无运行日志");
    } catch (error: any) {
      setLogContent(error?.message || "日志加载失败");
    } finally {
      setLogLoading(false);
    }
  }, [selectedInstanceId]);

  useEffect(() => {
    if (activeTab === "log") void loadLog();
  }, [activeTab, loadLog]);

  const loadTableMetrics = useCallback(async () => {
    if (!selectedInstanceId) {
      setTableMetrics([]);
      return;
    }

    setMetricsLoading(true);
    try {
      const response = await batchJobInstanceApi.tableMetrics(
        selectedInstanceId
      );
      setTableMetrics(normalizeTableMetrics(response));
    } catch {
      setTableMetrics([]);
    } finally {
      setMetricsLoading(false);
    }
  }, [selectedInstanceId]);

  useEffect(() => {
    void loadTableMetrics();
  }, [loadTableMetrics]);

  const handleSelectInstance = (id: string) => {
    if (!id || id === selectedInstanceId) return;
    const selected = instances.find(
      (item) => String(firstValue(item?.id, item?.instanceId) || "") === id
    );
    setSelectedInstanceId(id);
    setInstanceDetail(selected || null);
    setLogContent("");
    setTableMetrics([]);
    updateRouteState(id, activeTab);
  };

  const handleTabChange = (key: string) => {
    const nextKey = key as DetailTabKey;
    setActiveTab(nextKey);
    updateRouteState(selectedInstanceId, nextKey);
  };

  const currentInstance = useMemo(() => {
    if (instanceDetail) return instanceDetail;
    return (
      instances.find(
        (item) =>
          String(firstValue(item?.id, item?.instanceId)) === selectedInstanceId
      ) || null
    );
  }, [instanceDetail, instances, selectedInstanceId]);

  const mergedInstance = useMemo<InstanceRecord>(
    () => ({
      ...((definition || {}) as InstanceRecord),
      ...(currentInstance || {}),
    }),
    [currentInstance, definition]
  );

  const sourceType = firstValue(
    mergedInstance?.sourceType,
    definition?.sourceType
  );
  const sinkType = firstValue(mergedInstance?.sinkType, definition?.sinkType);
  const sourceTable = firstValue(
    mergedInstance?.sourceTable,
    mergedInstance?.sourceTableName,
    definition?.sourceTable
  );
  const sinkTable = firstValue(
    mergedInstance?.sinkTable,
    mergedInstance?.targetTable,
    mergedInstance?.sinkTableName,
    definition?.sinkTable
  );

  const readRows = firstValue(
    mergedInstance?.readRowCount,
    mergedInstance?.sourceRecordCount,
    0
  );
  const writeRows = firstValue(
    mergedInstance?.writeRowCount,
    mergedInstance?.sinkSuccessRecordCount,
    0
  );
  const durationMillis = firstValue(
    mergedInstance?.durationMillis,
    mergedInstance?.duration,
    0
  );
  const qps = firstValue(
    mergedInstance?.qps,
    mergedInstance?.writeQps,
    mergedInstance?.readQps,
    0
  );

  const runtimeConfig = stringifyConfig(
    firstValue(
      currentInstance?.runtimeConfig,
      currentInstance?.jobConfig,
      currentInstance?.config,
      definition?.jobDefinitionInfo
    )
  );

  const tableRows = useMemo<TableMetricRecord[]>(() => {
    if (tableMetrics.length > 0) return tableMetrics;
    if (!sourceTable && !sinkTable) return [];

    return [
      {
        __key: "instance-summary",
        sourceTable,
        sinkTable,
        readRowCount: readRows,
        writeRowCount: writeRows,
        readQps: mergedInstance?.readQps,
        writeQps: mergedInstance?.writeQps || qps,
        status: getInstanceStatus(mergedInstance),
      },
    ];
  }, [
    mergedInstance,
    qps,
    readRows,
    sinkTable,
    sourceTable,
    tableMetrics,
    writeRows,
  ]);

  const sqlRecords = useMemo<SqlRecord[]>(() => {
    const records: SqlRecord[] = [];
    const used = new Set<string>();

    const append = (title: string, sql: unknown, tableName?: unknown) => {
      if (!sql || typeof sql !== "string" || used.has(sql)) return;
      used.add(sql);
      records.push({
        key: `${title}-${records.length}`,
        title,
        tableName: tableName ? String(tableName) : undefined,
        sql,
      });
    };

    append(
      "实例建表语句",
      firstValue(
        currentInstance?.createTableSql,
        currentInstance?.targetCreateTableSql,
        currentInstance?.migrationSql,
        currentInstance?.schemaSql,
        currentInstance?.ddl
      ),
      sinkTable
    );

    tableRows.forEach((item, index) => {
      append(
        `表结构迁移 ${index + 1}`,
        firstValue(
          item?.createTableSql,
          item?.targetCreateTableSql,
          item?.migrationSql,
          item?.schemaSql,
          item?.ddl
        ),
        firstValue(item?.sinkTable, item?.targetTable, item?.sourceTable)
      );
    });

    return records;
  }, [currentInstance, sinkTable, tableRows]);

  const instanceOptions = useMemo(
    () =>
      instances.map((item, index) => {
        const id = String(firstValue(item?.id, item?.instanceId) || "");
        const meta = statusMeta(getInstanceStatus(item));
        return {
          value: id,
          label: `运行记录 ${index + 1} · ${meta.label}`,
        };
      }),
    [instances]
  );

  const tableColumns = useMemo<ColumnsType<TableMetricRecord>>(
    () => [
      {
        title: "来源表",
        dataIndex: "sourceTable",
        minWidth: 190,
        render: (_value, record) => (
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#30343b]">
              {firstValue(record?.sourceTable, record?.sourceTableName, "-")}
            </div>
            <div className="mt-0.5 text-[11px] text-[#9aa0aa]">来源</div>
          </div>
        ),
      },
      {
        title: "目标表",
        dataIndex: "sinkTable",
        minWidth: 190,
        render: (_value, record) => (
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#30343b]">
              {firstValue(
                record?.sinkTable,
                record?.targetTable,
                record?.sinkTableName,
                "-"
              )}
            </div>
            <div className="mt-0.5 text-[11px] text-[#9aa0aa]">目标</div>
          </div>
        ),
      },
      {
        title: "读取行数",
        dataIndex: "readRowCount",
        width: 120,
        align: "right",
        render: (_value, record) =>
          formatNumber(
            firstValue(record?.readRowCount, record?.sourceRecordCount, 0)
          ),
      },
      {
        title: "写入行数",
        dataIndex: "writeRowCount",
        width: 120,
        align: "right",
        render: (_value, record) =>
          formatNumber(
            firstValue(record?.writeRowCount, record?.sinkSuccessRecordCount, 0)
          ),
      },
      {
        title: "读取 QPS",
        dataIndex: "readQps",
        width: 105,
        align: "right",
        render: (value) => formatNumber(value),
      },
      {
        title: "写入 QPS",
        dataIndex: "writeQps",
        width: 105,
        align: "right",
        render: (value) => formatNumber(value),
      },
      {
        title: "状态",
        dataIndex: "status",
        width: 100,
        render: (value) => {
          const meta = statusMeta(value);
          return (
            <Tag color={meta.color} className="!m-0">
              {meta.label}
            </Tag>
          );
        },
      },
    ],
    []
  );

  if (pageLoading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Spin size="large" />
      </div>
    );
  }

  if (!definition) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Empty description="未找到离线同步任务">
          <Button onClick={() => history.push("/sync/batch-link-up")}>
            返回任务列表
          </Button>
        </Empty>
      </div>
    );
  }

  const currentStatus = statusMeta(getInstanceStatus(mergedInstance));
  const errorMessage = firstValue(
    currentInstance?.errorMessage,
    currentInstance?.lastErrorMessage,
    definition?.lastErrorMessage
  );

  const triggerMode = firstValue(
    currentInstance?.runMode,
    currentInstance?.triggerType,
    currentInstance?.assignmentMode,
    "手动运行"
  );
  const failedRows = firstValue(currentInstance?.failedRecordCount, 0);
  const skippedRows = firstValue(currentInstance?.skippedRecordCount, 0);
  const startTime = formatDateTime(
    firstValue(currentInstance?.startTime, currentInstance?.createTime)
  );
  const endTime = formatDateTime(currentInstance?.endTime);

  const overviewContent = (
    <div className="grid gap-3 xl:grid-cols-2">
      <SectionCard title="同步数据">
        <div className="grid grid-cols-2 gap-3 p-5 md:grid-cols-3">
          <MetricTile label="读取行数" value={formatNumber(readRows)} />
          <MetricTile label="写入行数" value={formatNumber(writeRows)} />
          <MetricTile label="平均 QPS" value={formatNumber(qps)} />
          <MetricTile
            label="读取数据量"
            value={formatBytes(currentInstance?.sourceReadBytes)}
          />
          <MetricTile
            label="写入数据量"
            value={formatBytes(currentInstance?.sinkWrittenBytes)}
          />
          <MetricTile label="失败行数" value={formatNumber(failedRows)} />
        </div>

        {toNumber(skippedRows) > 0 ? (
          <div className="mx-5 mb-5 rounded-md bg-[#fff8f0] px-4 py-3 text-[12px] text-[#9a6700]">
            本次同步跳过 {formatNumber(skippedRows)} 行
          </div>
        ) : null}
      </SectionCard>

      <SectionCard title="执行情况">
        <div className="grid grid-cols-1 gap-x-10 gap-y-6 p-5 sm:grid-cols-2">
          <div>
            <div className="text-[12px] text-[#8a8f98]">触发方式</div>
            <div className="mt-2 text-[15px] font-medium text-[#161823]">
              {triggerMode}
            </div>
          </div>
          <div>
            <div className="text-[12px] text-[#8a8f98]">运行耗时</div>
            <div className="mt-2 text-[15px] font-medium text-[#161823]">
              {formatDuration(durationMillis)}
            </div>
          </div>
          <div>
            <div className="text-[12px] text-[#8a8f98]">开始时间</div>
            <div className="mt-2 text-[15px] font-medium text-[#161823]">
              {startTime}
            </div>
          </div>
          <div>
            <div className="text-[12px] text-[#8a8f98]">结束时间</div>
            <div className="mt-2 text-[15px] font-medium text-[#161823]">
              {endTime}
            </div>
          </div>
        </div>
      </SectionCard>
    </div>
  );

  const tabItems: Array<{
    key: DetailTabKey;
    label: string;
    children: ReactNode;
  }> = [
    {
      key: "overview",
      label: "总览",
      children: overviewContent,
    },
    {
      key: "log",
      label: "运行日志",
      children: (
        <SectionCard
          title="运行日志"
          extra={
            <Button
              size="small"
              type="text"
              icon={<ReloadOutlined />}
              loading={logLoading}
              className="!text-[#667085]"
              onClick={() => void loadLog()}
            >
              刷新
            </Button>
          }
        >
          <div className="px-5 pb-5 pt-4">
            <div className="mb-3 text-[12px] leading-5 text-[#8a8f98]">
              展示当前运行记录的执行过程，便于排查同步异常。
            </div>
            <div className="overflow-hidden rounded-md bg-[#181a1f]">
              {logLoading ? (
                <div className="flex min-h-[420px] items-center justify-center text-white/60">
                  <Spin size="small" />
                </div>
              ) : (
                <pre className="m-0 max-h-[680px] min-h-[420px] overflow-auto whitespace-pre-wrap break-words p-4 font-mono text-[12px] leading-5 text-[#d6d9df]">
                  {logContent || "当前运行记录暂无日志"}
                </pre>
              )}
            </div>
          </div>
        </SectionCard>
      ),
    },
    {
      key: "sync",
      label: "同步情况",
      children: (
        <SectionCard
          title={
            <span className="flex items-center gap-2">
              <TableOutlined className="text-[#7c828c]" />
              表级同步结果
            </span>
          }
          extra={
            <span className="text-[12px] text-[#8a8f98]">
              {tableRows.length} 张表
            </span>
          }
        >
          <div className="p-5">
            <Table<TableMetricRecord>
              rowKey="__key"
              size="small"
              loading={metricsLoading}
              columns={tableColumns}
              dataSource={tableRows}
              pagination={false}
              scroll={{ x: 920 }}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="当前运行记录暂无表级同步指标"
                  />
                ),
              }}
              className="[&_.ant-table-container]:!rounded-md [&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid [&_.ant-table-container]:!border-[#eceef1] [&_.ant-table-thead>tr>th]:!h-10 [&_.ant-table-thead>tr>th]:!bg-[#f7f7f8] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-tbody>tr>td]:!py-3 [&_.ant-table-tbody>tr>td]:!text-[12px]"
            />
          </div>
        </SectionCard>
      ),
    },
    {
      key: "config",
      label: "运行配置",
      children: (
        <SectionCard title="运行配置">
          <div className="p-5">
            {runtimeConfig ? (
              <pre className="m-0 max-h-[680px] min-h-[420px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f7f7f8] p-4 font-mono text-[12px] leading-5 text-[#30343b]">
                {runtimeConfig}
              </pre>
            ) : (
              <div className="flex min-h-[360px] items-center justify-center">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="当前运行记录未返回运行配置"
                />
              </div>
            )}
          </div>
        </SectionCard>
      ),
    },
    {
      key: "structure",
      label: "结构迁移",
      children: (
        <SectionCard title="结构迁移">
          <div className="p-5">
            {sqlRecords.length > 0 ? (
              <div className="space-y-3">
                {sqlRecords.map((item) => (
                  <div
                    key={item.key}
                    className="overflow-hidden rounded-md border border-solid border-[#eceef1]"
                  >
                    <div className="flex min-h-11 items-center justify-between gap-3 bg-[#f7f7f8] px-4">
                      <div className="min-w-0">
                        <span className="text-[12px] font-medium text-[#30343b]">
                          {item.title}
                        </span>
                        {item.tableName ? (
                          <span className="ml-2 text-[11px] text-[#9aa0aa]">
                            {item.tableName}
                          </span>
                        ) : null}
                      </div>
                      <Button
                        type="text"
                        size="small"
                        icon={<CopyOutlined />}
                        className="!text-[#667085]"
                        onClick={() =>
                          void copyText(item.sql, "建表语句已复制")
                        }
                      >
                        复制
                      </Button>
                    </div>
                    <pre className="m-0 max-h-[520px] overflow-auto whitespace-pre-wrap break-words bg-[#181a1f] p-4 font-mono text-[12px] leading-5 text-[#d6d9df]">
                      {item.sql}
                    </pre>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex min-h-[320px] flex-col items-center justify-center rounded-md bg-[#f7f7f8] px-6 text-center">
                <FileTextOutlined className="text-[28px] text-[#c0c4cc]" />
                <div className="mt-3 text-[13px] font-medium text-[#667085]">
                  暂无结构迁移语句
                </div>
                <div className="mt-1 max-w-[560px] text-[12px] leading-5 text-[#9aa0aa]">
                  当前实例接口尚未返回目标表建表语句。接口补充
                  createTableSql、ddl 或 migrationSql
                  后，本页会自动展示并支持复制。
                </div>
              </div>
            )}
          </div>
        </SectionCard>
      ),
    },
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f7f8] text-[#161823]">
        <div className="mx-auto w-full max-w-[1800px] px-4 pb-8 pt-0 lg:px-5">
          <div className="mb-2 flex h-10 items-center">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              className="!h-9 !px-1 !text-[14px] !font-semibold !text-[#30343b]"
              onClick={() => history.push("/sync/batch-link-up")}
            >
              返回同步列表
            </Button>
          </div>

          <section className="bg-white" style={{ borderRadius: 8 }}>
            <div className="grid min-h-[176px] gap-6 px-5 py-6 lg:px-6 xl:grid-cols-[116px_minmax(0,1fr)_320px] xl:items-center">
              <div className="relative flex h-[116px] w-[116px] shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white">
                <svg
  width="76"
  height="76"
  viewBox="0 0 76 76"
  fill="none"
  xmlns="http://www.w3.org/2000/svg"
  className="relative z-10 -translate-y-2"
  shapeRendering="crispEdges"
>
  {/* 左上小星星 */}
  <rect x="15" y="14" width="4" height="4" fill="#161823" />
  <rect x="11" y="18" width="4" height="4" fill="#161823" />
  <rect x="19" y="18" width="4" height="4" fill="#161823" />
  <rect x="15" y="22" width="4" height="4" fill="#161823" />

  {/* 右上粉色闪光 */}
  <rect x="56" y="18" width="4" height="4" fill="#FE2C55" />
  <rect x="52" y="22" width="4" height="4" fill="#FE2C55" />
  <rect x="60" y="22" width="4" height="4" fill="#FE2C55" />
  <rect x="56" y="26" width="4" height="4" fill="#FE2C55" />

  {/* 漂浮小像素 */}
  <rect x="24" y="12" width="3" height="3" fill="#C8CDD5" />
  <rect x="49" y="12" width="3" height="3" fill="#C8CDD5" />

  {/* 宝箱顶部轮廓 */}
  <rect x="22" y="27" width="32" height="4" fill="#161823" />
  <rect x="18" y="31" width="4" height="12" fill="#161823" />
  <rect x="54" y="31" width="4" height="12" fill="#161823" />

  {/* 宝箱盖 */}
  <rect x="22" y="31" width="32" height="4" fill="#F0F2F5" />
  <rect x="22" y="35" width="32" height="8" fill="#FFFFFF" />

  {/* 盖子高光 */}
  <rect x="26" y="35" width="20" height="4" fill="#F6F7F9" />

  {/* 宝箱中间黑边 */}
  <rect x="18" y="43" width="40" height="4" fill="#161823" />

  {/* 宝箱主体左右边 */}
  <rect x="18" y="47" width="4" height="16" fill="#161823" />
  <rect x="54" y="47" width="4" height="16" fill="#161823" />
  <rect x="22" y="63" width="32" height="4" fill="#161823" />

  {/* 宝箱主体 */}
  <rect x="22" y="47" width="32" height="16" fill="#F5F6F8" />

  {/* 像素阴影面 */}
  <rect x="22" y="55" width="32" height="8" fill="#E6E9EE" />
  <rect x="22" y="59" width="8" height="4" fill="#D7DBE2" />
  <rect x="46" y="59" width="8" height="4" fill="#D7DBE2" />

  {/* 中央锁 */}
  <rect x="34" y="43" width="8" height="4" fill="#FE2C55" />
  <rect x="32" y="47" width="12" height="8" fill="#161823" />
  <rect x="36" y="47" width="4" height="4" fill="#FE2C55" />
  <rect x="36" y="51" width="4" height="4" fill="#FFFFFF" />

  {/* 左右装饰铆钉 */}
  <rect x="25" y="50" width="4" height="4" fill="#AEB4BF" />
  <rect x="47" y="50" width="4" height="4" fill="#AEB4BF" />

  {/* 宝箱冒出的像素光 */}
  <rect x="32" y="22" width="4" height="4" fill="#FE2C55" />
  <rect x="40" y="18" width="4" height="4" fill="#161823" />
  <rect x="44" y="24" width="3" height="3" fill="#FE2C55" />

  {/* 小脚，增加角色感 */}
  <rect x="25" y="67" width="8" height="3" fill="#161823" />
  <rect x="43" y="67" width="8" height="3" fill="#161823" />
</svg>

                <div
                  className="
      pointer-events-none
      absolute
      inset-x-0
      bottom-0
      z-20
      h-[50px]
      bg-gradient-to-b
      from-transparent
      via-black/15
      to-black/35
    "
                />
              </div>

              <div className="min-w-0">
  {/* 任务名称 */}
  <div className="max-w-[520px] truncate text-[14px] font-medium leading-5 text-[#161823]">
    {definition.jobName || '离线同步任务'}
  </div>

  {/* 时间 */}
  {currentInstance ? (
    <div className="mt-1 text-[12px] leading-4 text-[#8a8f98]">
      {startTime}
    </div>
  ) : null}

  {/* 状态 */}
  {currentInstance ? (
    <div className="mt-1 flex items-center gap-1 text-[11px] leading-4 text-[#667085]">
      <span
        className={[
          'inline-block h-[10px] w-[10px] rounded-full',
          currentStatus.label === '已完成'
            ? 'bg-[#20c77a]'
            : currentStatus.label === '运行中'
              ? 'bg-[#1677ff]'
              : 'bg-[#ff4d4f]',
        ].join(' ')}
      />

      <span>{currentStatus.label}</span>
    </div>
  ) : null}

  {/* 数据源流向：降级为辅助信息 */}
  <div className="mt-2 flex min-w-0 items-center gap-1.5 text-[11px] leading-4 text-[#8a8f98]">
    <span className="max-w-[220px] truncate">
      {firstValue(
        definition?.sourceDatasourceName,
        sourceType,
        '来源数据源',
      )}
      {sourceTable ? ` / ${sourceTable}` : ''}
    </span>

    <SyncOutlined className="shrink-0 text-[10px] text-[#b0b5bd]" />

    <span className="max-w-[220px] truncate">
      {firstValue(
        definition?.sinkDatasourceName,
        sinkType,
        '目标数据源',
      )}
      {sinkTable ? ` / ${sinkTable}` : ''}
    </span>
  </div>
</div>

              <div className="min-w-0 xl:justify-self-end">
                
                <div className="flex gap-2">
                  <Select
                    showSearch
                    variant="filled"
                    allowClear={false}
                    value={selectedInstanceId || undefined}
                    options={instanceOptions}
                    optionFilterProp="label"
                    placeholder="请选择运行记录"
                    className="min-w-0 flex-1"
                    onChange={handleSelectInstance}
                    notFoundContent="暂无运行记录"
                  />
                  <Tooltip title="刷新运行记录">
                    <ReloadOutlined />
                  </Tooltip>
                </div>
              </div>
            </div>

            {errorMessage ? (
              <div className="mx-5 mb-4 rounded-md bg-[#fff5f5] px-4 py-3 text-[12px] leading-5 text-[#d92d20] lg:mx-6">
                <span className="font-medium">执行异常：</span>
                {String(errorMessage)}
              </div>
            ) : null}
          </section>

          <div className="px-5 lg:px-6">
            <YakTab
              activeKey={activeTab}
              onChange={handleTabChange}
              items={tabItems.map(({ key, label }) => ({ key, label }))}
            />
          </div>

          <div className="mt-3">
            {!selectedInstanceId || !currentInstance ? (
              <SectionCard title="执行详情">
                <div className="flex min-h-[360px] items-center justify-center">
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无运行记录，请先运行一次离线同步任务"
                  />
                </div>
              </SectionCard>
            ) : instanceLoading ? (
              <SectionCard title="执行详情">
                <div className="flex min-h-[360px] items-center justify-center">
                  <Spin />
                </div>
              </SectionCard>
            ) : (
              tabItems.find((item) => item.key === activeTab)?.children
            )}
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
