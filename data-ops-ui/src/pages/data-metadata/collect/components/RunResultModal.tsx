import { Alert, Button, Modal, Tag } from 'antd';
import { history } from '@umijs/max';

import type { CollectRunRecord } from '@/services/metadata/types';
import {
  formatDuration,
  formatMetadataTime,
  PROVIDER_LABELS,
  RUN_STATUS_COLORS,
  RUN_STATUS_LABELS,
  summarizeRunCounts,
} from '../constants';
import { currentSourceCatalogPath, readFrozenHarvestScope } from '../scopeEvidence';

/**
 * RunView and its historical frozen scope are Metadata Owner evidence.
 * This modal intentionally does NOT turn the current directory into a
 * historical "full run member list" (unchanged entities are not covered
 * by the change stream).
 */
const RunResultModal = ({
  run,
  title,
  onClose,
}: {
  run: CollectRunRecord | null;
  title: string;
  onClose: () => void;
}) => {
  const scope = readFrozenHarvestScope(run?.scopeSnapshot);

  return (
    <Modal
      open={Boolean(run)}
      title={title}
      okText="知道了"
      cancelButtonProps={{ hidden: true }}
      onOk={onClose}
      onCancel={onClose}
      width={640}
    >
      {run ? (
        <div className="space-y-3 py-1">
          <div className="text-[13px] font-medium text-[#344054]">① 本次完成了吗？</div>
          <div className="flex items-center gap-2">
            {run.status ? (
              <Tag color={RUN_STATUS_COLORS[run.status]}>
                {RUN_STATUS_LABELS[run.status] ?? run.status}
              </Tag>
            ) : <Tag color="warning">状态未知</Tag>}
            {run.dryRun ? <Tag>仅预演，未写入目录</Tag> : <Tag>正式采集</Tag>}
            {run.providerType ? (
              <span className="text-[12px] text-[#667085]">{PROVIDER_LABELS[run.providerType]}</span>
            ) : null}
            <span className="text-[12px] text-[#98a2b3]">耗时 {formatDuration(run.durationMs)}</span>
          </div>
          <div className="rounded-lg bg-[#f9fafb] px-3 py-2 text-[13px]">
            {summarizeRunCounts(run)}
            <div className="mt-1 text-[12px] text-[#98a2b3]">
              以上统计为本次元数据实体计数（可能包含库、表、列），不是表数量或本次完整表清单。
            </div>
            <div className="mt-1 text-[12px] text-[#98a2b3]">
              {formatMetadataTime(run.startedAt)} ~ {formatMetadataTime(run.finishedAt)} · 触发人{' '}
              {run.createdBy || '-'} · Run #{run.runId}
            </div>
          </div>
          <div className="text-[13px] font-medium text-[#344054]">② 哪些范围与缺口需要核对？</div>
          {scope ? (
            <div className="rounded-lg border border-solid border-[#e4e7ec] px-3 py-2 text-[12px] leading-6">
              <div>冻结来源：#{scope.dataSourceId} / {scope.databaseName || '全部库'} / {scope.schemaName || '默认 Schema'}</div>
              <div>冻结表匹配（SQL LIKE）：{scope.tablePattern || '全部表'}；字段：{scope.collectColumns === false ? '不采集列' : scope.collectColumns === true ? '采集列' : '未知'}</div>
              <div className="text-[#667085]">这是历史 Run 的快照；不代表此刻任务配置或当前目录未被后续采集更新。</div>
            </div>
          ) : (
            <Alert type="warning" showIcon message="本次历史范围快照不可用"
              description="不能用当前任务配置反推本次运行范围；请核对真实 Run/任务历史。" />
          )}
          {run.errorMessage ? (
            <Alert type="error" showIcon message="运行错误或未完成部分"
              description={run.errorMessage} />
          ) : null}
          {(run.cntPartialFailed ?? 0) > 0 ? (
            <Alert type="warning" showIcon message="本轮存在局部采集失败"
              description="其他已提交的元数据仍可能存在；不要将局部失败推断为整个来源不存在。" />
          ) : null}
          <div className="text-[13px] font-medium text-[#344054]">③ 下一步</div>
          {scope && !run.dryRun ? (
            <Button type="primary" onClick={() => {
              onClose();
              history.push(currentSourceCatalogPath(scope));
            }}>查看此来源当前已采集表</Button>
          ) : (
            <div className="text-[12px] text-[#667085]">需要正式采集与可核验的来源身份后，才能进入当前源表目录。</div>
          )}
          <div className="text-[12px] text-[#667085]">
            当前目录是另一次实时查询，不等于本次 Run 的精确完整成员清单。
          </div>
        </div>
      ) : null}
    </Modal>
  );
};

export default RunResultModal;
