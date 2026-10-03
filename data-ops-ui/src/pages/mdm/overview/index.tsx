import { Card, Space, Tag, Tooltip, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';

import { YakButton, YakEmpty } from '@/components/ui';
import { getMdmOverview } from '@/services/mdm/api';
import type {
  MdmOverviewData,
  MdmOverviewEntityCard,
  MdmOverviewPipelineNode,
  MdmOverviewTotals,
  MdmPipelineNodeKey,
} from '@/services/mdm/types';

/**
 * 计数口径：后端用 -1 表示「这一项没查到」，与「真的是 0」区分开。
 * 这里统一渲染成「-」，避免把查询失败伪装成空数据（docs/home-overview-contract.md）。
 */
const count = (value: number) => (value < 0 ? '-' : value.toLocaleString());

const TOTAL_CARDS: {
  key: keyof MdmOverviewTotals;
  label: string;
  hint: string;
  to?: string;
  alarm?: boolean;
}[] = [
  { key: 'entities', label: '主数据实体', hint: '已定义的实体数', to: '/mdm/modeling' },
  { key: 'activeRecords', label: '生效记录', hint: '全部实体下状态为生效的记录' },
  {
    key: 'pendingChanges',
    label: '待审批变更',
    hint: '在途变更单，点进去查进度或撤回；通过/拒绝走行内「审批单」',
    to: '/mdm/approval?status=PENDING',
    alarm: true,
  },
  { key: 'cleanRules', label: '清洗规则', hint: '去重/标准化/补全规则总数', to: '/mdm/cleansing' },
  {
    key: 'distributionTargets',
    label: '分发目标',
    hint: '生效中的分发配置',
    to: '/data-service/overview',
  },
  { key: 'subscribers', label: '订阅方', hint: '生效中的变更订阅' },
];

/** 管线节点跳转：每个节点指向该平台里真正处理这件事的页面，而不是 MDM 内部再建一套。 */
const PIPELINE_LINKS: Record<MdmPipelineNodeKey, string> = {
  COLLECT: '/sync/batch-link-up',
  PROCESSING: '/data-development',
  CLEANSE: '/mdm/cleansing',
  APPROVE: '/mdm/approval?status=PENDING',
  DISTRIBUTE: '/data-service/overview',
};

const PIPELINE_HINTS: Record<MdmPipelineNodeKey, string> = {
  COLLECT: '已接入的采集落地任务',
  PROCESSING: '主数据加工任务在数据开发域，MDM 侧无真相表，故显示「-」',
  CLEANSE: '清洗规则数；附带本轮已合并的记录数',
  APPROVE: '在途变更单，直达 MDM 变更台账（已筛至「审批中」）；通过/拒绝点行内「审批单」',
  DISTRIBUTE: '生效分发配置；附带最近一次执行有失败条目的配置数',
};

/**
 * 各节点第二个数字口径不同，不能一律写成「待处理」：
 * 已合并是历史结果（不需要谁去处理），待审批/分发失败才是真待办。
 */
const ATTENTION_META: Partial<Record<MdmPipelineNodeKey, { label: string; alarm: boolean }>> = {
  CLEANSE: { label: '已合并', alarm: false },
  APPROVE: { label: '待审批', alarm: true },
  DISTRIBUTE: { label: '有失败', alarm: true },
};

const PipelineStrip = ({ nodes }: { nodes: MdmOverviewPipelineNode[] }) => (
  <div className="flex flex-wrap items-stretch gap-2">
    {nodes.map((node, index) => {
      const meta = ATTENTION_META[node.key];
      // 加工真相在数据开发域(契约不伪造):显示「跨域」而不是伪装成 0 或「-」。
      const crossDomain = node.count < 0;
      return (
        <div key={node.key} className="flex items-center gap-2">
          {index > 0 && <span className="text-[16px] text-[#d0d5dd]">›</span>}
          <Tooltip title={PIPELINE_HINTS[node.key]}>
            <div
              className="min-w-[132px] cursor-pointer rounded border border-[#eaecf0] bg-[#fbfcfd] px-3 py-2 hover:border-[#FE2C55]"
              onClick={() => history.push(PIPELINE_LINKS[node.key])}
            >
              <div className="flex items-center gap-1.5 text-[12px] text-[#667085]">
                {node.label}
                {meta?.alarm && node.attention > 0 && (
                  <span
                    className="inline-block h-[6px] w-[6px] rounded-full bg-[#f5222d]"
                    aria-label={`${meta.label} ${node.attention}`}
                  />
                )}
              </div>
              <div className="mt-0.5 text-[20px] font-semibold leading-7">
                {crossDomain ? (
                  <span className="text-[14px] font-medium text-[#667085]">跨域</span>
                ) : (
                  count(node.count)
                )}
                {meta && node.attention > 0 && (
                  <span
                    className="ml-1 text-[12px] font-normal"
                    style={{ color: meta.alarm ? '#f5222d' : '#98a2b3' }}
                  >
                    {meta.label} {count(node.attention)}
                  </span>
                )}
              </div>
            </div>
          </Tooltip>
        </div>
      );
    })}
  </div>
);

const EntityCardView = ({ card }: { card: MdmOverviewEntityCard }) => (
  <Card
    size="small"
    className="cursor-pointer hover:!border-[#FE2C55]"
    onClick={() => history.push(`/mdm/modeling/${card.entityId}`)}
  >
    <div className="flex items-start justify-between gap-2">
      <div className="min-w-0">
        <div className="truncate text-[14px] font-semibold">{card.entityName}</div>
        <div className="mt-0.5 truncate text-[12px] text-[#667085]">{card.entityCode}</div>
      </div>
      {card.pendingChanges > 0 && <Tag color="processing">{count(card.pendingChanges)} 待审批</Tag>}
    </div>
    <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[12px] text-[#667085]">
      <span>记录 {count(card.activeRecords)}</span>
      <span>分发 {count(card.distributionTargets)}</span>
      <span>订阅 {count(card.subscribers)}</span>
    </div>
    <div className="mt-2">
      <Typography.Link
        className="!text-[12px]"
        onClick={(event) => {
          event.stopPropagation();
          history.push(`/mdm/cleansing?entityId=${card.entityId}`);
        }}
      >
        去清洗
      </Typography.Link>
    </div>
  </Card>
);

const MdmOverviewPage = () => {
  const [data, setData] = useState<MdmOverviewData | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getMdmOverview(12));
    } catch {
      // 不降级成空数据：拿不到就明说，让人重试而不是以为没有主数据。
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">主数据总览</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            采集 → 加工 → 清洗 → 审批 → 分发 的单一入口：计数全部服务端聚合；「-」表示查询失败而非 0，加工真相在数据开发域（显示「跨域」）
          </div>
        </div>
        <Space>
          <YakButton onClick={load} loading={loading}>
            刷新
          </YakButton>
          <YakButton
            type="primary"
            className="!text-white"
            onClick={() => history.push('/mdm/modeling')}
          >
            进入主数据建模
          </YakButton>
        </Space>
      </div>

      {loading ? (
        <div className="mt-4 grid grid-cols-3 gap-3 max-lg:grid-cols-2 max-md:grid-cols-1">
          {TOTAL_CARDS.map((item) => (
            <Card key={item.key} size="small" loading />
          ))}
        </div>
      ) : !data ? (
        <div className="mt-5">
          <YakEmpty
            title="总览加载失败"
            description="统计接口未返回数据，请点击右上角「刷新」重试"
          />
        </div>
      ) : (
        <>
          <div className="mt-4 grid grid-cols-3 gap-3 max-lg:grid-cols-2 max-md:grid-cols-1">
            {TOTAL_CARDS.map((item) => {
              const value = Number(data.totals[item.key] ?? -1);
              return (
                <Card key={item.key} size="small">
                  <Tooltip title={item.hint}>
                    <div className="text-[12px] text-[#667085]">{item.label}</div>
                  </Tooltip>
                  <div
                    className="mt-1 cursor-pointer text-[24px] font-semibold leading-8"
                    style={{
                      color:
                        item.alarm && value > 0 ? '#f5222d' : value < 0 ? '#98a2b3' : undefined,
                    }}
                    onClick={() => item.to && history.push(item.to)}
                  >
                    {count(value)}
                  </div>
                </Card>
              );
            })}
          </div>

          <Card title="主数据管线" size="small" className="!my-4" loading={loading}>
            <PipelineStrip nodes={data.pipeline} />
          </Card>

          <Card title="实体动线（最近更新）" size="small" loading={loading}>
            {data.entities.length === 0 ? (
              <YakEmpty
                compact
                title="暂无主数据实体"
                description="先到「主数据建模」定义实体，实体卡片会自动出现在这里"
              />
            ) : (
              <div className="grid grid-cols-3 gap-3 max-lg:grid-cols-2 max-md:grid-cols-1">
                {data.entities.map((card) => (
                  <EntityCardView key={card.entityId} card={card} />
                ))}
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  );
};

export default MdmOverviewPage;
