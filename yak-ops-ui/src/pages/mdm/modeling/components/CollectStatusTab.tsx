import { Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  LandingStatusCell,
  LandingTaskModal,
  RunLandingLink,
  useMdmCollectStatus,
} from '@/pages/mdm/components/CollectLanding';
import { listMdmSources } from '@/services/mdm/api';
import type { MdmSourceRecord, MdmSourceRole } from '@/services/mdm/types';

const ROLE_LABELS: Record<MdmSourceRole, string> = {
  MAIN: '主来源',
  AUXILIARY: '辅助来源',
};

/**
 * 实体详情"采集"Tab(R1 真数版):采集配置与执行复用「数据集成」(sync,零改动);
 * 本页提供一键生成落地任务(平台库预建落地表 + saveGuide 注册)与执行/状态反查,
 * 状态列按 jobDefinitionId 实时向 sync 查最近 SUCCEEDED 运行,不冗余落库(D-M10)。
 */
const CollectStatusTab = ({ entityId }: { entityId: number }) => {
  const [sources, setSources] = useState<MdmSourceRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [landingTarget, setLandingTarget] = useState<MdmSourceRecord | null>(null);
  const { bySource: collectBySource, reload: reloadCollect } = useMdmCollectStatus(entityId);

  const loadSources = useCallback(async () => {
    setLoading(true);
    try {
      setSources(await listMdmSources(entityId));
    } catch {
      setSources([]);
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    void loadSources();
  }, [loadSources]);

  const columns: ColumnsType<MdmSourceRecord> = [
    { title: '数据源', dataIndex: 'datasourceName', width: 140 },
    { title: '表', dataIndex: 'table' },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (value: MdmSourceRole) => (
        <Tag color={value === 'MAIN' ? 'green' : 'default'}>{ROLE_LABELS[value]}</Tag>
      ),
    },
    {
      title: '落地采集',
      key: 'landing',
      width: 280,
      render: (_, record) => <LandingStatusCell status={collectBySource.get(record.id)} />,
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => {
        const status = collectBySource.get(record.id);
        return (
          <Space size={8}>
            {status ? (
              <RunLandingLink sourceId={record.id} onDone={reloadCollect} />
            ) : (
              <Typography.Link onClick={() => setLandingTarget(record)}>生成落地任务</Typography.Link>
            )}
            <Typography.Link onClick={() => history.push('/sync/batch-link-up')}>
              数据集成
            </Typography.Link>
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <div className="mb-3 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
        采集执行复用「数据集成」(sync)：一键「生成落地任务」将在平台业务库预建落地表并注册全量落地任务，
        「执行落地」触发一次运行；状态列为按任务实时反查的最近成功采集。
      </div>
      <div className="mb-3 flex justify-end">
        <YakButton className="!h-8 !rounded-lg !px-3" onClick={reloadCollect}>
          刷新状态
        </YakButton>
      </div>
      <Table<MdmSourceRecord>
        rowKey="id"
        columns={columns}
        dataSource={sources}
        loading={loading}
        size="middle"
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="暂无主数据来源"
              description="请先到「主数据识别」确认来源绑定，再生成落地任务"
            />
          ),
        }}
        pagination={false}
      />
      <LandingTaskModal
        open={!!landingTarget}
        sourceId={landingTarget?.id}
        sourceTable={landingTarget?.table}
        onClose={() => setLandingTarget(null)}
        onSuccess={reloadCollect}
      />
    </div>
  );
};

export default CollectStatusTab;
