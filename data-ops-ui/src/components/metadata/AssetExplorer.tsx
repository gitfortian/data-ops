import { Button, Checkbox, Input, Select, Space, Tag, Tooltip } from "antd";
import { Search, SlidersHorizontal, X } from "lucide-react";
import type { ReactNode } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";

import AssetDetailDrawer from "@/components/metadata/AssetDetailDrawer";
import {
  ENTITY_STATUS_OPTIONS,
  PROVIDER_OPTIONS,
  assetSubtitle,
  assetTitle,
  resolveTypeVisual,
} from "@/components/metadata/typeVisual";
import { YakEmpty } from "@/components/ui";
import { listAllDataSources } from "@/services/data-source/api";
import {
  getEntityDetail,
  listEntityTypes,
  searchMetadata,
} from "@/services/metadata/api";
import { formatMetadataTime } from "@/services/metadata/presentation";
import type {
  EntityTypeView,
  MetadataFilter,
  MetadataSearchItem,
  MetadataSearchParams,
  MetadataSearchResult,
} from "@/services/metadata/types";

const PAGE_SIZE = 20;
/** 无游标时只能 offset 深翻，上限与服务端 SearchConditionBuilder.MAX_OFFSET 同值。 */
const MAX_OFFSET = 5000;

type SortOption = "" | NonNullable<MetadataSearchParams["sortField"]>;

/** 原生筛选条已覆盖的键：元模型里同名字段不再重复出控件。 */
const NATIVE_FILTER_KEYS = new Set([
  "providerType",
  "entityStatus",
  "datasourceId",
  "dataSourceId",
  "parentAssetId",
  "databaseName",
  "layerCode",
  "owner",
  "ownerUser",
  "hasSummary",
]);

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: "", label: "默认（有词按权重、无词按更新）" },
  { value: "relevance", label: "类型权重" },
  { value: "updateTime", label: "更新时间" },
  { value: "createTime", label: "入库时间" },
  { value: "name", label: "名称" },
  { value: "id", label: "目录 id" },
];

/** 空值不参与请求：一个空串筛进去会被服务端当成"要筛空值"，而不是"不筛"。 */
const compactFilter = (filter: MetadataFilter): MetadataFilter => {
  const out: Record<string, unknown> = {};
  Object.entries(filter).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    if (Array.isArray(value) && value.length === 0) return;
    if (typeof value === "object" && !Array.isArray(value)) {
      const pair = value as { from?: string; to?: string };
      if (!pair.from && !pair.to) return;
      out[key] = `${pair.from ?? ""}..${pair.to ?? ""}`;
      return;
    }
    out[key] = value;
  });
  return out as MetadataFilter;
};

export interface DrillTarget {
  item: MetadataSearchItem;
  label: string;
}

/**
 * 目录检索面板：M2-2 后由资产目录「元数据实体」视图独占（原目录浏览/统一搜索两页并入）。
 *
 * <p>两页的差别只有 `searchable`——是否给一个 q 输入框。类型切换只改 `index` 参数、
 * 不换接口，facet 计数来自服务端同一次 GROUP BY，因此不会出现"切了类型总数还不变"的假象。
 */
const AssetExplorer = ({ searchable = false }: { searchable?: boolean }) => {
  const [types, setTypes] = useState<EntityTypeView[]>([]);
  const [dataSourceNames, setDataSourceNames] = useState<
    { id: number; name: string }[]
  >([]);

  const [keyword, setKeyword] = useState("");
  const [q, setQ] = useState("");
  const [index, setIndex] = useState<string[]>([]);
  const [filter, setFilter] = useState<MetadataFilter>({});
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [sortField, setSortField] = useState<SortOption>("");
  const [sortOrder, setSortOrder] = useState<"asc" | "desc">("desc");
  const [explain, setExplain] = useState(false);
  const [drill, setDrill] = useState<DrillTarget | null>(null);

  const [page, setPage] = useState(1);
  /** page → 进入该页所需的 searchAfter 游标；没有游标只能退回 offset。 */
  const [cursors, setCursors] = useState<Record<number, string | undefined>>({
    1: undefined,
  });
  const [result, setResult] = useState<MetadataSearchResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  /** 只用来"重试同一发查询"：不参与任何参数，改了它不改变检索条件。 */
  const [nonce, setNonce] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<MetadataSearchItem | null>(null);

  useEffect(() => {
    listEntityTypes("ENTITY")
      .then((list) =>
        setTypes((list ?? []).filter((type) => type.status === "ACTIVE"))
      )
      .catch(() => setTypes([]));
    listAllDataSources()
      .then((result) =>
        setDataSourceNames(
          (result.bizData ?? [])
            .filter((ds) => ds.id != null)
            .map((ds) => ({
              id: Number(ds.id),
              name: ds.name ?? String(ds.id),
            }))
        )
      )
      .catch(() => setDataSourceNames([]));
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const entityIdValue = params.get("entityId");
    const entityId =
      entityIdValue && /^\d+$/.test(entityIdValue)
        ? Number(entityIdValue)
        : undefined;
    if (!entityId) return;
    let current = true;
    getEntityDetail(entityId)
      .then(({ entity }) => {
        if (!current) return;
        const facts = entity.facts;
        setDetail({
          ...facts,
          id: entity.id,
          assetKey: String(facts.assetKey ?? params.get("assetKey") ?? ""),
          typeName: entity.typeName,
          attributes: entity.attributes,
        } as MetadataSearchItem);
      })
      .catch(() => {
        if (current) setError("无法加载目标元数据实体");
      });
    return () => {
      current = false;
    };
  }, []);

  const filterKey = JSON.stringify(compactFilter(filter));

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const search = await searchMetadata({
        q: searchable ? q : undefined,
        index: index.length > 0 ? index : undefined,
        queryFilter: compactFilter(filter),
        sortField: sortField || undefined,
        // 无显式排序时服务端自己定方向，前端不假装"asc"是用户选的。
        sortOrder: sortField ? sortOrder : undefined,
        size: PAGE_SIZE,
        from: cursors[page] ? undefined : (page - 1) * PAGE_SIZE,
        searchAfter: cursors[page],
        explain,
      });
      setResult(search);
      setError(null);
      const next = search.nextSearchAfter;
      setCursors((prev) => (next ? { ...prev, [page + 1]: next } : prev));
    } catch (cause) {
      // 业务原因（筛选键不合法、类型名不存在等）已由全局提示给出，这里只负责把结果面清空，
      // 但必须把失败留在面上——否则空态会说"目录里还没有数据"，把服务端的错记到数据账上。
      setResult(null);
      setError(
        cause instanceof Error && cause.message ? cause.message : "检索失败"
      );
    } finally {
      setLoading(false);
    }
  }, [
    q,
    searchable,
    index,
    filterKey,
    sortField,
    sortOrder,
    page,
    explain,
    nonce,
  ]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    void load();
  }, [load]);

  const resetPage = () => {
    setPage(1);
    setCursors({ 1: undefined });
  };

  const toggleType = (typeName: string) => {
    setIndex((prev) =>
      prev.includes(typeName)
        ? prev.filter((item) => item !== typeName)
        : [...prev, typeName]
    );
    setDrill(null);
    resetPage();
  };

  /** 「命中 N 列」下钻：换 index 到 tableColumn + 按父实体收窄，同一接口不开第二条路径。 */
  const drillColumns = (item: MetadataSearchItem) => {
    setIndex(["tableColumn"]);
    setFilter((prev) => ({ ...prev, parentAssetId: item.id }));
    setDrill({ item, label: assetTitle(item) });
    resetPage();
  };

  const clearDrill = () => {
    setIndex([]);
    setFilter((prev) => {
      const next = { ...prev };
      delete next.parentAssetId;
      return next;
    });
    setDrill(null);
    resetPage();
  };

  const setAttr = (
    fieldName: string,
    value: string | { from?: string; to?: string }
  ) => {
    setFilter(
      (prev) => ({ ...prev, [`attr.${fieldName}`]: value } as MetadataFilter)
    );
    resetPage();
  };

  const setNative = (key: string, value: string | boolean | undefined) => {
    setFilter((prev) => ({ ...prev, [key]: value } as MetadataFilter));
    resetPage();
  };

  const resetAll = () => {
    setKeyword("");
    setQ("");
    setIndex([]);
    setFilter({});
    setSortField("");
    setSortOrder("desc");
    setDrill(null);
    resetPage();
  };

  // 属性筛选控件的候选：元模型说能筛（filterable）才给控件。
  const attrFields = useMemo(() => {
    const scoped =
      index.length > 0
        ? types.filter((type) => index.includes(type.typeName))
        : types;
    const byName = new Map<
      string,
      {
        fieldName: string;
        displayName?: string | null;
        matchType?: string | null;
      }
    >();
    scoped.forEach((type) => {
      (type.fields ?? []).forEach((field) => {
        // 原生条已按目录固有列给出控件，同名 attr 控件会把同一个概念画两次（两个键、两种落点）。
        if (
          !field.filterable ||
          field.deprecated ||
          NATIVE_FILTER_KEYS.has(field.fieldName)
        )
          return;
        if (byName.has(field.fieldName)) return;
        byName.set(field.fieldName, {
          fieldName: field.fieldName,
          displayName: field.displayName,
          matchType: field.matchType,
        });
      });
    });
    return [...byName.values()];
  }, [types, index]);

  const items = result?.items ?? [];
  const facets = result?.typeFacets ?? [];
  const facetByType = useMemo(
    () =>
      Object.fromEntries(facets.map((facet) => [facet.typeName, facet.count])),
    [facets]
  );
  const offsetExhausted = !cursors[page + 1] && page * PAGE_SIZE >= MAX_OFFSET;
  const hasMore = items.length >= PAGE_SIZE;

  return (
    <div className="mt-4 space-y-4">
      <div className="rounded-xl border border-solid border-[#eceef2] bg-white p-4">
        <div className="flex flex-wrap items-center gap-3">
          {searchable ? (
            <>
              <Input
                className="max-w-[520px] flex-1"
                size="large"
                allowClear
                value={keyword}
                placeholder="搜索表、列、模型、标准字段、业务域、指标——一次查询跨全部类型"
                prefix={<Search size={15} className="text-[#98a2b3]" />}
                onChange={(event) => setKeyword(event.target.value)}
                onPressEnter={() => {
                  setQ(keyword.trim());
                  resetPage();
                }}
              />
              <Button
                size="large"
                type="primary"
                loading={loading}
                onClick={() => {
                  setQ(keyword.trim());
                  resetPage();
                }}
              >
                搜索
              </Button>
            </>
          ) : null}
          <div className="ml-auto flex items-center gap-3">
            <Button
              size="small"
              aria-expanded={advancedOpen}
              onClick={() => setAdvancedOpen(!advancedOpen)}
            >
              高级筛选
              {Object.keys(filter).length > 0
                ? ` (${Object.keys(filter).length})`
                : ""}
            </Button>
            <Checkbox
              className={advancedOpen ? "" : "hidden"}
              checked={explain}
              onChange={(event) => {
                setExplain(event.target.checked);
                resetPage();
              }}
            >
              <span className="text-[12px] text-[#667085]">查询诊断</span>
            </Checkbox>
            <Button size="small" onClick={resetAll}>
              重置
            </Button>
          </div>
        </div>

        {/* 类型是 facet 不是多入口：这一排按钮只改 index 参数，接口与页面都不换。 */}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => {
              setIndex([]);
              setDrill(null);
              resetPage();
            }}
            className={
              index.length === 0
                ? "rounded-[8px] bg-[#f2f3f5] px-3 py-1 text-[13px] font-semibold text-[#242731]"
                : "rounded-[8px] px-3 py-1 text-[13px] font-medium text-[#777c86] hover:bg-[#f7f8fa]"
            }
          >
            全部 {result ? `(${result.total})` : ""}
          </button>
          {types.map((type) => {
            const visual = resolveTypeVisual(types, type.typeName);
            const active = index.includes(type.typeName);
            const count = facetByType[type.typeName];
            return (
              <button
                key={type.id}
                type="button"
                onClick={() => toggleType(type.typeName)}
                className={
                  active
                    ? "rounded-[8px] px-3 py-1 text-[13px] font-semibold text-white"
                    : "rounded-[8px] px-3 py-1 text-[13px] font-medium text-[#777c86] hover:bg-[#f7f8fa]"
                }
                style={active ? { backgroundColor: visual.color } : undefined}
              >
                {visual.label} {count != null ? `(${count})` : ""}
              </button>
            );
          })}
        </div>

        <div
          className={
            advancedOpen
              ? "mt-3 flex flex-wrap items-end gap-3 border-0 border-t border-solid border-[#f2f4f7] pt-3"
              : "hidden"
          }
        >
          <Space size={8} wrap>
            <FieldSlot label="来源通道">
              <Select
                allowClear
                size="small"
                className="w-[110px]"
                placeholder="全部"
                value={
                  typeof filter.providerType === "string"
                    ? filter.providerType
                    : undefined
                }
                options={PROVIDER_OPTIONS}
                onChange={(value) => setNative("providerType", value)}
              />
            </FieldSlot>
            <FieldSlot label="治理状态">
              <Select
                allowClear
                size="small"
                className="w-[120px]"
                placeholder="全部"
                value={
                  typeof filter.entityStatus === "string"
                    ? filter.entityStatus
                    : undefined
                }
                options={ENTITY_STATUS_OPTIONS}
                onChange={(value) => setNative("entityStatus", value)}
              />
            </FieldSlot>
            <FieldSlot label="数据源">
              <Select
                allowClear
                showSearch
                size="small"
                optionFilterProp="label"
                className="w-[150px]"
                placeholder="全部"
                value={
                  filter.datasourceId != null
                    ? Number(filter.datasourceId)
                    : undefined
                }
                options={dataSourceNames.map((ds) => ({
                  value: ds.id,
                  label: ds.name,
                }))}
                onChange={(value) =>
                  setNative(
                    "datasourceId",
                    value == null ? undefined : String(value)
                  )
                }
              />
            </FieldSlot>
            <FieldSlot label="数据库">
              <Input
                size="small"
                className="w-[130px]"
                value={
                  typeof filter.databaseName === "string"
                    ? filter.databaseName
                    : ""
                }
                onChange={(event) =>
                  setNative("databaseName", event.target.value)
                }
              />
            </FieldSlot>
            <FieldSlot label="负责人">
              <Input
                size="small"
                className="w-[110px]"
                value={typeof filter.owner === "string" ? filter.owner : ""}
                onChange={(event) => setNative("owner", event.target.value)}
              />
            </FieldSlot>
            <FieldSlot label="分层">
              <Input
                size="small"
                className="w-[90px]"
                value={
                  typeof filter.layerCode === "string" ? filter.layerCode : ""
                }
                onChange={(event) => setNative("layerCode", event.target.value)}
              />
            </FieldSlot>
            <FieldSlot label="只看有描述">
              <Checkbox
                checked={filter.hasSummary === true}
                onChange={(event) =>
                  setNative(
                    "hasSummary",
                    event.target.checked ? true : undefined
                  )
                }
              />
            </FieldSlot>
          </Space>

          {attrFields.map((field) =>
            field.matchType === "range" ? (
              <FieldSlot
                key={field.fieldName}
                label={field.displayName || field.fieldName}
              >
                <Space.Compact size="small">
                  <Input
                    className="w-[70px]"
                    placeholder="起"
                    value={
                      (
                        filter[`attr.${field.fieldName}`] as
                          | { from?: string }
                          | undefined
                      )?.from ?? ""
                    }
                    onChange={(event) =>
                      setAttr(field.fieldName, {
                        ...(filter[`attr.${field.fieldName}`] as
                          | { to?: string }
                          | undefined),
                        from: event.target.value,
                      })
                    }
                  />
                  <Input
                    className="w-[70px]"
                    placeholder="止"
                    value={
                      (
                        filter[`attr.${field.fieldName}`] as
                          | { to?: string }
                          | undefined
                      )?.to ?? ""
                    }
                    onChange={(event) =>
                      setAttr(field.fieldName, {
                        ...(filter[`attr.${field.fieldName}`] as
                          | { from?: string }
                          | undefined),
                        to: event.target.value,
                      })
                    }
                  />
                </Space.Compact>
              </FieldSlot>
            ) : (
              <FieldSlot
                key={field.fieldName}
                label={field.displayName || field.fieldName}
              >
                <Input
                  size="small"
                  className="w-[120px]"
                  value={
                    typeof filter[`attr.${field.fieldName}`] === "string"
                      ? (filter[`attr.${field.fieldName}`] as string)
                      : ""
                  }
                  onChange={(event) =>
                    setAttr(field.fieldName, event.target.value)
                  }
                />
              </FieldSlot>
            )
          )}

          <div className="ml-auto flex items-center gap-2">
            <SlidersHorizontal size={14} className="text-[#98a2b3]" />
            <Select
              size="small"
              className="w-[190px]"
              value={sortField}
              options={SORT_OPTIONS}
              onChange={(value) => {
                setSortField(value as SortOption);
                resetPage();
              }}
            />
            <Select
              size="small"
              className="w-[78px]"
              disabled={!sortField}
              value={sortOrder}
              options={[
                { value: "desc", label: "降序" },
                { value: "asc", label: "升序" },
              ]}
              onChange={(value) => {
                setSortOrder(value);
                resetPage();
              }}
            />
          </div>
        </div>

        {drill ? (
          <div className="mt-3 flex items-center gap-2 rounded-[8px] bg-[#eff8ff] px-3 py-2 text-[12px] text-[#1570cd]">
            <span>正在查看「{drill.label}」命中的字段</span>
            <button
              type="button"
              className="ml-auto flex items-center"
              onClick={clearDrill}
            >
              <X size={13} /> 退出列视图
            </button>
          </div>
        ) : null}
      </div>

      {q && result?.explanation?.degradedToLike ? (
        <div className="rounded-[8px] bg-[#fffaeb] px-3 py-2 text-[12px] text-[#b54708]">
          关键词较短，本次使用模糊匹配。增加关键词可缩小结果范围。
        </div>
      ) : null}

      <div className="rounded-xl border border-solid border-[#eceef2] bg-white">
        <div className="flex items-center justify-between px-4 py-3 text-[13px] text-[#667085]">
          <span>
            {loading ? "检索中…" : `命中 ${result?.total ?? 0} 个实体`}
            {index.length > 0 ? `（已选 ${index.length} 个类型）` : ""}
          </span>
          {explain && result?.explanation ? (
            <Tooltip
              title={
                <div className="max-h-[260px] overflow-auto text-[11px]">
                  <div>后端：{result.explanation.backend}</div>
                  <div>布尔查询：{result.explanation.booleanQuery || "-"}</div>
                  <div>
                    SQL 条数：{result.explanation.sqlCount}（不随类型数增长）
                  </div>
                  <div>总数来源：{result.explanation.totalSource}</div>
                  {(result.explanation.notes ?? []).map((note) => (
                    <div key={note}>· {note}</div>
                  ))}
                </div>
              }
            >
              <span className="cursor-help text-[#667085]">查询诊断明细</span>
            </Tooltip>
          ) : null}
        </div>

        {!loading && items.length === 0 ? (
          error ? (
            <YakEmpty compact title="检索失败" description={error}>
              <Button
                size="small"
                onClick={() => setNonce((value) => value + 1)}
              >
                重试
              </Button>
            </YakEmpty>
          ) : (
            <YakEmpty
              compact
              title={q ? "没有命中的实体" : "目录里还没有数据"}
              description={
                q
                  ? "调整关键词或筛选条件后重试。"
                  : "到「采集与对账」创建并运行任务，或等待源域完成登记。"
              }
            />
          )
        ) : (
          <div className="divide-y divide-[#f2f4f7] border-0 border-t border-solid">
            {items.map((item) => {
              const visual = resolveTypeVisual(types, item.typeName);
              return (
                <div
                  key={item.id}
                  className="flex cursor-pointer items-start gap-3 px-4 py-3 transition-colors hover:bg-[#f9fafb]"
                  onClick={() => setDetail(item)}
                >
                  <span
                    className="mt-0.5 shrink-0 rounded-[4px] px-2 py-0.5 text-[12px] font-semibold text-white"
                    style={{ backgroundColor: visual.color }}
                  >
                    {item.typeDisplayName || visual.label}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[14px] font-medium text-[#101828]">
                      {assetTitle(item)}
                    </div>
                    <div className="mt-0.5 truncate text-[12px] text-[#98a2b3]">
                      {assetSubtitle(item)}
                    </div>
                    {item.summary ? (
                      <div className="mt-1 line-clamp-1 text-[12px] text-[#667085]">
                        {item.summary}
                      </div>
                    ) : null}
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1 max-sm:max-w-[100px]">
                    <Tag
                      color={
                        item.providerType === "REGISTERED"
                          ? "purple"
                          : "geekblue"
                      }
                    >
                      {item.providerType === "REGISTERED" ? "投影" : "采集"}
                    </Tag>
                    <span className="text-[12px] text-[#667085]">
                      {formatMetadataTime(item.updateTime)}
                    </span>
                    {(item.matchedColumnCount ?? 0) > 0 ? (
                      <Button
                        size="small"
                        type="link"
                        onClick={(event) => {
                          event.stopPropagation();
                          drillColumns(item);
                        }}
                      >
                        命中 {item.matchedColumnCount} 列
                      </Button>
                    ) : null}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        <div className="flex items-center justify-end gap-3 px-4 py-3">
          <span className="text-[12px] text-[#98a2b3]">
            第 {page} 页 · 每页 {PAGE_SIZE} 条
          </span>
          <Button
            size="small"
            disabled={page === 1 || loading}
            onClick={() => setPage(page - 1)}
          >
            上一页
          </Button>
          {offsetExhausted ? (
            <Tooltip
              title={`无游标时只能 offset 深翻，上限 ${MAX_OFFSET} 条；请改用排序列游标或收窄筛选`}
            >
              <Button size="small" disabled>
                下一页
              </Button>
            </Tooltip>
          ) : (
            <Button
              size="small"
              disabled={!hasMore || loading}
              onClick={() => setPage(page + 1)}
            >
              下一页
            </Button>
          )}
        </div>
      </div>

      <AssetDetailDrawer
        open={detail !== null}
        item={detail}
        types={types}
        onClose={() => setDetail(null)}
        onDrillColumns={(item) => {
          setDetail(null);
          drillColumns(item);
        }}
      />
    </div>
  );
};

const FieldSlot = ({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) => (
  <label className="flex items-center gap-1.5 text-[12px] text-[#667085]">
    <span className="shrink-0">{label}</span>
    {children}
  </label>
);

export default AssetExplorer;
