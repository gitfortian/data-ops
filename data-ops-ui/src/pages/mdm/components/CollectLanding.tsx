import { Modal, Select, Space, Tag, Tooltip, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { listAllDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { createMdmCollectLink, listMdmCollectStatus, runMdmCollectLink } from '@/services/mdm/api';
import type { MdmCollectStatus } from '@/services/mdm/types';

/** 采集落地状态(R1):按来源 ID 建索引,供列表页与实体详情 Tab 共用。 */
export const useMdmCollectStatus = (entityId?: number) => {
  const [statuses, setStatuses] = useState<MdmCollectStatus[]>([]);
  const [loading, setLoading] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setStatuses(await listMdmCollectStatus(entityId));
    } catch {
      setStatuses([]);
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const bySource = useMemo(
    () => new Map(statuses.map((item) => [item.sourceId, item])),
    [statuses],
  );

  return { statuses, bySource, loading, reload };
};

const JOB_STATUS_LABELS: Record<string, { text: string; color: string }> = {
  SUCCEEDED: { text: '采集成功', color: 'green' },
  RUNNING: { text: '运行中', color: 'blue' },
  SUBMITTED: { text: '运行中', color: 'blue' },
  QUEUED: { text: '排队中', color: 'blue' },
  PENDING: { text: '排队中', color: 'blue' },
  FAILED: { text: '运行失败', color: 'red' },
  CANCELED: { text: '已取消', color: 'orange' },
};

/** 落地状态单元格:未生成 / 已生成(含最近成功时间与行数)。 */
export const LandingStatusCell = ({ status }: { status?: MdmCollectStatus }) => {
  if (!status) {
    return <Tag>未生成</Tag>;
  }
  const job = status.lastJobStatus ? JOB_STATUS_LABELS[status.lastJobStatus] : undefined;
  if (status.lastSuccessTime) {
    return (
      <Tooltip title={`落地表 ${status.landingTable} · 任务 ${status.jobName ?? status.jobDefinitionId}`}>
        <Space size={4} wrap>
          <Tag color="green">已采集</Tag>
          <span className="text-[12px] text-[#667085]">
            {status.lastSuccessTime}
            {status.lastSuccessRows != null ? ` · ${status.lastSuccessRows} 行` : ''}
          </span>
        </Space>
      </Tooltip>
    );
  }
  return (
    <Tooltip title={`落地表 ${status.landingTable}，等待首次成功运行`}>
      <Space size={4}>
        {job ? <Tag color={job.color}>{job.text}</Tag> : <Tag>已生成</Tag>}
        {status.lastJobStatus === 'FAILED' && status.jobName ? (
          <span className="text-[12px] text-[#667085]">可查看任务日志</span>
        ) : null}
      </Space>
    </Tooltip>
  );
};

/** 一键生成落地任务弹窗:选择落地目标数据源(需可写平台业务库)。 */
export const LandingTaskModal = ({
  open,
  sourceId,
  sourceTable,
  onClose,
  onSuccess,
}: {
  open: boolean;
  sourceId?: number;
  sourceTable?: string;
  onClose: () => void;
  onSuccess: () => void;
}) => {
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [sinkId, setSinkId] = useState<number | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setSinkId(undefined);
      return;
    }
    void (async () => {
      try {
        const result = await listAllDataSources();
        setDataSources(result?.bizData ?? result ?? []);
      } catch {
        setDataSources([]);
      }
    })();
  }, [open]);

  const submit = async () => {
    if (!sourceId || !sinkId) {
      message.warning('请选择落地目标数据源');
      return;
    }
    setSubmitting(true);
    try {
      const link = await createMdmCollectLink({ sourceId, sinkDatasourceId: sinkId });
      message.success(`已生成落地任务,落地表 ${link.landingTable}`);
      onSuccess();
      onClose();
    } catch {
      message.error('生成落地任务失败(请检查数据集成模块与目标数据源可用性)');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="生成采集落地任务"
      open={open}
      onOk={submit}
      confirmLoading={submitting}
      onCancel={onClose}
      okText="生成"
      cancelText="取消"
      destroyOnClose
    >
      <div className="mb-4 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
        将在平台业务库预建落地表，并在「数据集成」注册一个全量落地离线任务（overwrite 刷新）；
        目标数据源需对平台业务库有写权限。
      </div>
      <div className="mb-2 text-[13px]">
        来源表：<span className="font-medium">{sourceTable ?? '-'}</span>
      </div>
      <Select
        className="!w-full"
        placeholder="选择落地目标数据源（指向平台业务库）"
        value={sinkId}
        onChange={setSinkId}
        showSearch
        optionFilterProp="label"
        options={dataSources.map((item) => ({
          label: `${item.name}（${item.id}）`,
          value: item.id,
        }))}
      />
    </Modal>
  );
};

/** 「执行落地」链接:上线(如需)并触发一次异步运行。 */
export const RunLandingLink = ({
  sourceId,
  onDone,
}: {
  sourceId: number;
  onDone: () => void;
}) => {
  const [running, setRunning] = useState(false);
  return (
    <Typography.Link
      disabled={running}
      onClick={async () => {
        setRunning(true);
        try {
          const receipt = await runMdmCollectLink(sourceId);
          message.success(`已提交落地运行（实例 ${receipt?.id ?? '-'}），稍后刷新查看结果`);
          setTimeout(onDone, 2000);
        } catch {
          message.error('触发落地运行失败（任务可能已有运行中的实例）');
        } finally {
          setRunning(false);
        }
      }}
    >
      {running ? '提交中…' : '执行落地'}
    </Typography.Link>
  );
};
