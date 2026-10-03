import { Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { YakButton, YakEmpty } from '@/components/ui';
import { CheckResultTag } from '@/components/quality/QualityStatus';
import type { CheckResult } from '@/services/data-quality';
import { getMdmQualityStatus, runMdmQualityCheck } from '@/services/mdm/api';
import type { MdmLandingQualityStatus } from '@/services/mdm/types';

/** 预填四元组跳「数据质量-新建监控」（quality 模块原生表单，零改动复用）。 */
const monitorCreatePath = (status: MdmLandingQualityStatus) => {
  const query = new URLSearchParams({
    dataSourceId: String(status.datasourceId),
    dataSourceName: status.datasourceName,
    databaseName: status.database || '',
    schemaName: '',
    tableName: status.landingTable,
  });
  return `/data-quality/monitor/create?${query.toString()}`;
};

const formatTime = (value?: string | null) =>
  value ? String(value).replace('T', ' ').slice(0, 19) : '';

/**
 * 实体详情"质量"Tab(R3 真数版):检查执行复用「数据质量」(quality,零改动)。
 * 一键体检 = 注册落地表资产 + 按内置模板(主键重复/必填)幂等创建监控 + 触发执行;
 * 状态列为按四元组实时反查,MDM 不冗余落库(D-M11);无监控时可跳原生表单自建。
 */
const QualityTab = ({ entityId }: { entityId: number }) => {
  const [statuses, setStatuses] = useState<MdmLandingQualityStatus[]>([]);
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(false);

  const loadStatus = useCallback(async () => {
    setLoading(true);
    try {
      setStatuses(await getMdmQualityStatus(entityId));
    } catch {
      setStatuses([]);
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  const check = useCallback(async () => {
    setChecking(true);
    try {
      const receipts = await runMdmQualityCheck(entityId);
      message.success(
        `已触发 ${receipts.length} 张落地表的质量检查，可在「最近检查」查看结果`,
      );
      await loadStatus();
    } catch (error: any) {
      message.error(error?.message || '质量体检触发失败');
    } finally {
      setChecking(false);
    }
  }, [entityId, loadStatus]);

  const columns: ColumnsType<MdmLandingQualityStatus> = [
    {
      title: '落地表',
      key: 'table',
      render: (_, record) => (
        <span className="font-mono text-[13px]">
          {record.database}.{record.landingTable}
        </span>
      ),
    },
    { title: '数据源', dataIndex: 'datasourceName', width: 150 },
    {
      title: '表资产',
      dataIndex: 'assetRegistered',
      width: 90,
      render: (value: boolean) =>
        value ? <Tag color="green">已注册</Tag> : <Tag>未注册</Tag>,
    },
    {
      title: '规则',
      key: 'rules',
      width: 160,
      render: (_, record) =>
        record.monitorId ? (
          <Space size={6}>
            <Typography.Link onClick={() => history.push(`/data-quality/monitor/${record.monitorId}`)}>
              {record.ruleCount} 条
            </Typography.Link>
            {!record.monitorEnabled && <Tag>监控停用</Tag>}
          </Space>
        ) : (
          <Typography.Link onClick={() => history.push(monitorCreatePath(record))}>
            在数据质量建规则
          </Typography.Link>
        ),
    },
    {
      title: '最近检查',
      key: 'last',
      width: 260,
      render: (_, record) =>
        record.lastResult && record.lastResult !== 'NOT_RUN' ? (
          <Space size={8}>
            <CheckResultTag value={record.lastResult as CheckResult} />
            {formatTime(record.lastRunTime) && (
              <span className="text-[12px] text-[#667085]">{formatTime(record.lastRunTime)}</span>
            )}
            {record.lastExecutionNo && (
              <Typography.Link
                className="text-[12px]"
                onClick={() =>
                  history.push(`/data-quality/execution/${record.lastExecutionNo}`)
                }
              >
                详情
              </Typography.Link>
            )}
          </Space>
        ) : (
          <span className="text-[13px] text-[#667085]">未检查，点右上「一键体检」</span>
        ),
    },
  ];

  return (
    <div>
      <div className="mb-3 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
        质量检查复用「数据质量」(quality)：「一键体检」自动注册落地表资产，并按内置模板
        （主键重复 / 主键与必填非空）创建或复用监控后触发执行；结果实时反查，MDM 侧不冗余存储。
      </div>
      <div className="mb-3 flex justify-end gap-2">
        <YakButton className="!h-8 !rounded-lg !px-3" onClick={loadStatus}>
          刷新状态
        </YakButton>
        <YakButton
          type="primary"
          className="!h-8 !rounded-lg !px-3"
          loading={checking}
          disabled={statuses.length === 0}
          onClick={check}
        >
          一键质量体检
        </YakButton>
      </div>
      <Table<MdmLandingQualityStatus>
        rowKey={(record) => `${record.datasourceId}.${record.database}.${record.landingTable}`}
        columns={columns}
        dataSource={statuses}
        loading={loading}
        size="middle"
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="暂无落地表"
              description="请先到「主数据识别」为来源生成采集落地任务，再回本页体检"
            />
          ),
        }}
        pagination={false}
      />
    </div>
  );
};

export default QualityTab;
