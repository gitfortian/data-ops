import { Button, Card, Descriptions, Input, message, Select, Space, Tabs, Tag, Tooltip } from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { history, useParams } from '@umijs/max';

import { YakEmpty } from '@/components/ui';
import UserSelect from '@/components/UserSelect';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  attachAssetTags,
  changeAssetOwner,
  detachAssetTag,
  getAssetDetail,
  getAssetSection,
  getAssetTags,
  getDirectoryTree,
  listAssetTags,
  reportAssetView,
  submitAssetPublishApproval,
  updateAssetSnapshot,
} from '@/services/data-asset/api';
import type {
  AssetSection,
  AssetTagRecord,
} from '@/services/data-asset/types';
import {
  ASSET_SOURCE_TYPE_LABELS,
  ASSET_TYPE_LABELS,
  collectDirOptions,
  formatAssetTime,
  HEALTH_DIMENSION_LABELS,
  HEALTH_ITEM_LABELS,
  HEALTH_STATE_LABELS,
  sourceObjectPath,
} from '../constants';
import AssetStatusTag from '../components/AssetStatusTag';
import HealthRing from '../components/HealthRing';
import OfflineModal from '../components/OfflineModal';
import PublishPrecheckModal from '../components/PublishPrecheckModal';
import StatusFlowStrip from '../components/StatusFlowStrip';

interface HealthItem {
  key: string;
  dimension: string;
  max: number;
  points: number;
  state: string;
  gap?: string | null;
}

/** 分区容错渲染:UNAVAILABLE 如实展示原因,不伪造空(design §6.4)。 */
const SectionBlock = ({
  title,
  section,
  children,
}: {
  title: string;
  section?: AssetSection;
  children?: ReactNode;
}) => {
  if (!section || section.status !== 'OK') {
    const state = section?.status;
    const stateTitle = state === 'EMPTY'
      ? '确认无数据'
      : state === 'NOT_APPLICABLE'
        ? '当前类型不适用'
        : state === 'PERMISSION_DENIED'
          ? '无权查看'
          : '暂不可用';
    return (
      <Card title={title} size="small" className="!mb-4">
        <YakEmpty
          compact
          title={stateTitle}
          description={section?.note ?? '依赖域尚未提供数据,不伪造为空'}
        />
      </Card>
    );
  }
  return (
    <Card title={title} size="small" className="!mb-4">
      {children}
      {section?.actions && section.actions.length > 0 && (
        <Space className="!mt-3">
          {section.actions.map((action) => (
            <Button key={action.target} type="link" onClick={() => history.push(action.target)}>
              {action.label}
            </Button>
          ))}
        </Space>
      )}
    </Card>
  );
};

const AssetDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const assetId = Number(id);
  const { can } = usePermissionAccess();
  const canUpdate = can('data-asset:update');

  const [detail, setDetail] = useState<Awaited<ReturnType<typeof getAssetDetail>> | null>(null);
  const [loading, setLoading] = useState(true);
  const [dirNames, setDirNames] = useState<Record<number, string>>({});
  const [currentTags, setCurrentTags] = useState<AssetTagRecord[]>([]);
  const [tagOptions, setTagOptions] = useState<AssetTagRecord[]>([]);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [offlineOpen, setOfflineOpen] = useState(false);
  const [snapshot, setSnapshot] = useState({ name: '', description: '', accessUri: '' });
  const [ownerValue, setOwnerValue] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getAssetDetail(assetId);
      setDetail(result);
      const sectionTypes = [
        'TECHNICAL_METADATA', 'QUALITY', 'SECURITY', 'LINEAGE', 'USAGE', 'LIFECYCLE',
      ] as const;
      void Promise.allSettled(
        sectionTypes.map(async (sectionType) => ({
          sectionType,
          result: await getAssetSection(assetId, sectionType),
        })),
      ).then((settled) => {
        setDetail((current) => {
          if (!current || current.asset.id !== assetId) return current;
          const sections = { ...current.sections };
          settled.forEach((entry, index) => {
            const sectionType = sectionTypes[index];
            if (entry.status === 'rejected') return;
            const { result: response } = entry.value;
            const raw = response.summary.values;
            const data = sectionType === 'USAGE'
              ? raw.views
              : Object.prototype.hasOwnProperty.call(raw, 'data')
                ? raw.data
                : raw;
            const section: AssetSection = {
              status: response.status,
              note: response.reason,
              data,
              actions: response.actions,
            };
            if (sectionType === 'TECHNICAL_METADATA') {
              sections.sourceAttrs = section as NonNullable<typeof sections.sourceAttrs>;
            }
            if (sectionType === 'QUALITY') sections.quality = section;
            if (sectionType === 'SECURITY') {
              sections.security = section as NonNullable<typeof sections.security>;
            }
            if (sectionType === 'LINEAGE') {
              sections.lineage = section as NonNullable<typeof sections.lineage>;
            }
            if (sectionType === 'USAGE') {
              sections.trend = section as NonNullable<typeof sections.trend>;
            }
            if (sectionType === 'LIFECYCLE') sections.ttl = section;
          });
          return { ...current, sections };
        });
      });
      setSnapshot({
        name: result.asset.name ?? '',
        description: result.asset.description ?? '',
        accessUri: result.asset.accessUri ?? '',
      });
      setOwnerValue(result.asset.owner ? [result.asset.owner] : []);
    } catch {
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [assetId]);

  useEffect(() => {
    if (!Number.isFinite(assetId)) return;
    void reload();
    void getAssetTags(assetId).then(setCurrentTags).catch(() => setCurrentTags([]));
    void listAssetTags().then(setTagOptions).catch(() => setTagOptions([]));
    void getDirectoryTree()
      .then((tree) => setDirNames(Object.fromEntries(collectDirOptions(tree).map((o) => [o.value, o.label]))))
      .catch(() => setDirNames({}));
    // 浏览上报尽力而为(服务端 5 分钟去重)
    void reportAssetView(assetId, 'detail').catch(() => undefined);
  }, [assetId, reload]);

  const asset = detail?.asset;

  const refreshTags = useCallback(
    () => getAssetTags(assetId).then(setCurrentTags).catch(() => undefined),
    [assetId],
  );

  const healthItems = useMemo<HealthItem[]>(() => {
    const raw = detail?.sections.health?.data?.detail;
    if (!raw) return [];
    try {
      return JSON.parse(raw) as HealthItem[];
    } catch {
      return [];
    }
  }, [detail]);

  const sourceLink = asset ? sourceObjectPath(asset.sourceType, asset.sourceId) : undefined;

  const saveSnapshot = async () => {
    setSaving(true);
    try {
      await updateAssetSnapshot(assetId, snapshot);
      message.success('已更新快照字段');
      void reload();
    } catch {
      // 全局错误提示已展示
    } finally {
      setSaving(false);
    }
  };

  const saveOwner = async () => {
    const owner = ownerValue[ownerValue.length - 1];
    if (!owner) {
      message.warning('请选择负责人');
      return;
    }
    try {
      await changeAssetOwner(assetId, owner);
      message.success('已变更负责人');
      void reload();
    } catch {
      // 全局错误提示已展示
    }
  };

  const submitPublishApproval = async () => {
    try {
      const instance = await submitAssetPublishApproval(assetId);
      message.success(`已发起上架审批(单 #${instance.id}),通过后自动上架`);
      void reload();
    } catch {
      // 全局错误提示已展示(流程未配置 49007/在途重复 49003 等)
    }
  };

  if (!asset && !loading) {
    return (
      <div className="p-6">
        <YakEmpty title="资产不存在或已删除" description="可能已被软删或不在当前项目空间">
          <Button onClick={() => history.push('/data-asset/catalog')}>返回资产目录</Button>
        </YakEmpty>
      </div>
    );
  }

  const lineage = detail?.sections.lineage;
  const lineageData = lineage?.status === 'OK' ? lineage.data : undefined;
  const rootId = lineageData?.root?.id;
  const relationList = lineageData?.relations ?? [];
  const upstreamIds = relationList.filter((r) => r.toAssetId === rootId).map((r) => r.fromAssetId);
  const downstreamIds = relationList.filter((r) => r.fromAssetId === rootId).map((r) => r.toAssetId);
  const nodeById = new Map((lineageData?.nodes ?? []).map((n) => [n.id, n]));
  const renderNodes = (ids: (number | undefined)[]) => {
    const nodes = ids.map((nodeId) => nodeById.get(nodeId ?? -1)).filter(Boolean);
    if (nodes.length === 0) return <span className="text-[12px] text-[#98a2b3]">无</span>;
    return (
      <Space size={4} wrap>
        {nodes.map((node) => (
          <Tag key={node!.id} color={node!.id === rootId ? 'magenta' : undefined}>
            {node!.name || node!.assetKey}
          </Tag>
        ))}
      </Space>
    );
  };

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <Space size={8} wrap>
            <Button type="text" size="small" className="!px-0 !text-[#667085]" onClick={() => history.back()}>
              ← 返回
            </Button>
            <span className="text-[20px] font-semibold leading-7">{asset?.name}</span>
            {asset && <AssetStatusTag status={asset.status} />}
            {detail?.sections.sourceAttrs?.data?.sourceChanged && (
              <Tooltip title="源域对象已变化,待盘点确认后刷新快照">
                <Tag color="gold">源已变更</Tag>
              </Tooltip>
            )}
          </Space>
          <div className="mt-1 text-[12px] text-[#98a2b3]">{asset?.assetKey}</div>
        </div>
        <Space>
          {asset && sourceLink && (
            <Button onClick={() => history.push(sourceLink)}>查看源对象</Button>
          )}
          {asset && canUpdate && ['PENDING', 'OFFLINE', 'IGNORED'].includes(asset.status) && (
            <Button type="primary" className="!text-white" onClick={() => setWizardOpen(true)}>
              上架
            </Button>
          )}
          {asset && canUpdate && ['PENDING', 'OFFLINE', 'IGNORED'].includes(asset.status) && (
            <Button onClick={() => void submitPublishApproval()}>申请上架审批</Button>
          )}
          {asset?.status === 'PUBLISHED' && canUpdate && (
            <Button danger onClick={() => setOfflineOpen(true)}>
              下架
            </Button>
          )}
        </Space>
      </div>

      <StatusFlowStrip section={detail?.sections.statusFlow} />

      <Tabs
        className="mt-4"
        items={[
          {
            key: 'overview',
            label: '概览',
            children: (
              <div>
                <Card title="台账信息" size="small" className="!mb-4" loading={loading}>
                  <Descriptions
                    size="small"
                    column={3}
                    items={[
                      { key: 'type', label: '资产类型', children: asset?.assetType ? (ASSET_TYPE_LABELS[asset.assetType] ?? asset.assetType) : '-' },
                      { key: 'source', label: '来源域', children: asset?.sourceType ? (ASSET_SOURCE_TYPE_LABELS[asset.sourceType] ?? asset.sourceType) : '-' },
                      { key: 'owner', label: '负责人', children: asset?.owner || <span className="text-[#f5222d]">未设置</span> },
                      { key: 'layer', label: '分层', children: asset?.layerCode ?? '-' },
                      { key: 'domain', label: '业务域', children: asset?.domainCode ?? '-' },
                      {
                        key: 'dir',
                        label: '目录',
                        children: asset?.directoryId
                          ? dirNames[asset.directoryId] ?? `#${asset.directoryId}`
                          : <span className="text-[#f5222d]">未归类</span>,
                      },
                      { key: 'security', label: '安全等级(快照)', children: asset?.securityLevelCode ?? '-' },
                      { key: 'views', label: '近30天浏览', children: asset?.viewCount30d ?? 0 },
                      { key: 'reconciled', label: '最近对账', children: formatAssetTime(asset?.reconciledAt) },
                      { key: 'firstListed', label: '首次上架', children: formatAssetTime(asset?.firstListedAt) },
                      { key: 'lastListed', label: '最近上架', children: formatAssetTime(asset?.lastListedAt) },
                      {
                        key: 'offline',
                        label: '最近下架',
                        children: asset?.lastOfflineAt
                          ? `${formatAssetTime(asset.lastOfflineAt)}（${asset.lastOfflineReason ?? '无原因'}）`
                          : '-',
                      },
                    ]}
                  />
                </Card>
                <SectionBlock title="源域实时属性(唯一事实源)" section={detail?.sections.sourceAttrs}>
                  {(() => {
                    const data = detail?.sections.sourceAttrs?.data;
                    return (
                      <Descriptions
                        size="small"
                        column={2}
                        items={[
                          { key: 'n', label: '源名称', children: data?.name ?? '-' },
                          { key: 'd', label: '源描述', children: data?.description ?? '-' },
                          { key: 'o', label: '源建议负责人', children: data?.suggestedOwner ?? '-' },
                          { key: 'u', label: '源更新时间', children: formatAssetTime(data?.updatedAt) },
                          ...(data?.extra
                            ? Object.entries(data.extra).map(([k, v]) => ({
                                key: k,
                                label: k,
                                children: String(v ?? '-'),
                              }))
                            : []),
                        ]}
                      />
                    );
                  })()}
                </SectionBlock>
              </div>
            ),
          },
          {
            key: 'health',
            label: '健康度',
            children: (
              <Card
                size="small"
                title={
                  <Space>
                    <HealthRing
                      score={detail?.sections.health?.data?.score ?? asset?.healthScore}
                      grade={detail?.sections.health?.data?.grade ?? asset?.healthGrade}
                      size={40}
                    />
                    <span>治理缺口一目了然,评分为派生值不可手改</span>
                  </Space>
                }
              >
                {detail?.sections.health?.status === 'UNAVAILABLE' ? (
                  <YakEmpty compact title="暂不可用" description={detail.sections.health.note ?? ''} />
                ) : (
                  <Space direction="vertical" className="w-full" size={0}>
                    {healthItems.map((item) => (
                      <div
                        key={item.key}
                        className="flex items-center justify-between border-b border-[#f0f0f0] py-2 last:border-0"
                      >
                        <div>
                          <span className="font-medium">{HEALTH_ITEM_LABELS[item.key] ?? item.key}</span>
                          <Tag className="!ml-2">{HEALTH_DIMENSION_LABELS[item.dimension] ?? item.dimension}</Tag>
                          {item.gap && <span className="text-[12px] text-[#f5222d]">{item.gap}</span>}
                        </div>
                        <Space>
                          {item.state !== 'OK' && (
                            <span className="text-[12px] text-[#98a2b3]">
                              {HEALTH_STATE_LABELS[item.state] ?? item.state}
                            </span>
                          )}
                          <span
                            className="text-[13px] font-medium"
                            style={{ color: item.points >= item.max ? '#52c41a' : item.state === 'OK' ? '#fa8c16' : '#98a2b3' }}
                          >
                            {item.points}/{item.max}
                          </span>
                        </Space>
                      </div>
                    ))}
                  </Space>
                )}
              </Card>
            ),
          },
          {
            key: 'lineage',
            label: '血缘',
            children: (
              <SectionBlock title="血缘局部图(1 跳)" section={detail?.sections.lineage}>
                <div>
                  <Descriptions
                    size="small"
                    column={1}
                    items={[
                      { key: 'up', label: '上游', children: renderNodes(upstreamIds) },
                      { key: 'self', label: '本体', children: <Tag color="magenta">{lineageData?.root?.name || asset?.name}</Tag> },
                      { key: 'down', label: '下游', children: renderNodes(downstreamIds) },
                    ]}
                  />
                  <Button
                    className="!mt-3"
                    onClick={() =>
                      history.push(`/data-analysis/lineage?assetKey=${encodeURIComponent(asset?.assetKey ?? '')}`)
                    }
                  >
                    打开全屏血缘图谱
                  </Button>
                </div>
              </SectionBlock>
            ),
          },
          {
            key: 'usage',
            label: '使用',
            children: (
              <SectionBlock title="近 30 天资产页浏览" section={detail?.sections.trend}>
                {(() => {
                  const trend = (detail?.sections.trend?.data ?? []) as { date: string; count: number }[];
                  const max = Math.max(1, ...trend.map((point) => point.count));
                  return (
                    <div className="flex h-[120px] items-end gap-[3px]">
                      {trend.map((point) => (
                        <Tooltip key={point.date} title={`${point.date}: ${point.count} 次`}>
                          <div
                            className="flex-1 rounded-t bg-[#FE2C55]/70"
                            style={{ height: `${Math.max(4, (point.count / max) * 100)}%` }}
                          />
                        </Tooltip>
                      ))}
                      {trend.length === 0 && <span className="text-[12px] text-[#98a2b3]">近 30 天无浏览</span>}
                    </div>
                  );
                })()}
              </SectionBlock>
            ),
          },
          {
            key: 'context',
            label: '安全/质量/字段/生命周期',
            children: (
              <div>
                <SectionBlock title="安全分级(实时)" section={detail?.sections.security}>
                  {(() => {
                    const data = detail?.sections.security?.data;
                    return (
                      <Descriptions
                        size="small"
                        column={2}
                        items={[
                          { key: 'level', label: '等级', children: data?.levelName ?? data?.levelCode ?? '-' },
                          { key: 'category', label: '分类', children: data?.categoryName ?? data?.categoryCode ?? '-' },
                        ]}
                      />
                    );
                  })()}
                </SectionBlock>
                <SectionBlock title="质量" section={detail?.sections.quality}>
                  {(() => {
                    const quality = detail?.sections.quality?.data as {
                      monitorCount?: number;
                      monitors?: {
                        monitorName?: string;
                        ruleCount?: number;
                        lastResult?: string;
                        lastRunTime?: string;
                      }[];
                    } | undefined;
                    if (!quality) return null;
                    return (
                      <Descriptions
                        size="small"
                        column={1}
                        items={[
                          { key: 'count', label: '监控数', children: quality.monitorCount ?? 0 },
                          {
                            key: 'monitors',
                            label: '最近结论',
                            children: quality.monitors?.map((monitor) =>
                              `${monitor.monitorName ?? '未命名'}：${monitor.lastResult ?? '未运行'}（${monitor.ruleCount ?? 0} 条规则）`,
                            ).join('；') ?? '暂无监控',
                          },
                        ]}
                      />
                    );
                  })()}
                </SectionBlock>
                <SectionBlock title="字段" section={detail?.sections.fields} />
                <SectionBlock title="生命周期" section={detail?.sections.ttl}>
                  {(() => {
                    const ttl = detail?.sections.ttl?.data as {
                      policyCode?: string;
                      bindingSource?: string;
                      state?: string;
                    } | undefined;
                    if (!ttl) return null;
                    return (
                      <Descriptions
                        size="small"
                        column={1}
                        items={[
                          { key: 'policy', label: '策略', children: ttl.policyCode || '未命中策略' },
                          { key: 'binding', label: '绑定来源', children: ttl.bindingSource ?? '-' },
                          { key: 'state', label: '下发状态', children: ttl.state ?? '-' },
                        ]}
                      />
                    );
                  })()}
                </SectionBlock>
              </div>
            ),
          },
          {
            key: 'govern',
            label: '治理',
            children: (
              <div className="max-w-[640px]">
                <Card title="业务标签" size="small" className="!mb-4">
                  <Space direction="vertical" className="w-full">
                    <Space size={4} wrap>
                      {currentTags.length === 0 && <span className="text-[12px] text-[#98a2b3]">尚无标签</span>}
                      {currentTags.map((tag) => (
                        <Tag
                          key={tag.id}
                          color={tag.color || undefined}
                          closable={canUpdate}
                          onClose={async (event) => {
                            event.preventDefault();
                            await detachAssetTag(assetId, tag.id).catch(() => undefined);
                            void refreshTags();
                            void reload();
                          }}
                        >
                          {tag.tagName}
                        </Tag>
                      ))}
                    </Space>
                    {canUpdate && (
                      <Select
                        mode="multiple"
                        placeholder="选择标签打标(可多选)"
                        className="w-full"
                        value={[]}
                        onChange={async (values: number[]) => {
                          if (values.length === 0) return;
                          await attachAssetTags(assetId, values).catch(() => undefined);
                          void refreshTags();
                          void reload();
                        }}
                        options={tagOptions
                          .filter((tag) => !currentTags.some((owned) => owned.id === tag.id))
                          .map((tag) => ({ value: tag.id, label: tag.tagName }))}
                      />
                    )}
                  </Space>
                </Card>
                <Card title="快照编辑(资产中心拥有名称/描述/入口)" size="small" className="!mb-4">
                  <Space direction="vertical" className="w-full">
                    <Input
                      addonBefore="名称"
                      value={snapshot.name}
                      maxLength={128}
                      onChange={(event) => setSnapshot({ ...snapshot, name: event.target.value })}
                    />
                    <Input.TextArea
                      rows={2}
                      maxLength={1024}
                      showCount
                      placeholder="描述"
                      value={snapshot.description}
                      onChange={(event) => setSnapshot({ ...snapshot, description: event.target.value })}
                    />
                    {asset?.sourceType === 'MANUAL' && (
                      <Input
                        addonBefore="访问入口"
                        maxLength={512}
                        value={snapshot.accessUri}
                        onChange={(event) => setSnapshot({ ...snapshot, accessUri: event.target.value })}
                      />
                    )}
                    <Button type="primary" disabled={!canUpdate} loading={saving} onClick={saveSnapshot} className="!text-white">
                      保存快照
                    </Button>
                  </Space>
                </Card>
                <Card title="统一负责人(唯一事实源,变更留审计)" size="small">
                  <Space>
                    <UserSelect value={ownerValue} onChange={setOwnerValue} max={1} placeholder="搜索并选择负责人" />
                    <Button disabled={!canUpdate} onClick={saveOwner}>
                      变更负责人
                    </Button>
                  </Space>
                </Card>
              </div>
            ),
          },
        ]}
      />

      {asset && (
        <>
          <PublishPrecheckModal
            open={wizardOpen}
            assets={[asset]}
            onClose={() => setWizardOpen(false)}
            onDone={() => {
              setWizardOpen(false);
              void reload();
            }}
          />
          <OfflineModal
            open={offlineOpen}
            assets={asset ? [asset] : []}
            onClose={() => setOfflineOpen(false)}
            onDone={() => {
              setOfflineOpen(false);
              void reload();
            }}
          />
        </>
      )}
    </div>
  );
};

export default AssetDetailPage;
