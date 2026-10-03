import { history } from "@umijs/max";
import { Alert, Button, Descriptions, Drawer, Spin, Tag, Tooltip } from "antd";
import { ExternalLink, RotateCw } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";

import {
  assetSubtitle,
  assetTitle,
  describeAttributeValue,
  resolveTypeVisual,
} from "@/components/metadata/typeVisual";
import { YakEmpty } from "@/components/ui";
import { lookupAssetBySource } from "@/services/data-asset/api";
import type { AssetSourceLookup } from "@/services/data-asset/types";
import { getTableStorage } from "@/services/data-lifecycle/api";
import type { TableStorage } from "@/services/data-lifecycle/types";
import { getEntityDetail } from "@/services/metadata/api";
import { formatMetadataTime } from "@/services/metadata/presentation";
import type {
  EntityTypeView,
  MetadataChangeRecord,
  MetadataEntityDetail,
  MetadataLineageEntry,
  MetadataSearchItem,
  MetadataSection,
  MetadataSourceProjection,
  MetadataStatsSectionData,
} from "@/services/metadata/types";

/**
 * 投影实体 → 源域编辑入口的路由。
 * 只列已确认真实存在的路径；标准字段/业务域当前无按 id 的深链,落列表页而不是编一个假详情地址。
 */
const SOURCE_ROUTES: Record<string, string> = {
  dataModel: "/modeling/models",
  metric: "/metric/manage",
};

const SECTION_TITLES: Record<string, string> = {
  stats: "统计",
  children: "子级实体",
  history: "变更历史",
  labels: "标签",
  lineage: "血缘",
  source: "源域现状（实时）",
};

const fact = (
  detail: MetadataEntityDetail | null,
  key: string
): string | null => {
  const value = detail?.entity.facts?.[key];
  return value === undefined || value === null || value === ""
    ? null
    : String(value);
};

/**
 * 实体详情抽屉（工单 118 聚合接口 + 工单 123 展示面）。
 *
 * <p>面板构成不写死：后端给了哪几块就渲哪几块，块的判据在元模型里（`collectible`/父子对/
 * `lineage_asset_type`/`provider_bean`）。三态各渲各的样子——把 UNAVAILABLE 折成空面板，
 * 等于把"没接上"伪装成"没有",正是这张页最难查的错。
 *
 * <p>存储量是唯一的例外：它不在 `sections` 里，由 lifecycle 的按表快照端点单独给（元数据不依赖
 * lifecycle，否则与 modeling→metadata 那条 SPI 边成环）。
 */
const AssetDetailDrawer = ({
  open,
  item,
  types,
  onClose,
  onDrillColumns,
}: {
  open: boolean;
  item: MetadataSearchItem | null;
  types: EntityTypeView[];
  onClose: () => void;
  onDrillColumns?: (item: MetadataSearchItem) => void;
}) => {
  const [detail, setDetail] = useState<MetadataEntityDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);
  const [storage, setStorage] = useState<TableStorage | null>(null);
  const [assetLookup, setAssetLookup] = useState<AssetSourceLookup | null>(
    null
  );

  const entityTypeName = detail?.entity.typeName ?? item?.typeName ?? "";
  const registered =
    (fact(detail, "providerType") ?? item?.providerType) === "REGISTERED";

  useEffect(() => {
    if (!open || !item) return;
    let alive = true;
    setLoading(true);
    setError(null);
    setDetail(null);
    setStorage(null);
    setAssetLookup(null);
    getEntityDetail(item.id)
      .then((result) => {
        if (!alive) return;
        setDetail(result);
        // Physical Metadata tables are indexed in Asset with the Metadata entity id as sourceId.
        // Registered projections have their own owning-domain identity and are not looked up as METADATA.
        if (
          result.entity.typeName === "table" &&
          result.entity.facts.providerType === "HARVESTED"
        ) {
          lookupAssetBySource("METADATA", String(result.entity.id))
            .then((lookup) => alive && setAssetLookup(lookup))
            .catch(() => alive && setAssetLookup(null));
        }
        const { databaseName, tableName, dataSourceId } = result.entity.facts;
        // 只有表级实体有存储量可问；列与投影实体问了也是白问。
        if (databaseName && tableName && result.entity.typeName === "table") {
          getTableStorage({
            datasourceId: dataSourceId == null ? null : Number(dataSourceId),
            databaseName,
            tableName,
          })
            .then((snapshot) => alive && setStorage(snapshot))
            .catch(() => alive && setStorage(null));
        }
      })
      .catch(
        (e: unknown) =>
          alive && setError(e instanceof Error ? e.message : "详情读取失败")
      )
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [open, item, nonce]);

  if (!item) return null;

  const visual = resolveTypeVisual(types, entityTypeName || item.typeName);
  const sourcePath =
    registered && item.sourceId
      ? SOURCE_ROUTES[entityTypeName || ""]
      : undefined;
  const attributes = (detail?.entity.attributes ?? {}) as Record<
    string,
    unknown
  >;
  const attributeKeys = Object.keys(attributes);
  const fieldLabel = (fieldName: string): string => {
    const field = types
      .find((type) => type.typeName === (entityTypeName || item.typeName))
      ?.fields?.find(
        (candidate) =>
          candidate.fieldName === fieldName ||
          candidate.storageSlot === fieldName
      );
    return field?.displayName || fieldName;
  };

  const renderBody = () => {
    if (loading) {
      return (
        <div className="flex justify-center py-12">
          <Spin />
        </div>
      );
    }
    if (error) {
      // 整份详情只在实体本身不在目录时失败（49001）；此时如实说，不拿半截数据撑门面。
      return (
        <Alert
          type="error"
          showIcon
          message="详情读取失败"
          description={error}
          action={
            <Button
              size="small"
              icon={<RotateCw size={13} />}
              onClick={() => setNonce((n) => n + 1)}
            >
              重试
            </Button>
          }
        />
      );
    }
    if (!detail) return null;
    const sections = detail.sections ?? {};
    return (
      <div className="space-y-5">
        <Descriptions
          size="small"
          column={2}
          labelStyle={{ width: 96, color: "#667085" }}
          items={[
            {
              key: "fqn",
              label: "全名",
              span: 2,
              children:
                fact(detail, "fullyQualifiedName") ||
                detail.entity.facts.assetKey,
            },
            {
              key: "path",
              label: "位置",
              span: 2,
              children: assetSubtitle(item),
            },
            {
              key: "summary",
              label: "描述",
              span: 2,
              children: detail.entity.facts.summary || "-",
            },
            {
              key: "owner",
              label: "负责人",
              children: detail.entity.facts.ownerUser || "-",
            },
            {
              key: "domains",
              label: "业务域",
              children:
                ((detail.entity.facts.domainIds ?? []) as string[]).join(
                  "、"
                ) || "-",
            },
            {
              key: "source",
              label: "来源",
              children: `${detail.entity.facts.sourceType ?? "-"} / ${
                detail.entity.facts.sourceId ?? "-"
              }`,
            },
            {
              key: "updatedBy",
              label: "更新人",
              children: detail.entity.facts.updatedBy || "-",
            },
            {
              key: "firstSeen",
              label: "首次发现",
              children: formatMetadataTime(detail.entity.facts.firstSeenAt),
            },
            {
              key: "lastCollect",
              label: "最近采集",
              children: formatMetadataTime(detail.entity.facts.lastCollectAt),
            },
          ]}
        />

        <div>
          <div className="mb-2 text-[14px] font-semibold text-[#101828]">
            属性
          </div>
          {attributeKeys.length > 0 ? (
            <div className="space-y-1 rounded-[8px] border border-solid border-[#eaecf0] p-3">
              {attributeKeys.map((key) => (
                <div key={key} className="flex gap-3 text-[13px]">
                  <span className="w-[160px] max-sm:w-[110px] shrink-0 truncate text-[#667085]">
                    <Tooltip title={key}>{fieldLabel(key)}</Tooltip>
                  </span>
                  <span className="break-all">
                    {describeAttributeValue(attributes[key])}
                  </span>
                </div>
              ))}
            </div>
          ) : typeof detail.entity.attributes === "string" ? (
            // 属性袋里存进过非 JSON 值：原样给出，让坏数据可见而不是静默吞掉。
            <div className="break-all rounded-[8px] bg-[#fffaeb] p-3 text-[12px] text-[#b54708]">
              属性值不是合法 JSON，原样展示：{String(detail.entity.attributes)}
            </div>
          ) : (
            <YakEmpty
              compact
              title="该实体暂无属性"
              description="源域未投影属性，或该类型字段尚未提槽"
            />
          )}
        </div>

        {Object.entries(sections).map(([key, section]) =>
          section ? (
            <SectionBlock
              key={key}
              name={key}
              section={section as MetadataSection<unknown>}
            >
              {renderSectionContent(key, section as MetadataSection<unknown>)}
            </SectionBlock>
          ) : null
        )}

        {detail.entity.typeName === "table" ? (
          <SectionBlock
            name="storage"
            title="存储量（每日快照）"
            section={storageSection(storage)}
          >
            {storage ? (
              <Descriptions
                size="small"
                column={2}
                labelStyle={{ width: 96, color: "#667085" }}
                items={[
                  {
                    key: "size",
                    label: "占用",
                    children: `${storage.sizeGb} GB（近似）`,
                  },
                  {
                    key: "snapshot",
                    label: "快照日期",
                    children: storage.snapshotDate || "-",
                  },
                ]}
              />
            ) : null}
          </SectionBlock>
        ) : null}
      </div>
    );
  };

  return (
    <Drawer
      open={open}
      width={720}
      placement="right"
      onClose={onClose}
      destroyOnClose
      title={
        <div className="flex items-center gap-2">
          <span
            className="rounded-[4px] px-2 py-0.5 text-[12px] font-semibold text-white"
            style={{ backgroundColor: visual.color }}
          >
            {visual.label}
          </span>
          <span className="truncate text-[18px] font-semibold leading-7 text-[#101828]">
            {assetTitle(item)}
          </span>
        </div>
      }
      styles={{
        header: { padding: "18px 24px", borderBottom: "1px solid #eaecf0" },
        body: { padding: 24 },
      }}
    >
      <div className="space-y-5">
        <div className="flex flex-wrap items-center gap-2">
          <Tag color={registered ? "purple" : "geekblue"}>
            {registered ? "登记投影 · 实时读源域" : "物理采集"}
          </Tag>
          {item.entityStatus ? <Tag>{item.entityStatus}</Tag> : null}
          {item.layerCode ? <Tag color="cyan">{item.layerCode}</Tag> : null}
          {sourcePath && item.sourceId ? (
            <Button
              size="small"
              icon={<ExternalLink size={13} />}
              onClick={() => history.push(`${sourcePath}/${item.sourceId}`)}
            >
              打开源域详情
            </Button>
          ) : null}
          {assetLookup?.state === "FOUND" && assetLookup.assetId ? (
            <Button
              size="small"
              icon={<ExternalLink size={13} />}
              onClick={() =>
                history.push(`/data-asset/detail/${assetLookup.assetId}`)
              }
            >
              查看统一资产治理
            </Button>
          ) : null}
          {assetLookup?.state === "NOT_INDEXED" ? (
            <Tag>尚未纳入资产台账</Tag>
          ) : null}
          {(item.matchedColumnCount ?? 0) > 0 && onDrillColumns ? (
            <Button size="small" onClick={() => onDrillColumns(item)}>
              查看命中的 {item.matchedColumnCount} 列
            </Button>
          ) : null}
        </div>

        {renderBody()}
      </div>
    </Drawer>
  );
};

/** 一块一个标题 + 三态徽标；UNAVAILABLE 必带原因原文，EMPTY 带后端给的说明。 */
const SectionBlock = ({
  name,
  title,
  section,
  children,
}: {
  name: string;
  /** 不在 sections 里的块（如存储量）由调用方给标题。 */
  title?: string;
  section: MetadataSection<unknown>;
  children?: ReactNode;
}) => (
  <div>
    <div className="mb-2 flex items-center gap-2">
      <span className="text-[14px] font-semibold text-[#101828]">
        {title ?? SECTION_TITLES[name] ?? name}
      </span>
      {section.status === "UNAVAILABLE" ? (
        <Tag color="orange">暂不可用</Tag>
      ) : section.status === "EMPTY" ? (
        <Tag>暂无数据</Tag>
      ) : null}
    </div>
    {section.status === "UNAVAILABLE" ? (
      <div className="rounded-[8px] bg-[#fffaeb] p-3 text-[12px] leading-5 text-[#b54708]">
        {section.code ? `错误码 ${section.code}：` : ""}
        {section.message || "该块读取失败"}
      </div>
    ) : section.status === "EMPTY" ? (
      <div className="rounded-lg border border-[#eaecf0] bg-[#f9fafb] px-3 py-3 text-[13px] text-[#667085]">
        {section.message || "当前范围暂无数据"}
      </div>
    ) : (
      children
    )}
  </div>
);

const renderSectionContent = (
  name: string,
  section: MetadataSection<unknown>
) => {
  if (name === "stats") {
    const stats = (section.data ?? {}) as MetadataStatsSectionData;
    return (
      <Descriptions
        size="small"
        column={2}
        labelStyle={{ width: 96, color: "#667085" }}
        items={[
          {
            key: "rows",
            label: "行数",
            children: `${stats.rowCountApprox ?? "未知"}${
              stats.approximate ? "（近似）" : ""
            }`,
          },
          {
            key: "partitioned",
            label: "分区",
            children: describeAttributeValue(stats.partitioned),
          },
          {
            key: "ddl",
            label: "最后 DDL",
            children: formatMetadataTime(stats.lastDdlTime),
          },
          {
            key: "collect",
            label: "采集时刻",
            children: formatMetadataTime(stats.lastCollectAt),
          },
        ]}
      />
    );
  }
  if (name === "children") {
    const children = (section.data ?? []) as MetadataEntityDetail["entity"][];
    return (
      <div className="space-y-1 rounded-[8px] border border-solid border-[#eaecf0] p-3">
        {children.slice(0, 20).map((child) => (
          <div key={child.id} className="flex items-center gap-2 text-[13px]">
            <Tag>{child.typeName}</Tag>
            <span className="truncate">
              {String(
                child.facts?.displayName || child.facts?.name || child.id
              )}
            </span>
          </div>
        ))}
        {children.length > 20 ? (
          <div className="text-[12px] text-[#98a2b3]">
            另有 {children.length - 20} 条，未在本页列出
          </div>
        ) : null}
      </div>
    );
  }
  if (name === "history") {
    const page = (section.data ?? {}) as { bizData?: MetadataChangeRecord[] };
    const records = page.bizData ?? [];
    return (
      <div className="space-y-1 rounded-[8px] border border-solid border-[#eaecf0] p-3">
        {records.map((change) => (
          <div key={change.id} className="flex gap-2 text-[13px]">
            <span className="w-[92px] shrink-0 text-[#667085]">
              {formatMetadataTime(change.changedAt)}
            </span>
            <span className="w-[88px] shrink-0">{change.changeType}</span>
            <span className="w-[120px] shrink-0 truncate">
              {change.fieldName || "-"}
            </span>
            <span className="truncate text-[#475467]">
              {change.beforeValue ? `${change.beforeValue} → ` : ""}
              {change.afterValue || ""}
            </span>
          </div>
        ))}
        {/* 详情只给一屏；完整时间线走 /entities/{id}/changes，翻页不在抽屉里堆状态。 */}
        <div className="text-[12px] text-[#98a2b3]">
          最近 {records.length} 条变更
        </div>
      </div>
    );
  }
  if (name === "labels") {
    const labels = (section.data ?? []) as Array<Record<string, unknown>>;
    return (
      <div className="flex flex-wrap gap-2">
        {labels.map((label) => (
          <Tooltip
            key={String(label.id)}
            title={`${label.labelType ?? ""} · ${label.state ?? ""}`}
          >
            <Tag>{String(label.labelCode)}</Tag>
          </Tooltip>
        ))}
      </div>
    );
  }
  if (name === "lineage") {
    const entry = (section.data ?? {}) as MetadataLineageEntry;
    return (
      <Button
        size="small"
        icon={<ExternalLink size={13} />}
        onClick={() => entry.path && history.push(entry.path)}
      >
        到血缘图查看
      </Button>
    );
  }
  if (name === "source") {
    const projection = (section.data ?? {}) as MetadataSourceProjection;
    const columns = projection.extra?.columns ?? [];
    return (
      <div className="space-y-2">
        <div className="text-[12px] text-[#667085]">
          {projection.displayName || projection.name || "-"} · 物理表{" "}
          {projection.extra?.tableName || "-"} · 版本{" "}
          {projection.extra?.latestVersionNo ?? "-"} · 状态{" "}
          {projection.extra?.status || "-"} · 源域更新{" "}
          {formatMetadataTime(projection.sourceUpdatedAt)}
        </div>
        <div className="rounded-[8px] border border-solid border-[#eaecf0]">
          <div className="flex gap-2 border-b border-solid border-[#eaecf0] px-3 py-2 text-[12px] text-[#667085]">
            <span className="w-[160px] shrink-0">列名</span>
            <span className="w-[140px] shrink-0">类型</span>
            <span className="flex-1 truncate">列注释</span>
            <span className="w-[110px] shrink-0">关联标准字段</span>
          </div>
          {columns.map((column) => (
            <div
              key={String(column.columnName)}
              className="flex gap-2 px-3 py-1 text-[13px]"
            >
              <span className="w-[160px] shrink-0 truncate">
                {String(column.columnName ?? "-")}
              </span>
              <span className="w-[140px] shrink-0 truncate">
                {String(column.dataType ?? "-")}
              </span>
              {/* 注释与标准字段并排：人工据此判断要不要沉淀，本模块不做自动抽取（错误沉淀不可逆）。 */}
              <span className="flex-1 truncate text-[#475467]">
                {String(column.comment ?? column.businessDescription ?? "-")}
              </span>
              <span className="w-[110px] shrink-0">
                {column.stdFieldId ? `#${String(column.stdFieldId)}` : "未关联"}
              </span>
            </div>
          ))}
        </div>
      </div>
    );
  }
  return null;
};

/** 存储量块独立于 sections：它出自 lifecycle，且"还没有快照"与"读不到"要分得开。 */
const storageSection = (
  storage: TableStorage | null
): MetadataSection<unknown> =>
  storage
    ? { status: "OK", data: storage }
    : {
        status: "EMPTY",
        message: "该表还没有存储量快照，或本轮快照任务未跑到",
      };

export default AssetDetailDrawer;
