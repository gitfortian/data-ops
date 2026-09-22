import { Descriptions, Spin, Tag, Tabs, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from '@umijs/max';
import { YakEmpty } from '@/components/ui';
import { getMdmEntity } from '@/services/mdm/api';
import type { MdmEntityRecord, MdmEntityStatus } from '@/services/mdm/types';
import AttributeTab from '@/pages/mdm/modeling/components/AttributeTab';
import ChangeHistoryTab from '@/pages/mdm/modeling/components/ChangeHistoryTab';
import CollectStatusTab from '@/pages/mdm/modeling/components/CollectStatusTab';
import DistributionTab from '@/pages/mdm/modeling/components/DistributionTab';
import LineageTab from '@/pages/mdm/modeling/components/LineageTab';
import QualityTab from '@/pages/mdm/modeling/components/QualityTab';
import RecordTab from '@/pages/mdm/modeling/components/RecordTab';
import SubscriptionTab from '@/pages/mdm/modeling/components/SubscriptionTab';

const STATUS_LABELS: Record<MdmEntityStatus, string> = {
  DRAFT: '草稿',
  ACTIVE: '生效',
  DISABLED: '停用',
};

const STATUS_COLORS: Record<MdmEntityStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'green',
  DISABLED: 'red',
};

/**
 * 主数据实体详情(一站式视图骨架,ticket 51)。
 * 概览随实体数据展示;属性/记录/采集/质量/分发/血缘/变更均已真接平台能力
 * (复用数据集成/数据质量/血缘/审批中心/数据服务,D-M11 MDM 零执行引擎);
 * 标准 Tab 复用语义中心,随 ticket 52 接入。
 */
const MdmEntityDetailPage = () => {
  const navigate = useNavigate();
  const params = useParams<{ id?: string }>();
  const entityId = Number(params.id);
  const [entity, setEntity] = useState<MdmEntityRecord | null>(null);
  const [loading, setLoading] = useState(false);

  const loadEntity = useCallback(async () => {
    if (!entityId) return;
    setLoading(true);
    try {
      setEntity(await getMdmEntity(entityId));
    } catch {
      message.error('加载主数据实体失败');
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    void loadEntity();
  }, [loadEntity]);

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <Typography.Link onClick={() => navigate('/mdm/modeling')}>主数据建模</Typography.Link>
            <span className="text-[#c4c9d1]">/</span>
            <div className="text-[20px] font-semibold leading-7">
              {entity ? `${entity.name}（${entity.code}）` : '实体详情'}
            </div>
          </div>
          <div className="mt-1 text-[13px] text-[#667085]">
            主数据实体的概览与配置；属性、记录与通用能力 Tab 随后续能力接入
          </div>
        </div>
      </div>

      <Spin spinning={loading}>
        {entity ? (
          <div className="mt-5">
            <Descriptions
              column={3}
              bordered
              size="middle"
              items={[
                { key: 'code', label: '实体编码', children: entity.code },
                { key: 'name', label: '实体名称', children: entity.name },
                {
                  key: 'status',
                  label: '状态',
                  children: <Tag color={STATUS_COLORS[entity.status]}>{STATUS_LABELS[entity.status]}</Tag>,
                },
                { key: 'owner', label: '负责人', children: entity.owner || '-' },
                {
                  key: 'createTime',
                  label: '创建时间',
                  children: entity.createTime ? String(entity.createTime).replace('T', ' ').slice(0, 19) : '-',
                },
                {
                  key: 'updateTime',
                  label: '更新时间',
                  children: entity.updateTime ? String(entity.updateTime).replace('T', ' ').slice(0, 19) : '-',
                },
                { key: 'description', label: '描述', span: 3, children: entity.description || '-' },
              ]}
            />

            <div className="mt-5">
              <Tabs
                items={[
                  { key: 'attributes', label: '属性', children: <AttributeTab entityId={entityId} /> },
                  { key: 'records', label: '记录', children: <RecordTab entityId={entityId} /> },
                  { key: 'collect', label: '采集', children: <CollectStatusTab entityId={entityId} /> },
                  { key: 'quality', label: '质量', children: <QualityTab entityId={entityId} /> },
                  { key: 'distribute', label: '分发配置', children: <DistributionTab entityId={entityId} /> },
                  { key: 'subscription', label: '订阅', children: <SubscriptionTab entityId={entityId} /> },
                  { key: 'lineage', label: '血缘', children: <LineageTab entityId={entityId} /> },
                  { key: 'standard', label: '标准', children: <EmptyTab note="标准引用复用语义中心，随 ticket 52 接入" /> },
                  { key: 'changes', label: '变更记录', children: <ChangeHistoryTab entityId={entityId} /> },
                ]}
              />
            </div>
          </div>
        ) : (
          !loading && (
            <div className="mt-5">
              <YakEmpty title="实体不存在或已删除" description="返回列表查看当前项目的主数据实体" />
            </div>
          )
        )}
      </Spin>
    </div>
  );
};

const EmptyTab = ({ note }: { note: string }) => (
  <YakEmpty compact title="暂无内容" description={note} />
);

export default MdmEntityDetailPage;
