import Table from '@/components/ReadableTable';
import {
  Button,
  Alert,
  Dropdown,
  Grid,
  Pagination,
  Input,
  Modal,
  message,
  Segmented,
  Select,
  Space,
  Tag,
  Tooltip,
  Tree,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { history, useSearchParams } from '@umijs/max';

import { YakButton, YakEmpty } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  batchMoveAssetsDirectory,
  deleteAsset,
  getDirectoryTree,
  ignoreAssets,
  listAssetTags,
  pageAssets,
} from '@/services/data-asset/api';
import type {
  AssetRecord,
  AssetStatus,
  DirNode,
  HealthGrade,
  AssetTagRecord,
} from '@/services/data-asset/types';
import { listSemanticLayers } from '@/services/semantic/api';
import type { SemanticLayerRecord } from '@/services/semantic/types';
import {
  ASSET_SOURCE_TYPE_LABELS,
  ASSET_TYPE_LABELS,
  formatAssetTime,
  healthGradeColor,
  SORT_LABELS,
  toTreeData,
  type DirTreeNode,
} from '../constants';
import AssetStatusTag from '../components/AssetStatusTag';
import HealthRing from '../components/HealthRing';
import ManualRegisterModal from '../components/ManualRegisterModal';
import OfflineModal from '../components/OfflineModal';
import PublishPrecheckModal from '../components/PublishPrecheckModal';
// 元数据实体检索核(M2-2 目录三合一:目录浏览/统一搜索并入本账,docs/PLATFORM_CORE_FLOW.md)
import AssetExplorer from '@/components/metadata/AssetExplorer';

const PUBLISHABLE: AssetStatus[] = ['PENDING', 'OFFLINE', 'IGNORED'];
const IGNORABLE: AssetStatus[] = ['PENDING', 'OFFLINE'];
const DELETABLE: AssetStatus[] = ['OFFLINE', 'SOURCE_GONE'];
/** 手工登记无源域对象:未上架可直接撤销登记(与后端 48003 规则一致)。 */
const MANUAL_DELETABLE: AssetStatus[] = ['PENDING', 'OFFLINE', 'IGNORED'];

const isDeletable = (record: AssetRecord) =>
  DELETABLE.includes(record.status) ||
  (record.sourceType === 'MANUAL' && MANUAL_DELETABLE.includes(record.status));

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
  const returnAssetId = returnAssetIdValue && /^\d+$/.test(returnAssetIdValue)
    ? Number(returnAssetIdValue)
    : undefined;
  const [view, setView] = useState<CatalogView>(() =>
    entryParams.get('view') === 'entity' ? '元数据实体' : '台账资产',
  );

  const switchView = (next: CatalogView) => {
    setView(next);
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
  const [loadError, setLoadError] = useState(false);
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

  const [viewMode, setViewMode] = useState<'列表' | '卡片'>('列表');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [selectedRows, setSelectedRows] = useState<AssetRecord[]>([]);

  const [dirTree, setDirTree] = useState<DirNode[]>([]);
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [tags, setTags] = useState<AssetTagRecord[]>([]);

  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardAssets, setWizardAssets] = useState<AssetRecord[]>([]);
  const [manualOpen, setManualOpen] = useState(false);
  const [offlineTarget, setOfflineTarget] = useState<AssetRecord[] | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
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
      setRecords(result.records);
      setTotal(result.total);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, keyword, assetTypes, layerCodes, statuses, grades, tagIds, directoryId, sortBy]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    getDirectoryTree().then(setDirTree).catch(() => setDirTree([]));
    listSemanticLayers()
      .then((list) => setLayers(list.filter((layer) => layer.status === 'ENABLED')))
      .catch(() => setLayers([]));
    listAssetTags().then(setTags).catch(() => setTags([]));
  }, []);

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
    let target: number | undefined;
    Modal.confirm({
      title: '批量移目录',
      content: (
        <Tree
          treeData={treeData as never}
          selectedKeys={target ? [target] : []}
          onSelect={(keys) => {
            target = keys[0] as number | undefined;
          }}
          style={{ maxHeight: 300, overflow: 'auto' }}
        />
      ),
      okText: '移动',
      cancelText: '取消',
      onOk: async () => {
        try {
          const count = await batchMoveAssetsDirectory(selectedIds, target ?? null);
          message.success(`已移动 ${count} 个资产`);
          refresh();
        } catch {
          // 已由全局错误提示统一展示后端原话（如目录不存在/有环 48006）
        }
      },
    });
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

  const columns: ColumnsType<AssetRecord> = [
    {
      title: '资产',
      dataIndex: 'name',
      ellipsis: true,
      render: (name: string, record) => (
        <div>
          <a href={`/data-asset/detail/${record.id}`}
            className="font-medium text-[#161823] hover:text-[#FE2C55]"
            onClick={(event) => { event.preventDefault(); history.push(`/data-asset/detail/${record.id}`); }}
          >
            {name}
          </a>
          <div className="truncate text-[12px] text-[#667085]" title={record.assetKey}>{record.assetKey}</div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'assetType',
      width: 110,
      render: (type: string, record) => (
        <div className="flex flex-wrap gap-1">
          <Tag>{ASSET_TYPE_LABELS[type as keyof typeof ASSET_TYPE_LABELS] ?? ASSET_SOURCE_TYPE_LABELS[record.sourceType as keyof typeof ASSET_SOURCE_TYPE_LABELS] ?? type}</Tag>
          {record.layerCode && <Tag color="geekblue">{record.layerCode}</Tag>}
        </div>
      ),
    },
    { title: '负责人', dataIndex: 'owner', width: 110, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: AssetStatus) => <AssetStatusTag status={status} />,
    },
    {
      title: '健康度',
      dataIndex: 'healthScore',
      width: 88,
      render: (score: number | null, record) => <HealthRing score={score} grade={record.healthGrade} size={38} />,
    },
    {
      title: '近30天浏览',
      dataIndex: 'viewCount30d',
      width: 100,
      align: 'right' as const,
      render: (count?: number) => count ?? 0,
    },
    {
      title: '源更新',
      dataIndex: 'sourceUpdatedAt',
      width: 150,
      render: (value?: string) => formatAssetTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      fixed: 'right' as const,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => history.push(`/data-asset/detail/${record.id}`)}>详情</Button>
          <Dropdown trigger={['click']} menu={{ items: [
            { key: 'publish', label: '上架', disabled: !canUpdate || !PUBLISHABLE.includes(record.status), onClick: () => openWizard([record]) },
            { key: 'offline', label: '下架', disabled: !canUpdate || record.status !== 'PUBLISHED', onClick: () => setOfflineTarget([record]) },
            { key: 'ignore', label: '忽略', disabled: !canUpdate || !IGNORABLE.includes(record.status), onClick: () => runIgnore([record]) },
            { key: 'delete', label: '删除', danger: true, disabled: !canDelete || !isDeletable(record), onClick: () => runDelete(record) },
          ] }}>
            <Button type="text" size="small" aria-label={`更多操作：${record.name}`}>更多</Button>
          </Dropdown>
        </Space>
      ),
    },
  ];

  const hasSelection = selectedIds.length > 0;

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">资产目录</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            {view === '台账资产'
              ? '查找数据对象，了解负责人、来源与治理状态。'
              : '检索已登记的技术实体，查看结构、属性与采集证据。'}
          </div>
        </div>
        <Space size={12} wrap>
          {returnAssetId && Number.isSafeInteger(returnAssetId) && returnAssetId > 0 && (
            <Button onClick={() => history.push(`/data-asset/detail/${returnAssetId}`)}>
              返回资产详情
            </Button>
          )}
          <Segmented
            options={['台账资产', '元数据实体']}
            value={view}
            onChange={(value) => switchView(value as CatalogView)}
          />
          {view === '台账资产' && canCreate && (
            <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={() => setManualOpen(true)}>
              手工登记
            </YakButton>
          )}
        </Space>
      </div>

      {view === '元数据实体' ? (
        <div className="mt-4">
          <AssetExplorer searchable />
        </div>
      ) : (
      <div className="mt-4 flex min-h-0 flex-1 gap-4 max-md:flex-col">
      <div className="w-[240px] shrink-0 max-md:w-full">
        <Button className="mb-2 md:!hidden" aria-expanded={directoryOpen} onClick={() => setDirectoryOpen(!directoryOpen)}>
          {directoryId ? '目录筛选 · 已选择' : '选择目录'}
        </Button>
        <div className={directoryOpen ? '' : 'hidden md:block'}>
        <div className="mb-2 text-[13px] font-medium text-[#667085]">资产目录</div>
        {treeData.length === 0 ? (
          <YakEmpty compact title="暂无目录" description="到「目录与标签」页按分层+业务域一键初始化" />
        ) : (
          <Tree
            treeData={treeData as never}
            selectedKeys={directoryId ? [directoryId] : []}
            onSelect={(keys) => {
              setDirectoryId(keys[0] as number | undefined);
              setPageNo(1);
            }}
            defaultExpandAll
            blockNode
          />
        )}
        {directoryId && (
          <Button type="link" size="small" className="!px-0" onClick={() => setDirectoryId(undefined)}>
            清除目录筛选
          </Button>
        )}
        </div>
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex flex-wrap items-center gap-3">
          <Input.Search
            allowClear
            placeholder="名称 / 描述 / 负责人 / 标签"
            className="!w-[240px] max-sm:!w-full"
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            onSearch={(value) => {
              setKeyword(value.trim());
              setPageNo(1);
            }}
          />
          <Select
            mode="multiple"
            allowClear
            placeholder="类型"
            className="min-w-[120px] max-w-[220px]"
            value={assetTypes}
            onChange={(value) => { setAssetTypes(value); setPageNo(1); }}
            options={Object.entries(ASSET_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
            maxTagCount="responsive"
          />
          <Select
            mode="multiple"
            allowClear
            placeholder="分层"
            className={advancedOpen ? 'min-w-[110px] max-w-[200px]' : 'hidden'}
            value={layerCodes}
            onChange={(value) => { setLayerCodes(value); setPageNo(1); }}
            options={layers.map((layer) => ({ value: layer.code, label: layer.name }))}
            maxTagCount="responsive"
          />
          <Select
            mode="multiple"
            allowClear
            placeholder="状态"
            className="min-w-[110px] max-w-[200px]"
            value={statuses}
            onChange={(value) => { setStatuses(value); setPageNo(1); }}
            options={Object.entries({ PENDING: '待上架', PUBLISHED: '已上架', OFFLINE: '已下架', IGNORED: '已忽略', SOURCE_GONE: '源已消失' }).map(([value, label]) => ({ value, label }))}
            maxTagCount="responsive"
          />
          <Select
            mode="multiple"
            allowClear
            placeholder="健康度"
            className={advancedOpen ? 'min-w-[100px] max-w-[160px]' : 'hidden'}
            value={grades}
            onChange={(value) => { setGrades(value); setPageNo(1); }}
            options={['A', 'B', 'C', 'D'].map((grade) => ({
              value: grade,
              label: <span style={{ color: healthGradeColor(grade) }}>{grade} 级</span>,
            }))}
            maxTagCount="responsive"
          />
          <Select
            mode="multiple"
            allowClear
            placeholder="标签"
            className={advancedOpen ? 'min-w-[110px] max-w-[200px]' : 'hidden'}
            value={tagIds}
            onChange={(value) => { setTagIds(value); setPageNo(1); }}
            options={tags.map((tag) => ({ value: tag.id, label: tag.tagName }))}
            maxTagCount="responsive"
          />
          <Select
            value={sortBy}
            className="!w-[150px]"
            onChange={(value) => {
              setSortBy(value);
              setPageNo(1);
            }}
            options={Object.entries(SORT_LABELS).map(([value, label]) => ({ value, label }))}
          />
          <Segmented
            options={['列表', '卡片']}
            className="max-sm:!hidden"
            value={viewMode}
            onChange={(value) => setViewMode(value as '列表' | '卡片')}
          />
          <Button aria-expanded={advancedOpen} onClick={() => setAdvancedOpen(!advancedOpen)}>
            高级筛选{layerCodes.length + grades.length + tagIds.length > 0 ? ` (${layerCodes.length + grades.length + tagIds.length})` : ''}
          </Button>
          <Button onClick={() => {
            setKeyword(''); setSearchText(''); setAssetTypes([]); setLayerCodes([]);
            setStatuses([]); setGrades([]); setTagIds([]); setDirectoryId(undefined); setSortBy(''); setPageNo(1);
          }}>重置</Button>
        </div>

        {canUpdate && hasSelection && (
          <div className="mt-3 flex flex-wrap items-center gap-2 rounded-lg bg-[#f8f9fa] px-3 py-2">
            <span className="text-[13px]">已选 {selectedIds.length} 项</span>
            <YakButton size="small" onClick={() => openWizard(selectedRows)}>
              批量上架
            </YakButton>
            <YakButton size="small" onClick={() => setOfflineTarget(selectedRows.filter((row) => row.status === 'PUBLISHED'))}>
              批量下架
            </YakButton>
            <YakButton size="small" onClick={runBatchMove}>
              移目录
            </YakButton>
            <YakButton size="small" onClick={() => runIgnore(selectedRows.filter((row) => IGNORABLE.includes(row.status)))}>
              忽略
            </YakButton>
            <span className="ml-auto text-[12px] text-[#98a2b3]">仅本页可选，单次批量上限 200</span>
          </div>
        )}

        <div className="mt-4 flex-1">
          {loadError && <Alert className="mb-3" type="error" showIcon message="资产列表读取失败" description="请重试；下方保留上次读取的结果。" action={<Button onClick={load}>重试</Button>} />}
          {viewMode === '列表' && screens.sm !== false ? (
            <Table<AssetRecord>
              rowKey="id"
              columns={columns}
              dataSource={records}
              loading={loading}
              scroll={{ x: 1030 }}
              rowClassName={() => 'cursor-pointer'}
              onRow={(record) => ({
                onClick: (event) => {
                  const target = event.target as HTMLElement;
                  // 行内链接/按钮/勾选框已有自身行为,不触发行跳转
                  if (target.closest('a, button, input, .ant-checkbox-wrapper')) {
                    return;
                  }
                  if (window.getSelection()?.toString()) {
                    return;
                  }
                  history.push(`/data-asset/detail/${record.id}`);
                },
              })}
              rowSelection={{
                selectedRowKeys: selectedIds,
                onChange: (keys, rows) => {
                  setSelectedIds(keys as number[]);
                  setSelectedRows(rows);
                },
              }}
              locale={{
                emptyText: (
                  <YakEmpty
                    compact
                    title="没有符合条件的资产"
                    description="调整筛选，或等待对账把源域对象登记进来"
                  />
                ),
              }}
              pagination={{
                current: pageNo,
                pageSize,
                total,
                showSizeChanger: true,
                showTotal: (count) => `共 ${count} 条`,
                onChange: (next, size) => {
                  setPageNo(next);
                  setPageSize(size);
                },
              }}
            />
          ) : (
            <><AssetCardGrid
              records={records}
              loading={loading}
              onOpen={(record) => history.push(`/data-asset/detail/${record.id}`)}
            />
            <Pagination className="mt-4" size="small" current={pageNo} pageSize={pageSize} total={total}
              showTotal={(count) => `共 ${count} 条`} onChange={(next, size) => { setPageNo(next); setPageSize(size); }} />
            </>
          )}
        </div>
      </div>
      </div>
      )}

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
    </div>
  );
};

const AssetCardGrid = ({
  records,
  loading,
  onOpen,
}: {
  records: AssetRecord[];
  loading: boolean;
  onOpen: (record: AssetRecord) => void;
}) => (
  <div className="grid grid-cols-3 gap-3 max-xl:grid-cols-2 max-md:grid-cols-1">
    {records.map((record) => (
      <button type="button"
        key={record.id}
        className="cursor-pointer rounded-xl border border-solid border-[#e5e7eb] bg-white p-4 text-left transition hover:border-[#667085] hover:shadow-sm"
        onClick={() => onOpen(record)}
      >
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="truncate text-[15px] font-medium">{record.name}</div>
            <div className="mt-1 truncate text-[12px] text-[#98a2b3]">{record.assetKey}</div>
          </div>
          <HealthRing score={record.healthScore} grade={record.healthGrade} size={44} />
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-1">
          <Tag>{ASSET_TYPE_LABELS[record.assetType as keyof typeof ASSET_TYPE_LABELS] ?? record.assetType}</Tag>
          {record.layerCode && <Tag color="geekblue">{record.layerCode}</Tag>}
          {record.securityLevelCode && <Tag color="volcano">{record.securityLevelCode}</Tag>}
        </div>
        <div className="mt-3 flex items-center justify-between text-[12px] text-[#667085]">
          <span>{record.owner || '无负责人'}</span>
          <AssetStatusTag status={record.status} />
          <span>浏览 {record.viewCount30d ?? 0}</span>
        </div>
      </button>
    ))}
    {!loading && records.length === 0 && (
      <YakEmpty compact title="没有符合条件的资产" description="调整筛选后再试" />
    )}
  </div>
);

export default AssetCatalogPage;
