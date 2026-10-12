import { Alert, Button, Modal, Tag } from 'antd';
import { ArrowRightOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';

import type { CollectRunRecord } from '@/services/metadata/types';
import {
  formatDuration,
  formatMetadataTime,
  PROVIDER_LABELS,
  RUN_STATUS_COLORS,
  RUN_STATUS_LABELS,
} from '../constants';
import { currentSourceCatalogPath, readFrozenHarvestScope } from '../scopeEvidence';

const shown = (value?: number | null) => value == null ? '—' : value;

/**
 * This modal uses the persisted RunView and frozen scope from Metadata Owner.
 * The current catalog is a separate read, not a complete historical run-member list.
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
  const counts = run ? [
    { label: '实体总数', value: run.cntTotal },
    { label: '新增', value: run.cntNew },
    { label: '更新', value: run.cntChanged },
    { label: '未变化', value: run.cntUnchanged },
  ] : [];

  return (
    <Modal
      open={Boolean(run)}
      title={<span className="text-[18px] font-semibold text-[#26364a]">{title}</span>}
      okText="完成"
      cancelButtonProps={{ hidden: true }}
      onOk={onClose}
      onCancel={onClose}
      width={700}
      styles={{ body: { paddingTop: 14 } }}
    >
      {run ? (
        <div className="space-y-4 py-1">
          <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-[#fbfcfe] p-4">
            <div className="mb-3 flex flex-wrap items-center gap-2">
              {run.status ? (
                <Tag color={RUN_STATUS_COLORS[run.status]}>{RUN_STATUS_LABELS[run.status] || run.status}</Tag>
              ) : <Tag color="warning">状态未知</Tag>}
              <Tag color={run.dryRun ? 'default' : 'blue'}>{run.dryRun ? '预演 · 不写入目录' : '正式采集'}</Tag>
              <span className="text-[12px] text-[#93a0b1]">{run.providerType ? PROVIDER_LABELS[run.providerType] : '未知通道'}</span>
            </div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {counts.map((item) => (
                <div key={item.label} className="rounded-xl border border-solid border-[#e9edf3] bg-white px-3 py-3">
                  <div className="text-[11px] text-[#8c98a8]">{item.label}</div>
                  <div className="mt-1 text-[22px] font-semibold tabular-nums text-[#2b3b50]">{shown(item.value)}</div>
                </div>
              ))}
            </div>
            <div className="mt-3 text-[12px] leading-5 text-[#8a98ab]">
              这是当次元数据实体数，可能包含数据库、表和列；不能当作本次发现表数量或完整清单。
            </div>
            <div className="mt-2 text-[12px] leading-5 text-[#8a98ab]">
              {formatMetadataTime(run.startedAt)} – {formatMetadataTime(run.finishedAt)} ·
              耗时 {formatDuration(run.durationMs)} · 操作人 {run.createdBy || '—'} · Run #{run.runId}
            </div>
          </section>

          {run.errorMessage ? (
            <Alert type="error" showIcon message="本轮执行存在错误" description={run.errorMessage} />
          ) : null}
          {(run.cntPartialFailed ?? 0) > 0 ? (
            <Alert type="warning" showIcon message={'局部失败 ' + run.cntPartialFailed + ' 个实体'}
              description="成功提交的实体可能已进入当前目录；不能因此推断整个来源消失。" />
          ) : null}
          {(run.cntGone ?? 0) > 0 ? (
            <Alert type="warning" showIcon message={'当轮存在 ' + run.cntGone + ' 条撤销记录'}
              description="请先核对冻结范围和有效采集轮次，不把目录当前数量当作缺席证据。" />
          ) : null}

          <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-white p-4">
            <h3 className="m-0 text-[14px] font-semibold text-[#2d3c50]">本轮冻结的采集范围</h3>
            <p className="mb-3 mt-1 text-[12px] text-[#8c99a9]">这是运行时保存的范围，不会跟随之后的任务编辑而变化。</p>
            {scope ? (
              <div className="grid grid-cols-1 gap-3 rounded-xl bg-[#f7f9fc] p-4 sm:grid-cols-2">
                <div>
                  <div className="text-[11px] text-[#8c99aa]">来源 / 数据库 / Schema</div>
                  <div className="mt-1 break-all text-[13px] font-medium text-[#34455c]">
                    #{scope.dataSourceId} / {scope.databaseName || '全部库'} / {scope.schemaName || '默认 Schema'}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] text-[#8c99aa]">表范围 / 列级采集</div>
                  <div className="mt-1 break-all text-[13px] font-medium text-[#34455c]">
                    {scope.tablePattern || '全部表'} · {scope.collectColumns === false ? '不采集列' : scope.collectColumns === true ? '采集列' : '列级范围未知'}
                  </div>
                </div>
              </div>
            ) : (
              <Alert type="warning" showIcon message="缺少可信的冻结范围快照"
                description="不能用当前任务配置反推历史运行范围；请先核对任务运行证据。" />
            )}
          </section>

          <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-white p-4">
            <h3 className="m-0 text-[14px] font-semibold text-[#2d3c50]">接下来做什么？</h3>
            <p className="mb-3 mt-1 text-[12px] leading-5 text-[#8c99a9]">
              核对当前来源的真实表与列。目录是另外一次实时查询，并非这个 Run 的完整历史表清单。
            </p>
            {scope && !run.dryRun ? (
              <Button type="primary" onClick={() => {
                onClose();
                history.push(currentSourceCatalogPath(scope));
              }}>查看此来源当前已采集表 <ArrowRightOutlined /></Button>
            ) : (
              <div className="text-[12px] text-[#8a97a9]">
                {run.dryRun ? '这次是预演，未写入目录；需要正式采集后才能核对已采表。' : '缺少可核验的来源身份，暂时无法跳转到当前来源目录。'}
              </div>
            )}
          </section>
        </div>
      ) : null}
    </Modal>
  );
};

export default RunResultModal;
