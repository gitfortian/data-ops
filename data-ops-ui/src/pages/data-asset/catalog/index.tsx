import Table from '@/components/ReadableTable';
import { ArrowLeft, Boxes, Folder, Layers3, MoreHorizontal, Plus, ShieldCheck } from 'lucide-react';
import CatalogEntityIcon from '@/components/asset-catalog/CatalogEntityIcon';
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
import {
  Button,
  Alert,
  Dropdown,
  Grid,
  Pagination,
  Modal,
  message,
  Drawer,
  Skeleton,
  Select,
  Tooltip,
  Tree,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { classifyAssetListFailure, type AssetListReadFailure } from '../read-state';
import { useLatestAssetListRequest } from '../useLatestAssetListRequest';
import { history, useSearchParams } from '@umijs/max';

import { YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  batchMoveAssetsDirectory,
  deleteAsset,
  getDirectoryTree,
  ignoreAssets,
  listAssetTags,
  pageAssets,
} from '@/services/data-asset/api';
import type { AssetRecord, AssetStatus, DirNode, HealthGrade, AssetTagRecord } from '@/services/data-asset/types';
import { listSemanticLayers } from '@/services/semantic/api';
import type { SemanticLayerRecord } from '@/services/semantic/types';
import {
  ASSET_SOURCE_TYPE_LABELS,
  collectDirOptions,
  ASSET_TYPE_LABELS,
  formatAssetTime,
  healthGradeColor,
  SORT_LABELS,
  toTreeData,
  ASSET_STATUS_LABELS,
} from '../constants';
import AssetStatusTag from '../components/AssetStatusTag';
import ManualRegisterModal from '../components/ManualRegisterModal';
import OfflineModal from '../components/OfflineModal';
import PublishPrecheckModal from '../components/PublishPrecheckModal';
import AssetExplorer from '@/components/metadata/AssetExplorer';

const PUBLISHABLE: AssetStatus[] = ['PENDING', 'OFFLINE', 'IGNORED'];
const IGNORABLE: AssetStatus[] = ['PENDING', 'OFFLINE'];
const DELETABLE: AssetStatus[] = ['OFFLINE', 'SOURCE_GONE'];
/** 手工登记无源域对象:未上架可直接撤销登记(与后端 48003 规则一致)。 */
const MANUAL_DELETABLE: AssetStatus[] = ['PENDING', 'OFFLINE', 'IGNORED'];

const isDeletable = (record: AssetRecord) =>
  DELETABLE.includes(record.status) || (record.sourceType === 'MANUAL' && MANUAL_DELETABLE.includes(record.status));

type CatalogView = '台账资产' | '元数据实体';

const AssetCatalogPage = () => {
  const screens = Grid.useBreakpoint();
  const { can } = usePermissionAccess();
  const canUpdate = can('data-asset:update');
  const canCreate = can('data-asset:create');
  const canDelete = can('data-asset:delete');

  // 概览驾驶舱待办深链:?statuses=SOURCE_GONE&grades=D;实体视图深链:?view=entity
  const [entryParams, setEntryParams] = useSearchParams();
  const returnAssetIdValue = entryParams.get('returnAssetId');
  const returnAssetId = returnAssetIdValue && /^\d+$/.test(returnAssetIdValue) ? Number(returnAssetIdValue) : undefined;
  const view: CatalogView = entryParams.get('view') === 'entity' ? '元数据实体' : '台账资产';

  const switchView = (next: CatalogView) => {
    const params = new URLSearchParams(entryParams);
    if (next === '元数据实体') {
      params.set('view', 'entity');
    } else {
      params.delete('view');
    }
    setEntryParams(params, { replace: true });
  };

  const [records, setRecords] = useState<AssetRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<AssetListReadFailure | null>(null);
  const beginLatestRequest = useLatestAssetListRequest();
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [keyword, setKeyword] = useState('');
  const [assetTypes, setAssetTypes] = useState<string[]>([]);
  const [layerCodes, setLayerCodes] = useState<string[]>([]);
  const [statuses, setStatuses] = useState<AssetStatus[]>(
    () => (entryParams.get('statuses')?.split(',').filter(Boolean) as AssetStatus[]) ?? [],
  );
  const [grades, setGrades] = useState<HealthGrade[]>(
    () => (entryParams.get('grades')?.split(',').filter(Boolean) as HealthGrade[]) ?? [],
  );
  const [tagIds, setTagIds] = useState<number[]>([]);
  const [sortBy, setSortBy] = useState('');
  const [directoryId, setDirectoryId] = useState<number | undefined>();

  const [viewMode, setViewMode] = useState<CatalogDisplay>('list');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [selectedRows, setSelectedRows] = useState<AssetRecord[]>([]);

  const [dirTree, setDirTree] = useState<DirNode[]>([]);
  const [directoryError, setDirectoryError] = useState<AssetListReadFailure | null>(null);
  const [directoryLoading, setDirectoryLoading] = useState(true);
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [tags, setTags] = useState<AssetTagRecord[]>([]);

  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardAssets, setWizardAssets] = useState<AssetRecord[]>([]);
  const [manualOpen, setManualOpen] = useState(false);
  const [batchMoveOpen, setBatchMoveOpen] = useState(false);
  const [batchMoveTarget, setBatchMoveTarget] = useState<number | undefined>();
  const [batchMoveSaving, setBatchMoveSaving] = useState(false);
  const [offlineTarget, setOfflineTarget] = useState<AssetRecord[] | null>(null);

  const load = useCallback(async () => {
    const isCurrent = beginLatestRequest();
    if (view !== '台账资产') {
      setLoading(false);
      return;
    }
    setLoading(true);
    setLoadError(null);
    setRecords([]);
    setTotal(0);
    setSelectedIds([]);
    setSelectedRows([]);
    try {
      const result = await pageAssets({
        pageNo,
        pageSize,
        keyword: keyword.trim() || undefined,
        assetTypes: assetTypes.length ? assetTypes : undefined,
        layerCodes: layerCodes.length ? layerCodes : undefined,
        statuses: statuses.length ? statuses : undefined,
        grades: grades.length ? grades : undefined,
        tagIds: tagIds.length ? tagIds : undefined,
        directoryId,
        sortBy: (sortBy || undefined) as 'TIME' | 'VIEWS' | 'NAME' | undefined,
      });
      if (isCurrent()) {
        setRecords(result.records);
        setTotal(result.total);
      }
    } catch (error) {
      if (isCurrent()) setLoadError(classifyAssetListFailure(error));
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [
    view,
    pageNo,
    pageSize,
    keyword,
    assetTypes,
    layerCodes,
    statuses,
    grades,
    tagIds,
    directoryId,
    sortBy,
    beginLatestRequest,
  ]);

  useEffect(() => {
    void load();
  }, [load]);

  const reloadDirectories = useCallback(async () => {
    setDirectoryLoading(true);
    setDirectoryError(null);
    setDirTree([]);
    try {
      setDirTree(await getDirectoryTree());
    } catch (error) {
      setDirectoryError(classifyAssetListFailure(error));
    } finally {
      setDirectoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (view !== '台账资产') return;
    void reloadDirectories();
    listSemanticLayers()
      .then((list) => setLayers(list.filter((layer) => layer.status === 'ENABLED')))
      .catch(() => setLayers([]));
    listAssetTags()
      .then(setTags)
      .catch(() => setTags([]));
  }, [view, reloadDirectories]);

  const treeData = useMemo(() => toTreeData(dirTree), [dirTree]);

  const refresh = useCallback(() => {
    setSelectedIds([]);
    setSelectedRows([]);
    void load();
  }, [load]);

  const openWizard = (assets: AssetRecord[]) => {
    setWizardAssets(assets);
    setWizardOpen(true);
  };

  const runIgnore = (assets: AssetRecord[]) => {
    Modal.confirm({
      title: '忽略资产',
      content: `忽略后对账不再把「${assets.map((a) => a.name).join('、')}」复活为待上架；源域指纹变化时会自动回到待上架。确定忽略？`,
      okText: '忽略',
      cancelText: '取消',
      onOk: async () => {
        try {
          const count = await ignoreAssets(assets.map((asset) => asset.id));
          message.success(`已忽略 ${count} 个资产`);
          refresh();
        } catch {
          // 已上架须先下架(48016),提示已由全局展示
        }
      },
    });
  };

  const runBatchMove = () => {
    if (!canUpdate || selectedIds.length === 0) return;
    setBatchMoveTarget(undefined);
    setBatchMoveOpen(true);
  };

  const confirmBatchMove = async () => {
    if (!canUpdate || !selectedIds.length || batchMoveTarget == null || batchMoveSaving) return;
    setBatchMoveSaving(true);
    try {
      // Root-level placement is an explicit choice; no selection must never mean root.
      const destination = batchMoveTarget === 0 ? null : batchMoveTarget;
      const count = await batchMoveAssetsDirectory(selectedIds, destination);
      message.success(`已移动 ${count} 个资产`);
      setBatchMoveOpen(false);
      setBatchMoveTarget(undefined);
      refresh();
    } catch {
      // Leave the dialog open for retry; server enforces project and directory validity.
    } finally {
      setBatchMoveSaving(false);
    }
  };

  const runDelete = (record: AssetRecord) => {
    Modal.confirm({
      title: '删除台账行',
      content: `确定删除「${record.name}」？删除后该资产不再出现在台账中，且不可通过界面恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAsset(record.id);
          message.success('已删除台账行');
          refresh();
        } catch {
          // 状态不满足(48003)提示已由全局展示
        }
      },
    });
  };

  const typeLabel = (record: AssetRecord) =>
    (record.assetType && ASSET_TYPE_LABELS[record.assetType]) || ASSET_SOURCE_TYPE_LABELS[record.sourceType];
  const openAsset = (record: AssetRecord) => history.push('/data-asset/detail/' + record.id);
  const columns: ColumnsType<AssetRecord> = [
    {
      title: '资产名称',
      dataIndex: 'name',
      width: '30%',
      ellipsis: true,
      render: (_, record) => (
        <div className="catalog-name-cell">
          <CatalogObjectIcon>
            <CatalogEntityIcon
              kind={record.sourceType === 'MODEL' ? 'MODEL' : (record.assetType ?? record.sourceType)}
              size={19}
            />
          </CatalogObjectIcon>
          <div className="catalog-name-copy">
            <a
              className="catalog-object-name"
              href={'/data-asset/detail/' + record.id}
              onClick={(event) => {
                event.preventDefault();
                openAsset(record);
              }}
            >
              {record.name}
            </a>
            <div className="catalog-object-description" title={record.description || record.assetKey}>
              {record.description || record.assetKey}
            </div>
          </div>
        </div>
      ),
    },
    {
      title: '类型 / 分层',
      width: 120,
      render: (_, record) => (
        <div>
          <span className="catalog-type-label">{typeLabel(record)}</span>
          {record.layerCode && <div className="catalog-path">{record.layerCode}</div>}
        </div>
      ),
    },
    {
      title: '负责人',
      dataIndex: 'owner',
      width: 125,
      render: (owner?: string) =>
        owner ? (
          <span className="catalog-owner">
            <span>{owner.slice(0, 1)}</span>
            {owner}
          </span>
        ) : (
          <span className="catalog-missing">待指定</span>
        ),
    },
    {
      title: '上架状态',
      dataIndex: 'status',
      width: 100,
      render: (status: AssetStatus) => <AssetStatusTag status={status} />,
    },
    {
      title: '健康度',
      width: 90,
      render: (_, record) =>
        record.healthScore == null ? (
          <span className="catalog-missing">未评估</span>
        ) : (
          <Tooltip title="资产治理健康度">
            <span style={{ color: healthGradeColor(record.healthGrade) }}>
              {record.healthScore} <small>分 · {record.healthGrade ?? '—'}</small>
            </span>
          </Tooltip>
        ),
    },
    { title: '源更新时间', dataIndex: 'sourceUpdatedAt', width: 160, render: (time?: string) => formatAssetTime(time) },
    {
      title: '',
      key: 'action',
      width: 55,
      fixed: 'right',
      render: (_, record) => (
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              { key: 'detail', label: '查看详情', onClick: () => openAsset(record) },
              {
                key: 'publish',
                label: '上架',
                disabled: !canUpdate || !PUBLISHABLE.includes(record.status),
                onClick: () => openWizard([record]),
              },
              {
                key: 'offline',
                label: '下架',
                disabled: !canUpdate || record.status !== 'PUBLISHED',
                onClick: () => setOfflineTarget([record]),
              },
              {
                key: 'ignore',
                label: '忽略',
                disabled: !canUpdate || !IGNORABLE.includes(record.status),
                onClick: () => runIgnore([record]),
              },
              {
                key: 'delete',
                label: '删除',
                danger: true,
                disabled: !canDelete || !isDeletable(record),
                onClick: () => runDelete(record),
              },
            ],
          }}
        >
          <Button
            type="text"
            size="small"
            aria-label={'更多操作：' + record.name}
            icon={<MoreHorizontal size={17} />}
          />
        </Dropdown>
      ),
    },
  ];

  const resetFilters = () => {
    setKeyword('');
    setSearchText('');
    setAssetTypes([]);
    setLayerCodes([]);
    setStatuses([]);
    setGrades([]);
    setTagIds([]);
    setDirectoryId(undefined);
    setPageNo(1);
  };
  const removeFilter = (action: () => void) => {
    action();
    setPageNo(1);
  };
  const selectedDirectory = collectDirOptions(dirTree).find((option) => option.value === directoryId);
  const filterChips: CatalogFilterChip[] = [
    ...(keyword
      ? [
          {
            key: 'keyword',
            label: '关键词：' + keyword,
            onRemove: () =>
              removeFilter(() => {
                setKeyword('');
                setSearchText('');
              }),
          },
        ]
      : []),
    ...assetTypes.map((type) => ({
      key: 'type-' + type,
      label: ASSET_TYPE_LABELS[type as keyof typeof ASSET_TYPE_LABELS] ?? type,
      onRemove: () => removeFilter(() => setAssetTypes(assetTypes.filter((value) => value !== type))),
    })),
    ...statuses.map((status) => ({
      key: 'status-' + status,
      label: ASSET_STATUS_LABELS[status],
      onRemove: () => removeFilter(() => setStatuses(statuses.filter((value) => value !== status))),
    })),
    ...layerCodes.map((code) => ({
      key: 'layer-' + code,
      label: '分层：' + code,
      onRemove: () => removeFilter(() => setLayerCodes(layerCodes.filter((value) => value !== code))),
    })),
    ...grades.map((grade) => ({
      key: 'grade-' + grade,
      label: '健康度：' + grade,
      onRemove: () => removeFilter(() => setGrades(grades.filter((value) => value !== grade))),
    })),
    ...tagIds.map((id) => ({
      key: 'tag-' + id,
      label: tags.find((tag) => tag.id === id)?.tagName ?? '标签 ' + id,
      onRemove: () => removeFilter(() => setTagIds(tagIds.filter((value) => value !== id))),
    })),
    ...(directoryId != null
      ? [
          {
            key: 'directory',
            label: '目录：' + (selectedDirectory?.label ?? directoryId),
            onRemove: () => removeFilter(() => setDirectoryId(undefined)),
          },
        ]
      : []),
  ];

  const hasSelection = selectedIds.length > 0;
  const publishableSelected = selectedRows.filter((row) => PUBLISHABLE.includes(row.status));
  const publishedSelected = selectedRows.filter((row) => row.status === 'PUBLISHED');
  const ignorableSelected = selectedRows.filter((row) => IGNORABLE.includes(row.status));

  return (
    <main className="asset-catalog">
      <header className="catalog-page-heading">
        <div>
          <h1>资产目录</h1>
          <p>发现数据，了解来源，让每一次使用都有依据。</p>
        </div>
        <div className="catalog-heading-actions">
          {returnAssetId && Number.isSafeInteger(returnAssetId) && returnAssetId > 0 && (
            <Button icon={<ArrowLeft size={14} />} onClick={() => history.push('/data-asset/detail/' + returnAssetId)}>
              返回资产详情
            </Button>
          )}
          {view === '台账资产' && canCreate && (
            <Button type="primary" aria-label="登记资产" icon={<Plus size={16} />} onClick={() => setManualOpen(true)}>
              <span className="catalog-register-label">登记资产</span>
            </Button>
          )}
        </div>
      </header>
      <nav className="catalog-scope-tabs" aria-label="目录范围">
        <button type="button" aria-pressed={view === '台账资产'} onClick={() => switchView('台账资产')}>
          <ShieldCheck size={16} />
          资产台账
        </button>
        <button type="button" aria-pressed={view === '元数据实体'} onClick={() => switchView('元数据实体')}>
          <Layers3 size={16} />
          元数据实体
        </button>
        <span>{view === '台账资产' ? '查看归属、上架状态与治理健康度' : '查看已采集和登记的表、字段及业务对象'}</span>
      </nav>
      {view === '元数据实体' ? (
        <AssetExplorer searchable />
      ) : (
        <>
          <CatalogSearch
            value={searchText}
            placeholder="搜索资产名称、描述、负责人或标签"
            loading={loading}
            onChange={(value) => {
              setSearchText(value);
              if (!value) {
                setKeyword('');
                setPageNo(1);
              }
            }}
            onSearch={() => {
              setKeyword(searchText.trim());
              setPageNo(1);
            }}
            onFilter={() => setAdvancedOpen(true)}
            filterCount={filterChips.length}
          />
          <CatalogFilters chips={filterChips} onReset={resetFilters} />
          <CatalogWorkbench
            sidebar={
              <>
                <div className="catalog-sidebar-section">
                  <h3>资产类型</h3>
                  <div className="catalog-types">
                    <CatalogTypeButton
                      label="全部资产"
                      icon={<Boxes />}
                      active={!assetTypes.length}
                      onClick={() => {
                        setAssetTypes([]);
                        setPageNo(1);
                      }}
                    />
                    {Object.entries(ASSET_TYPE_LABELS).map(([value, label]) => (
                      <CatalogTypeButton
                        key={value}
                        label={label}
                        icon={<CatalogEntityIcon kind={value} size={16} />}
                        active={assetTypes.includes(value)}
                        onClick={() => {
                          setAssetTypes(
                            assetTypes.includes(value)
                              ? assetTypes.filter((type) => type !== value)
                              : [...assetTypes, value],
                          );
                          setPageNo(1);
                        }}
                      />
                    ))}
                  </div>
                </div>
                <div className="catalog-sidebar-section catalog-sidebar-fields">
                  <h3>治理筛选</h3>
                  <CatalogField label="上架状态">
                    <Select
                      mode="multiple"
                      allowClear
                      placeholder="全部状态"
                      value={statuses}
                      maxTagCount="responsive"
                      options={Object.entries(ASSET_STATUS_LABELS).map(([value, label]) => ({ value, label }))}
                      onChange={(value) => {
                        setStatuses(value);
                        setPageNo(1);
                      }}
                    />
                  </CatalogField>
                </div>
                <div className="catalog-sidebar-section">
                  <h3>业务目录</h3>
                  <Button
                    className="catalog-directory-toggle"
                    type="text"
                    aria-expanded={directoryOpen}
                    icon={<Folder size={15} />}
                    onClick={() => setDirectoryOpen(!directoryOpen)}
                  >
                    业务目录{selectedDirectory ? ' · ' + selectedDirectory.label : ''}
                  </Button>
                  <div className="catalog-directory-body" data-expanded={directoryOpen}>
                    <CatalogTypeButton
                      label="全部目录"
                      icon={<Folder />}
                      active={directoryId == null}
                      onClick={() => {
                        setDirectoryId(undefined);
                        setPageNo(1);
                      }}
                    />
                    {directoryLoading ? (
                      <Skeleton active paragraph={{ rows: 3 }} title={false} />
                    ) : directoryError ? (
                      <div className="catalog-directory-caption">
                        {directoryError === 'FORBIDDEN' ? '无权读取目录' : '目录读取失败'}
                        <Button type="link" size="small" onClick={() => void reloadDirectories()}>
                          重试
                        </Button>
                      </div>
                    ) : treeData.length ? (
                      <Tree
                        treeData={treeData}
                        selectedKeys={directoryId != null ? [directoryId] : []}
                        blockNode
                        defaultExpandAll
                        onSelect={(keys) => {
                          setDirectoryId(keys.length ? Number(keys[0]) : undefined);
                          setPageNo(1);
                        }}
                      />
                    ) : (
                      <div className="catalog-directory-caption">
                        尚未创建目录
                        <Button type="link" size="small" onClick={() => history.push('/data-asset/taxonomy')}>
                          管理目录
                        </Button>
                      </div>
                    )}
                  </div>
                </div>
              </>
            }
          >
            <CatalogResultToolbar
              title={keyword ? '搜索结果' : filterChips.length ? '筛选结果' : '全部资产'}
              count={total}
              loading={loading}
              failed={!!loadError}
              display={viewMode}
              onDisplay={setViewMode}
              onRefresh={load}
              sort={
                <Select
                  aria-label="资产排序"
                  variant="borderless"
                  value={sortBy}
                  style={{ width: 160 }}
                  options={Object.entries(SORT_LABELS).map(([value, label]) => ({
                    value,
                    label: value ? label : '推荐排序',
                  }))}
                  onChange={(value) => {
                    setSortBy(value);
                    setPageNo(1);
                  }}
                />
              }
            />
            {canUpdate && hasSelection && (
              <div className="catalog-batch-bar">
                <span>已选 {selectedIds.length} 项</span>
                <Button
                  size="small"
                  disabled={!publishableSelected.length}
                  onClick={() => openWizard(publishableSelected)}
                >
                  上架（{publishableSelected.length}）
                </Button>
                <Button
                  size="small"
                  disabled={!publishedSelected.length}
                  onClick={() => setOfflineTarget(publishedSelected)}
                >
                  下架（{publishedSelected.length}）
                </Button>
                <Button size="small" onClick={runBatchMove}>
                  移动目录
                </Button>
                <Button size="small" disabled={!ignorableSelected.length} onClick={() => runIgnore(ignorableSelected)}>
                  忽略（{ignorableSelected.length}）
                </Button>
                <Button
                  type="text"
                  size="small"
                  onClick={() => {
                    setSelectedIds([]);
                    setSelectedRows([]);
                  }}
                >
                  取消选择
                </Button>
              </div>
            )}
            {loadError ? (
              <Alert
                type="error"
                showIcon
                message={loadError === 'FORBIDDEN' ? '无权读取资产目录' : '资产列表读取失败'}
                description="请重试，或检查当前项目的访问权限。"
                action={<Button onClick={load}>重试</Button>}
              />
            ) : loading ? (
              <Skeleton active paragraph={{ rows: 8 }} />
            ) : !records.length ? (
              <YakEmpty
                title="没有符合条件的资产"
                description={
                  filterChips.length ? '试试其他关键词，或清空筛选条件。' : '完成资产盘点后，数据会出现在这里。'
                }
              >
                <Button onClick={filterChips.length ? resetFilters : () => history.push('/data-asset/inventory')}>
                  {filterChips.length ? '清空筛选' : '前往盘点上架'}
                </Button>
              </YakEmpty>
            ) : viewMode === 'list' && screens.md !== false ? (
              <Table<AssetRecord>
                className="catalog-table"
                rowKey="id"
                columns={columns}
                dataSource={records}
                pagination={false}
                scroll={{ x: 'max-content' }}
                rowSelection={
                  canUpdate
                    ? {
                        selectedRowKeys: selectedIds,
                        onChange: (keys, rows) => {
                          setSelectedIds(keys.map(Number));
                          setSelectedRows(rows);
                        },
                      }
                    : undefined
                }
              />
            ) : (
              <AssetCardGrid records={records} onOpen={openAsset} />
            )}
            {!loadError && total > 0 && (
              <div className="catalog-pagination">
                <span>共 {total} 项资产</span>
                <Pagination
                  size="small"
                  current={pageNo}
                  pageSize={pageSize}
                  total={total}
                  showSizeChanger
                  onChange={(next, size) => {
                    setPageNo(size !== pageSize ? 1 : next);
                    setPageSize(size);
                  }}
                />
              </div>
            )}
          </CatalogWorkbench>
          <Drawer
            title="筛选资产"
            open={advancedOpen}
            onClose={() => setAdvancedOpen(false)}
            width={360}
            footer={
              <div className="catalog-drawer-actions">
                <Button onClick={resetFilters}>清空条件</Button>
                <Button type="primary" onClick={() => setAdvancedOpen(false)}>
                  查看结果
                </Button>
              </div>
            }
          >
            <CatalogField label="分层">
              <Select
                mode="multiple"
                allowClear
                placeholder="全部分层"
                value={layerCodes}
                options={layers.map((layer) => ({ value: layer.code, label: layer.name }))}
                onChange={(value) => {
                  setLayerCodes(value);
                  setPageNo(1);
                }}
              />
            </CatalogField>
            <CatalogField label="健康度">
              <Select
                mode="multiple"
                allowClear
                placeholder="全部等级"
                value={grades}
                options={['A', 'B', 'C', 'D'].map((value) => ({ value, label: value + ' 级' }))}
                onChange={(value) => {
                  setGrades(value);
                  setPageNo(1);
                }}
              />
            </CatalogField>
            <CatalogField label="业务标签">
              <Select
                mode="multiple"
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="全部标签"
                value={tagIds}
                options={tags.map((tag) => ({ value: tag.id, label: tag.tagName }))}
                onChange={(value) => {
                  setTagIds(value);
                  setPageNo(1);
                }}
              />
            </CatalogField>
          </Drawer>
        </>
      )}

      <Modal
        open={batchMoveOpen}
        title="批量调整资产目录"
        width={520}
        okText="确认移动"
        cancelText="取消"
        okButtonProps={{ disabled: batchMoveTarget == null || !canUpdate || !selectedIds.length }}
        confirmLoading={batchMoveSaving}
        maskClosable={!batchMoveSaving}
        keyboard={!batchMoveSaving}
        onOk={() => void confirmBatchMove()}
        onCancel={() => {
          if (batchMoveSaving) return;
          setBatchMoveOpen(false);
          setBatchMoveTarget(undefined);
        }}
        destroyOnClose
      >
        <div className="mb-4 mt-3 rounded-xl border border-solid border-[#e4ebf4] bg-[#f4f7fb] px-4 py-3 text-[12px] leading-5 text-[#73839a]">
          本次仅改变已选 {selectedIds.length} 项资产的目录归属，不改变来源实体、负责人或发布状态。
          必须明确选择目标；取消目录归属也必须主动选中“目录根级”。
        </div>
        <div className="mb-2 text-[13px] font-medium text-[#34465c]">目标目录</div>
        <Select
          showSearch
          optionFilterProp="label"
          className="w-full"
          placeholder="明确选择目标目录"
          value={batchMoveTarget}
          onChange={setBatchMoveTarget}
          options={[{ value: 0, label: '目录根级（取消目录归属）' }, ...collectDirOptions(dirTree)]}
        />
      </Modal>
      <PublishPrecheckModal
        open={wizardOpen}
        assets={wizardAssets}
        onClose={() => setWizardOpen(false)}
        onDone={() => {
          setWizardOpen(false);
          refresh();
        }}
      />
      <ManualRegisterModal
        open={manualOpen}
        onClose={() => setManualOpen(false)}
        onCreated={() => {
          setManualOpen(false);
          refresh();
        }}
      />
      <OfflineModal
        open={Boolean(offlineTarget)}
        assets={offlineTarget ?? []}
        onClose={() => setOfflineTarget(null)}
        onDone={() => {
          setOfflineTarget(null);
          refresh();
        }}
      />
    </main>
  );
};

const AssetCardGrid = ({ records, onOpen }: { records: AssetRecord[]; onOpen: (record: AssetRecord) => void }) => (
  <div className="catalog-result-card-grid">
    {records.map((record) => (
      <button type="button" key={record.id} className="catalog-result-card" onClick={() => onOpen(record)}>
        <div className="catalog-card-top">
          <CatalogObjectIcon>
            <CatalogEntityIcon
              kind={record.sourceType === 'MODEL' ? 'MODEL' : (record.assetType ?? record.sourceType)}
              size={20}
            />
          </CatalogObjectIcon>
          <AssetStatusTag status={record.status} />
        </div>
        <div className="catalog-object-name">{record.name}</div>
        <div className="catalog-card-description">{record.description || '暂无描述'}</div>
        <div className="catalog-path">{record.assetKey}</div>
        <div className="catalog-card-footer">
          <span>{record.owner || '待指定负责人'}</span>
          <span>
            {(record.assetType && ASSET_TYPE_LABELS[record.assetType]) || ASSET_SOURCE_TYPE_LABELS[record.sourceType]}
            {record.layerCode && ' · ' + record.layerCode}
          </span>
        </div>
      </button>
    ))}
  </div>
);

/** Remount on Project/permission change, discarding old filters and selections. */
export default function ScopedAssetCatalogPage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <AssetCatalogPage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
