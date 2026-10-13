import { Alert, Button, Checkbox, Drawer, Grid, Input, Select, Skeleton, Space, Tag } from 'antd';
import { ArrowLeft, ArrowRight, Boxes, FileSearch, X } from 'lucide-react';
import CatalogEntityIcon from '@/components/asset-catalog/CatalogEntityIcon';
import Table from '@/components/ReadableTable';
import type { ColumnsType } from 'antd/es/table';
import {
  CatalogField,
  CatalogFilters,
  CatalogObjectIcon,
  CatalogResultToolbar,
  CatalogSearch,
  CatalogTypeButton,
  CatalogWorkbench,
  type CatalogDisplay,
  type CatalogFilterChip,
} from '@/components/asset-catalog/CatalogWorkbench';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import AssetDetailDrawer from '@/components/metadata/AssetDetailDrawer';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  ENTITY_STATUS_OPTIONS,
  PROVIDER_OPTIONS,
  assetSubtitle,
  assetTitle,
  resolveTypeVisual,
} from '@/components/metadata/typeVisual';
import { YakEmpty } from '@/components/ui';
import { listAllDataSources } from '@/services/data-source/api';
import { getEntityDetail, listEntityTypes, searchMetadata } from '@/services/metadata/api';
import { formatMetadataTime } from '@/services/metadata/presentation';
import type {
  EntityTypeView,
  MetadataFilter,
  MetadataSearchItem,
  MetadataSearchParams,
  MetadataSearchResult,
} from '@/services/metadata/types';

const PAGE_SIZE = 20;
/** 无游标时只能 offset 深翻，上限与服务端 SearchConditionBuilder.MAX_OFFSET 同值。 */
const MAX_OFFSET = 5000;

type SortOption = '' | NonNullable<MetadataSearchParams['sortField']>;

/** 原生筛选条已覆盖的键：元模型里同名字段不再重复出控件。 */
const NATIVE_FILTER_KEYS = new Set([
  'providerType',
  'entityStatus',
  'datasourceId',
  'dataSourceId',
  'parentAssetId',
  'databaseName',
  'layerCode',
  'owner',
  'ownerUser',
  'hasSummary',
]);

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: '', label: '默认排序' },
  { value: 'relevance', label: '相关性' },
  { value: 'updateTime', label: '更新时间' },
  { value: 'createTime', label: '入库时间' },
  { value: 'name', label: '名称' },
  { value: 'id', label: '登记顺序' },
];

/** 空值不参与请求：一个空串筛进去会被服务端当成"要筛空值"，而不是"不筛"。 */
const compactFilter = (filter: MetadataFilter): MetadataFilter => {
  const out: Record<string, unknown> = {};
  Object.entries(filter).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '' || value === '..') return;
    if (Array.isArray(value) && value.length === 0) return;
    if (typeof value === 'object' && !Array.isArray(value)) {
      const pair = value as { from?: string; to?: string };
      if (!pair.from && !pair.to) return;
      out[key] = `${pair.from ?? ''}..${pair.to ?? ''}`;
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

/** Metadata owns the paged search, type definitions and entity detail. */
const AssetExplorer = ({ searchable = false }: { searchable?: boolean }) => {
  const screens = Grid.useBreakpoint();
  const [display, setDisplay] = useState<CatalogDisplay>('list');
  const { currentProject } = useSecurityProject();
  const projectId = currentProject?.id;
  const readEpoch = useRef(0);
  // Navigation hints are never authority: validate the source in the active project's
  // DataSource Owner listing before searching. Search still enforces server-side RBAC.
  const sourceHint = useMemo(() => {
    const raw = new URLSearchParams(window.location.search).get('datasourceId');
    if (raw === null) return null;
    const parsed = Number(raw);
    return /^[1-9]\d*$/.test(raw) && Number.isSafeInteger(parsed) ? parsed : 'INVALID';
  }, []);
  const databaseHint = useMemo(() => {
    const raw = new URLSearchParams(window.location.search).get('databaseName');
    return raw && raw.length <= 255 ? raw : undefined;
  }, []);
  const [sourceHintProject, setSourceHintProject] = useState<string>();
  const [sourceHintStatus, setSourceHintStatus] = useState<'NONE' | 'VERIFYING' | 'VALID' | 'INVALID' | 'UNAVAILABLE'>(
    sourceHint === null ? 'NONE' : sourceHint === 'INVALID' ? 'INVALID' : 'VERIFYING',
  );
  const [types, setTypes] = useState<EntityTypeView[]>([]);
  const [typesError, setTypesError] = useState(false);
  const [sourceError, setSourceError] = useState(false);
  const [sourceRetry, setSourceRetry] = useState(0);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [dataSourceNames, setDataSourceNames] = useState<{ id: number; name: string }[]>([]);

  const [keyword, setKeyword] = useState('');
  const [q, setQ] = useState('');
  const [index, setIndex] = useState<string[]>(typeof sourceHint === 'number' ? ['table'] : []);
  const [filter, setFilter] = useState<MetadataFilter>(
    typeof sourceHint === 'number'
      ? {
          datasourceId: String(sourceHint),
          providerType: 'HARVESTED',
          ...(databaseHint ? { databaseName: databaseHint } : {}),
        }
      : {},
  );
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [sortField, setSortField] = useState<SortOption>('');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
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
    let active = true;
    ++readEpoch.current;
    setResult(null);
    setDetail(null);
    setTypes([]);
    setTypesError(false);
    setSourceError(false);
    setSourceHintProject(undefined);
    if (typeof sourceHint === 'number') setSourceHintStatus('VERIFYING');
    listEntityTypes('ENTITY')
      .then((list) => {
        if (active) setTypes((list ?? []).filter((type) => type.status === 'ACTIVE'));
      })
      .catch(() => {
        if (active) {
          setTypes([]);
          setTypesError(true);
        }
      });
    listAllDataSources()
      .then((result) => {
        if (!active) return;
        const sources = (result.bizData ?? [])
          .filter((ds) => ds.id != null)
          .map((ds) => ({ id: Number(ds.id), name: ds.name ?? String(ds.id) }));
        setDataSourceNames(sources);
        if (typeof sourceHint === 'number') {
          setSourceHintProject(String(projectId ?? ''));
          setSourceHintStatus(sources.some((ds) => ds.id === sourceHint) ? 'VALID' : 'INVALID');
        }
      })
      .catch(() => {
        if (!active) return;
        setDataSourceNames([]);
        setSourceError(true);
        if (typeof sourceHint === 'number') {
          setSourceHintProject(String(projectId ?? ''));
          setSourceHintStatus('UNAVAILABLE');
        }
      });
    return () => {
      active = false;
      ++readEpoch.current;
    };
  }, [sourceHint, projectId, sourceRetry]);

  useEffect(() => {
    if (sourceHint !== null && (sourceHintStatus !== 'VALID' || sourceHintProject !== String(projectId ?? ''))) return;
    const params = new URLSearchParams(window.location.search);
    const entityIdValue = params.get('entityId');
    const entityId = entityIdValue && /^\d+$/.test(entityIdValue) ? Number(entityIdValue) : undefined;
    if (!entityId) return;
    let current = true;
    getEntityDetail(entityId)
      .then(({ entity }) => {
        if (!current) return;
        const facts = entity.facts;
        if (
          typeof sourceHint === 'number' &&
          (Number(facts.dataSourceId) !== sourceHint ||
            (databaseHint && facts.databaseName !== databaseHint) ||
            entity.typeName !== 'table' ||
            facts.providerType !== 'HARVESTED')
        ) {
          setDetailError('目标实体与指定的数据源或数据库不一致，无法打开。');
          return;
        }
        setDetail({
          ...facts,
          id: entity.id,
          assetKey: String(facts.assetKey ?? params.get('assetKey') ?? ''),
          typeName: entity.typeName,
          attributes: entity.attributes,
        } as MetadataSearchItem);
      })
      .catch(() => {
        if (current) setDetailError('无法加载目标元数据实体，请重新打开详情。');
      });
    return () => {
      current = false;
    };
  }, [sourceHintStatus, sourceHintProject, sourceHint, databaseHint, projectId]);

  const filterKey = JSON.stringify(compactFilter(filter));

  const load = useCallback(async () => {
    if (typeof sourceHint === 'number' && sourceHintProject !== String(projectId ?? '')) return;
    if (sourceHintStatus === 'VERIFYING') return;
    const ticket = ++readEpoch.current;
    if (sourceHintStatus === 'INVALID' || sourceHintStatus === 'UNAVAILABLE') {
      setLoading(false);
      setResult(null);
      setError(
        sourceHintStatus === 'INVALID'
          ? '指定的数据源不存在或当前项目无权访问；未将其解释为零张表。'
          : '无法核验数据源与当前项目的关系；请重试读取来源。',
      );
      return;
    }
    setLoading(true);
    setResult(null);
    setError(null);
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
      if (ticket !== readEpoch.current) return;
      setResult(search);
      setError(null);
      const next = search.nextSearchAfter;
      setCursors((prev) => (next ? { ...prev, [page + 1]: next } : prev));
    } catch (cause) {
      if (ticket !== readEpoch.current) return;
      // 业务原因（筛选键不合法、类型名不存在等）已由全局提示给出，这里只负责把结果面清空，
      // 但必须把失败留在面上——否则空态会说"目录里还没有数据"，把服务端的错记到数据账上。
      setResult(null);
      setError(cause instanceof Error && cause.message ? cause.message : '检索失败');
    } finally {
      if (ticket === readEpoch.current) setLoading(false);
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
    sourceHintStatus,
    sourceHintProject,
    sourceHint,
    projectId,
  ]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    void load();
  }, [load]);

  const resetPage = () => {
    setPage(1);
    setCursors({ 1: undefined });
  };

  const toggleType = (typeName: string) => {
    setIndex((prev) => (prev.includes(typeName) ? prev.filter((item) => item !== typeName) : [...prev, typeName]));
    setDrill(null);
    setFilter((prev) => {
      const next = { ...prev };
      delete next.parentAssetId;
      return next;
    });
    resetPage();
  };

  /** 「命中 N 列」下钻：换 index 到 tableColumn + 按父实体收窄，同一接口不开第二条路径。 */
  const drillColumns = (item: MetadataSearchItem) => {
    setIndex(['tableColumn']);
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

  const setAttr = (fieldName: string, value: string | { from?: string; to?: string }) => {
    setFilter((prev) => ({ ...prev, [`attr.${fieldName}`]: value }) as MetadataFilter);
    resetPage();
  };

  const setNative = (key: string, value: string | boolean | undefined) => {
    setFilter((prev) => ({ ...prev, [key]: value }) as MetadataFilter);
    resetPage();
  };

  const resetAll = () => {
    setKeyword('');
    setQ('');
    setIndex([]);
    setFilter({});
    setSortField('');
    setSortOrder('desc');
    setDrill(null);
    resetPage();
  };

  // 属性筛选控件的候选：元模型说能筛（filterable）才给控件。
  const attrFields = useMemo(() => {
    const scoped = index.length > 0 ? types.filter((type) => index.includes(type.typeName)) : types;
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
        if (!field.filterable || field.deprecated || NATIVE_FILTER_KEYS.has(field.fieldName)) return;
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
  const facetByType = useMemo(() => Object.fromEntries(facets.map((facet) => [facet.typeName, facet.count])), [facets]);
  const offsetExhausted = !cursors[page + 1] && page * PAGE_SIZE >= MAX_OFFSET;
  const hasMore = Boolean(result?.nextSearchAfter) || (items.length > 0 && page * PAGE_SIZE < (result?.total ?? 0));

  const statusOptions = ENTITY_STATUS_OPTIONS.map((option) => ({
    ...option,
    label:
      (
        {
          Draft: '草稿',
          'In Review': '审核中',
          Approved: '已批准',
          Archived: '已归档',
          Deprecated: '已废弃',
          Rejected: '已拒绝',
          Unprocessed: '未处理',
        } as Record<string, string>
      )[option.value] ?? option.label,
  }));
  const nativeLabels: Record<string, string> = {
    providerType: '来源方式',
    entityStatus: '治理状态',
    datasourceId: '数据源',
    databaseName: '数据库',
    owner: '负责人',
    layerCode: '分层',
    hasSummary: '有描述',
    parentAssetId: '父实体',
  };
  const describeFilter = (key: string, value: unknown) => {
    if (key === 'datasourceId')
      return dataSourceNames.find((item) => String(item.id) === String(value))?.name ?? String(value);
    if (key === 'providerType')
      return PROVIDER_OPTIONS.find((option) => option.value === value)?.label ?? String(value);
    if (key === 'entityStatus') return statusOptions.find((option) => option.value === value)?.label ?? String(value);
    return value === true ? '是' : String(value);
  };
  const filterChips: CatalogFilterChip[] = [
    ...(q
      ? [
          {
            key: 'q',
            label: '关键词：' + q,
            onRemove: () => {
              setQ('');
              setKeyword('');
              resetPage();
            },
          },
        ]
      : []),
    ...index.map((name) => ({
      key: 'type-' + name,
      label: resolveTypeVisual(types, name).label,
      onRemove: () => toggleType(name),
    })),
    ...Object.entries(compactFilter(filter))
      .filter(([key]) => key !== 'parentAssetId')
      .map(([key, value]) => ({
        key,
        label:
          (nativeLabels[key] || attrFields.find((field) => 'attr.' + field.fieldName === key)?.displayName || key) +
          '：' +
          describeFilter(key, value),
        onRemove: () => {
          setFilter((prev) => {
            const next = { ...prev };
            delete (next as Record<string, unknown>)[key];
            return next;
          });
          resetPage();
        },
      })),
  ];
  const openDetail = (item: MetadataSearchItem) => {
    setDetailError(null);
    setDetail(item);
  };
  const sourceLabel = (item: MetadataSearchItem) =>
    dataSourceNames.find((ds) => ds.id === item.dataSourceId)?.name ||
    item.databaseName ||
    (item.providerType === 'REGISTERED' ? '业务登记' : '—');
  const columns: ColumnsType<MetadataSearchItem> = [
    {
      title: '实体名称',
      width: '30%',
      ellipsis: true,
      render: (_, item) => {
        return (
          <div className="catalog-name-cell">
            <CatalogObjectIcon>
              <CatalogEntityIcon
                kind={item.typeName}
                iconUrl={types.find((type) => type.typeName === item.typeName)?.iconUrl}
                size={19}
              />
            </CatalogObjectIcon>
            <div className="catalog-name-copy">
              <button
                type="button"
                className="catalog-object-name catalog-name-button"
                onClick={() => openDetail(item)}
              >
                {assetTitle(item)}
              </button>
              <div className="catalog-object-description" title={item.summary || assetSubtitle(item)}>
                {item.summary || assetSubtitle(item)}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      title: '类型',
      width: 110,
      render: (_, item) => (
        <span className="catalog-type-label">
          {item.typeDisplayName || resolveTypeVisual(types, item.typeName).label}
        </span>
      ),
    },
    {
      title: '来源',
      width: 150,
      render: (_, item) => (
        <div>
          <div>{sourceLabel(item)}</div>
          <div className="catalog-path" title={assetSubtitle(item)}>
            {assetSubtitle(item)}
          </div>
        </div>
      ),
    },
    {
      title: '负责人',
      width: 120,
      render: (_, item) =>
        item.ownerUser ? (
          <span className="catalog-owner">
            <span>{item.ownerUser.slice(0, 1)}</span>
            {item.ownerUser}
          </span>
        ) : (
          <span className="catalog-missing">未指定</span>
        ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 155,
      render: (time: string | null) => formatMetadataTime(time),
    },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      render: (_, item) => (
        <Space direction="vertical" size={0}>
          <Button type="link" size="small" onClick={() => openDetail(item)}>
            查看详情
          </Button>
          {(item.matchedColumnCount ?? 0) > 0 && (
            <Button type="link" size="small" onClick={() => drillColumns(item)}>
              命中 {item.matchedColumnCount} 列
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      {searchable && (
        <CatalogSearch
          value={keyword}
          placeholder="搜索表、字段、模型、指标或业务说明"
          loading={loading}
          onChange={(value) => {
            setKeyword(value);
            if (!value) {
              setQ('');
              resetPage();
            }
          }}
          onSearch={() => {
            setQ(keyword.trim());
            resetPage();
          }}
          onFilter={() => setAdvancedOpen(true)}
          filterCount={filterChips.length}
        />
      )}
      <CatalogFilters chips={filterChips} onReset={resetAll} />
      {detailError && (
        <Alert
          type="warning"
          showIcon
          message={detailError}
          closable
          onClose={() => setDetailError(null)}
          style={{ marginBottom: 16 }}
        />
      )}
      <CatalogWorkbench
        sidebar={
          <>
            <div className="catalog-sidebar-section">
              <h3>实体类型</h3>
              <div className="catalog-types">
                <CatalogTypeButton label="全部实体" icon={<Boxes />} active={!index.length} onClick={clearDrill} />
                {types.map((type) => (
                  <CatalogTypeButton
                    key={type.id}
                    label={resolveTypeVisual(types, type.typeName).label}
                    icon={<CatalogEntityIcon kind={type.typeName} iconUrl={type.iconUrl} size={16} />}
                    active={index.includes(type.typeName)}
                    count={facetByType[type.typeName]}
                    onClick={() => toggleType(type.typeName)}
                  />
                ))}
              </div>
              {typesError && (
                <div className="catalog-directory-caption">
                  类型读取失败
                  <Button size="small" type="link" onClick={() => setSourceRetry((value) => value + 1)}>
                    重试
                  </Button>
                </div>
              )}
            </div>
            <div className="catalog-sidebar-section catalog-sidebar-fields">
              <h3>数据来源</h3>
              <CatalogField label="数据源">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder={sourceError ? '来源读取失败' : '全部数据源'}
                  value={filter.datasourceId != null ? Number(filter.datasourceId) : undefined}
                  options={dataSourceNames.map((ds) => ({ value: ds.id, label: ds.name }))}
                  onChange={(value) => setNative('datasourceId', value == null ? undefined : String(value))}
                />
              </CatalogField>
              <CatalogField label="来源方式">
                <Select
                  allowClear
                  placeholder="全部来源"
                  value={typeof filter.providerType === 'string' ? filter.providerType : undefined}
                  options={PROVIDER_OPTIONS}
                  onChange={(value) => setNative('providerType', value)}
                />
              </CatalogField>
              {sourceError && (
                <Button size="small" type="link" onClick={() => setSourceRetry((value) => value + 1)}>
                  重试来源读取
                </Button>
              )}
            </div>
          </>
        }
      >
        <CatalogResultToolbar
          title={drill ? '匹配字段' : q ? '搜索结果' : filterChips.length ? '筛选结果' : '全部实体'}
          count={result?.total}
          loading={loading || sourceHintStatus === 'VERIFYING'}
          failed={!!error}
          display={display}
          onDisplay={setDisplay}
          onRefresh={() =>
            sourceHintStatus === 'UNAVAILABLE' ? setSourceRetry((value) => value + 1) : setNonce((value) => value + 1)
          }
          sort={
            <Select
              aria-label="实体排序"
              variant="borderless"
              style={{ width: 130 }}
              value={sortField}
              options={SORT_OPTIONS}
              onChange={(value) => {
                setSortField(value);
                resetPage();
              }}
            />
          }
        />
        {drill && (
          <div className="catalog-inline-notice">
            <FileSearch size={15} />
            <span>「{drill.label}」中匹配的字段</span>
            <Button type="text" size="small" icon={<X size={13} />} onClick={clearDrill}>
              返回实体
            </Button>
          </div>
        )}
        {q && result?.explanation?.degradedToLike && (
          <div className="catalog-inline-notice">关键词较短，增加关键词可以缩小匹配范围。</div>
        )}
        {error ? (
          <Alert
            type="error"
            showIcon
            message="无法读取元数据目录"
            description={error}
            action={
              <Button
                onClick={() =>
                  sourceHintStatus === 'UNAVAILABLE'
                    ? setSourceRetry((value) => value + 1)
                    : setNonce((value) => value + 1)
                }
              >
                重试
              </Button>
            }
          />
        ) : loading || sourceHintStatus === 'VERIFYING' ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : !items.length ? (
          <YakEmpty
            title={filterChips.length ? '没有匹配的实体' : '目录里还没有数据'}
            description={
              filterChips.length ? '试试其他关键词，或清空筛选条件。' : '完成元数据采集或业务登记后，数据会出现在这里。'
            }
          >
            {filterChips.length > 0 && <Button onClick={resetAll}>清空筛选</Button>}
          </YakEmpty>
        ) : display === 'list' && screens.md !== false ? (
          <Table<MetadataSearchItem>
            className="catalog-table"
            rowKey="id"
            columns={columns}
            dataSource={items}
            pagination={false}
            scroll={{ x: 'max-content' }}
          />
        ) : (
          <div className="catalog-result-card-grid">
            {items.map((item) => {
              const visual = resolveTypeVisual(types, item.typeName);
              return (
                <article key={item.id} className="catalog-result-card">
                  <div className="catalog-card-top">
                    <CatalogObjectIcon>
                      <CatalogEntityIcon
                        kind={item.typeName}
                        iconUrl={types.find((type) => type.typeName === item.typeName)?.iconUrl}
                        size={20}
                      />
                    </CatalogObjectIcon>
                    <Tag>{item.typeDisplayName || visual.label}</Tag>
                  </div>
                  <button
                    type="button"
                    className="catalog-object-name catalog-name-button"
                    onClick={() => openDetail(item)}
                  >
                    {assetTitle(item)}
                  </button>
                  <div className="catalog-card-description">{item.summary || '暂无描述'}</div>
                  <div className="catalog-path">{assetSubtitle(item)}</div>
                  <div className="catalog-card-footer">
                    <span>{sourceLabel(item)}</span>
                    <span>{formatMetadataTime(item.updateTime)}</span>
                  </div>
                  {(item.matchedColumnCount ?? 0) > 0 && (
                    <Button type="link" size="small" onClick={() => drillColumns(item)}>
                      查看 {item.matchedColumnCount} 个匹配字段
                    </Button>
                  )}
                </article>
              );
            })}
          </div>
        )}
        {result && result.total > 0 && (
          <div className="catalog-pagination">
            <span>
              共 {result.total} 项 · 每页 {PAGE_SIZE} 项
            </span>
            <Space>
              <Button
                size="small"
                icon={<ArrowLeft size={13} />}
                disabled={page === 1 || loading}
                onClick={() => setPage(page - 1)}
              >
                上一页
              </Button>
              <span>第 {page} 页</span>
              <Button size="small" disabled={!hasMore || loading || offsetExhausted} onClick={() => setPage(page + 1)}>
                下一页
                <ArrowRight size={13} />
              </Button>
            </Space>
            {offsetExhausted && hasMore && <span>结果较多，请缩小筛选范围后继续浏览。</span>}
          </div>
        )}
      </CatalogWorkbench>
      <Drawer
        title="筛选元数据"
        open={advancedOpen}
        onClose={() => setAdvancedOpen(false)}
        width={360}
        footer={
          <div className="catalog-drawer-actions">
            <Button onClick={resetAll}>清空条件</Button>
            <Button type="primary" onClick={() => setAdvancedOpen(false)}>
              查看结果
            </Button>
          </div>
        }
      >
        <CatalogField label="治理状态">
          <Select
            allowClear
            placeholder="全部状态"
            value={typeof filter.entityStatus === 'string' ? filter.entityStatus : undefined}
            options={statusOptions}
            onChange={(value) => setNative('entityStatus', value)}
          />
        </CatalogField>
        <CatalogField label="数据库">
          <Input
            allowClear
            placeholder="输入数据库名称"
            value={typeof filter.databaseName === 'string' ? filter.databaseName : ''}
            onChange={(event) => setNative('databaseName', event.target.value)}
          />
        </CatalogField>
        <CatalogField label="负责人">
          <Input
            allowClear
            placeholder="输入负责人"
            value={typeof filter.owner === 'string' ? filter.owner : ''}
            onChange={(event) => setNative('owner', event.target.value)}
          />
        </CatalogField>
        <CatalogField label="分层">
          <Input
            allowClear
            placeholder="输入分层编码"
            value={typeof filter.layerCode === 'string' ? filter.layerCode : ''}
            onChange={(event) => setNative('layerCode', event.target.value)}
          />
        </CatalogField>
        <CatalogField label="描述">
          <Checkbox
            checked={filter.hasSummary === true}
            onChange={(event) => setNative('hasSummary', event.target.checked ? true : undefined)}
          >
            只看有描述的实体
          </Checkbox>
        </CatalogField>
        {attrFields.map((field) => (
          <CatalogField key={field.fieldName} label={field.displayName || field.fieldName}>
            {field.matchType === 'range' ? (
              <Space.Compact>
                <Input
                  placeholder="起始值"
                  aria-label={(field.displayName || field.fieldName) + '起始值'}
                  value={String(filter[('attr.' + field.fieldName) as keyof MetadataFilter] ?? '').split('..')[0]}
                  onChange={(event) =>
                    setAttr(
                      field.fieldName,
                      event.target.value +
                        '..' +
                        (String(filter[('attr.' + field.fieldName) as keyof MetadataFilter] ?? '').split('..')[1] ||
                          ''),
                    )
                  }
                />
                <Input
                  placeholder="结束值"
                  aria-label={(field.displayName || field.fieldName) + '结束值'}
                  value={String(filter[('attr.' + field.fieldName) as keyof MetadataFilter] ?? '').split('..')[1] || ''}
                  onChange={(event) =>
                    setAttr(
                      field.fieldName,
                      (String(filter[('attr.' + field.fieldName) as keyof MetadataFilter] ?? '').split('..')[0] || '') +
                        '..' +
                        event.target.value,
                    )
                  }
                />
              </Space.Compact>
            ) : (
              <Input
                allowClear
                value={String(filter[('attr.' + field.fieldName) as keyof MetadataFilter] ?? '')}
                onChange={(event) => setAttr(field.fieldName, event.target.value)}
              />
            )}
          </CatalogField>
        ))}
        <CatalogField label="排序方向">
          <Select
            disabled={!sortField}
            value={sortOrder}
            options={[
              { value: 'desc', label: '降序' },
              { value: 'asc', label: '升序' },
            ]}
            onChange={(value) => {
              setSortOrder(value);
              resetPage();
            }}
          />
        </CatalogField>
        <Checkbox
          checked={explain}
          onChange={(event) => {
            setExplain(event.target.checked);
            resetPage();
          }}
        >
          查询诊断
        </Checkbox>
        {explain && result?.explanation && (
          <div className="catalog-directory-caption">
            查询方式：{result.explanation.backend} · SQL 数量：{result.explanation.sqlCount}
          </div>
        )}
      </Drawer>
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
    </>
  );
};

export default AssetExplorer;
