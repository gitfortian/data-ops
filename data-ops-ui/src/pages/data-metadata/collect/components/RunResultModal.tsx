import { Alert, Button, Collapse, Descriptions, Modal, Tooltip } from 'antd';
import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { CollectRunRecord } from '@/services/metadata/types';
import { formatDuration, formatMetadataTime } from '../constants';
import { currentSourceCatalogPath, readFrozenHarvestScope } from '../scopeEvidence';
import '../index.less';

const shown = (value?: number | null) => (value == null ? '—' : value);

export default function RunResultModal({
  run,
  title,
  sourceNames = {},
  onClose,
}: {
  run: CollectRunRecord | null;
  title: string;
  sourceNames?: Record<string, string>;
  onClose: () => void;
}) {
  const scope = readFrozenHarvestScope(run?.scopeSnapshot);
  const preview = Boolean(run?.dryRun);
  const partial = (run?.cntPartialFailed ?? 0) > 0;
  const status = run?.status;
  const summary = !status
    ? '执行状态待确认'
    : preview
      ? status === 'SUCCESS'
        ? partial
          ? '采集预览部分完成'
          : '采集预览已完成'
        : status === 'RUNNING'
          ? '正在预览采集范围'
          : '采集预览未完成'
      : status === 'SUCCESS'
        ? partial
          ? '采集部分完成'
          : '采集已完成'
        : status === 'RUNNING'
          ? '正在采集'
          : status === 'SUSPECT'
            ? '采集已停止'
            : '采集失败';
  const icon =
    status === 'SUCCESS' && !partial ? (
      <CheckCircleOutlined />
    ) : status === 'RUNNING' ? (
      <ClockCircleOutlined />
    ) : (
      <ExclamationCircleOutlined />
    );
  return (
    <Modal
      open={Boolean(run)}
      title={title.replace(/dry[- ]?run\s*/gi, '').replace(/预演/g, '采集预览')}
      width={680}
      className="metadata-run-result"
      onCancel={onClose}
      footer={
        <div className="collect-result-actions">
          {scope && !preview ? (
            <Button
              onClick={() => {
                onClose();
                history.push(currentSourceCatalogPath(scope));
              }}
            >
              查看已采集表 <ArrowRightOutlined />
            </Button>
          ) : null}
          <Button type="primary" onClick={onClose}>
            完成
          </Button>
        </div>
      }
    >
      {run ? (
        <>
          <div
            className={`collect-result-summary ${status === 'SUCCESS' && !partial ? 'success' : status === 'RUNNING' ? 'running' : 'warning'}`}
          >
            <div className="collect-result-icon">{icon}</div>
            <div>
              <h2>{summary}</h2>
              <p>
                {preview
                  ? '仅预览结构，不更新元数据目录'
                  : `${formatMetadataTime(run.finishedAt || run.startedAt)} · 耗时 ${formatDuration(run.durationMs)}`}
              </p>
            </div>
          </div>
          <div className="collect-result-counts">
            {[
              { label: '元数据总数', value: run.cntTotal, help: '包含数据库、表及字段' },
              { label: preview ? '预计新增' : '新增', value: run.cntNew },
              { label: preview ? '预计更新' : '更新', value: run.cntChanged },
              { label: '未变化', value: run.cntUnchanged },
            ].map((item) => (
              <div key={item.label}>
                <Tooltip title={item.help}>
                  <span>{item.label}</span>
                </Tooltip>
                <strong>{shown(item.value)}</strong>
              </div>
            ))}
          </div>
          {partial ? <Alert type="warning" showIcon message={`${run.cntPartialFailed} 项处理失败`} /> : null}
          {(run.cntGone ?? 0) > 0 ? (
            <Alert type="warning" showIcon message={`${run.cntGone} 项元数据${preview ? '预计撤销' : '已撤销'}`} />
          ) : null}
          {run.errorMessage ? (
            <Collapse
              ghost
              items={[
                {
                  key: 'error',
                  label: '查看错误详情',
                  children: (
                    <pre className="collect-error-detail">{run.errorMessage.replace(/dry[- ]?run/gi, '采集预览')}</pre>
                  ),
                },
              ]}
            />
          ) : null}
          <section className="collect-result-scope">
            <h3>本次采集范围</h3>
            {scope ? (
              <Descriptions
                size="small"
                column={2}
                items={[
                  {
                    key: 'source',
                    label: '数据源',
                    children: sourceNames[String(scope.dataSourceId)] || `数据源 ${scope.dataSourceId}`,
                  },
                  { key: 'database', label: '库名', children: scope.databaseName || '全部库' },
                  { key: 'schema', label: 'Schema', children: scope.schemaName || '未限定' },
                  { key: 'table', label: '表范围', children: scope.tablePattern || '全部表' },
                  {
                    key: 'columns',
                    label: '字段结构',
                    children:
                      scope.collectColumns === true ? '包含字段' : scope.collectColumns === false ? '仅表结构' : '—',
                  },
                ]}
              />
            ) : (
              <Alert type="warning" showIcon message="本次采集范围暂不可用" />
            )}
          </section>
          <div className="collect-result-meta">
            <span>
              {preview ? `${formatMetadataTime(run.startedAt)} · ${formatDuration(run.durationMs)} · ` : ''}操作人{' '}
              {run.createdBy || '—'}
            </span>
            <span>运行编号 {run.runId}</span>
          </div>
        </>
      ) : null}
    </Modal>
  );
}
