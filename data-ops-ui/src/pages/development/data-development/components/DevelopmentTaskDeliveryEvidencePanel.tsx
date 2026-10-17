import { history } from '@umijs/max';
import { Alert, Button, Card, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import {
  inspectPublishedSqlRevision, listDevelopmentTaskExecutions, listDevelopmentTaskRevisions,
  type PublishedSqlRevisionEvidence,
} from '@/services/data-development';
import {
  inspectTaskRevisions, observeTaskExecutions,
  type TaskExecutionObservation, type TaskRevisionInspection,
} from '../taskDeliveryEvidence';

interface Props {
  nodeId: string;
  projectId: string | number | undefined;
}

const runTone = (status: string): string => {
  if (status === 'SUCCESS') return 'green';
  if (status === 'PENDING' || status === 'RUNNING') return 'blue';
  return 'orange';
};

/**
 * Read-only C5 evidence: DataDev's real task versions + bounded execution page.
 * Never claims the chosen SQL has a persisted link to the Modeling version.
 */
export default function DevelopmentTaskDeliveryEvidencePanel({ nodeId, projectId }: Props) {
  const [refresh, setRefresh] = useState(0);
  const [loading, setLoading] = useState(false);
  const [revisions, setRevisions] = useState<TaskRevisionInspection>();
  const [publishedEvidence, setPublishedEvidence] = useState<PublishedSqlRevisionEvidence>();
  const [publishedEvidenceProblem, setPublishedEvidenceProblem] = useState<string>();
  const [runs, setRuns] = useState<TaskExecutionObservation>();
  const [revisionProblem, setRevisionProblem] = useState<string>();
  const [runProblem, setRunProblem] = useState<string>();

  useEffect(() => {
    let active = true;
    setRevisions(undefined);
    setPublishedEvidence(undefined);
    setPublishedEvidenceProblem(undefined);
    setRuns(undefined);
    setRevisionProblem(undefined);
    setRunProblem(undefined);
    if (projectId == null) {
      setRevisionProblem('当前项目不明确，不能读取任务版本');
      setRunProblem('当前项目不明确，不能读取运行记录');
      return () => { active = false; };
    }
    setLoading(true);
    void Promise.allSettled([
      listDevelopmentTaskRevisions(nodeId),
      // This API filters SQL taskType, NOT nodeId. Never call its result a
      // complete task history or treat a zero-match sample as no execution.
      listDevelopmentTaskExecutions({ pageNo: 1, pageSize: 100, taskType: 'SQL' }),
    ]).then(async ([revisionResult, executionResult]) => {
      if (!active) return;
      if (revisionResult.status === 'fulfilled') {
        const result = inspectTaskRevisions(nodeId, revisionResult.value);
        if (result.state === 'INVALID_OWNER') setRevisionProblem(result.reason);
        else {
          setRevisions(result);
          if (result.state === 'PUBLISHED_REVISION_EXISTS') {
            const latest = result.revisions[0];
            try {
              const evidence = await inspectPublishedSqlRevision(nodeId, latest.revisionNo);
              if (!active) return;
              if (evidence.nodeId !== nodeId
                  || evidence.taskRevisionId !== latest.id
                  || evidence.taskRevisionNo !== latest.revisionNo
                  || evidence.immutableDefinitionSha256 !== latest.checksum
                  || evidence.authorizedForFrozenExecution !== false
                  || evidence.status !== 'PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED') {
                setPublishedEvidenceProblem('发布修订摘要、身份或执行授权状态与 DataDev 原域不一致');
              } else {
                setPublishedEvidence(evidence);
              }
            } catch (error: unknown) {
              if (active) setPublishedEvidenceProblem(error instanceof Error
                ? error.message : '精确发布任务修订校验失败');
            }
          }
        }
      } else {
        setRevisionProblem(revisionResult.reason instanceof Error
          ? revisionResult.reason.message : '任务已发布版本不可访问');
      }
      if (executionResult.status === 'fulfilled') {
        const result = observeTaskExecutions(nodeId, executionResult.value);
        if (result.state === 'INVALID_PAGE') setRunProblem(result.reason);
        else setRuns(result);
      } else {
        setRunProblem(executionResult.reason instanceof Error
          ? executionResult.reason.message : '运行历史不可访问');
      }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [nodeId, projectId, refresh]);

  return (
    <Card size="small" className="!mt-3" title="DataDev · 任务发布与真实运行证据（只读）"
      extra={<Space>
        <Button size="small" disabled={loading}
          onClick={() => setRefresh(x => x + 1)}>重读任务与运行证据</Button>
        <Button size="small" onClick={() => history.push('/data-development/executions')}>
          打开完整运行记录
        </Button>
      </Space>}>
      <Alert showIcon type="info" className="!mb-3"
        message="只核对人工选中的 DataDev SQL 任务；未确认其加工了上述冻结物理模型"
        description="任务草稿、已发布修订、任务执行回执是三种不同事实。即使存在 SUCCESS 回执，也无法据此证明数据已落仓、字段口径正确、与所选物理版本关联或已达到消费验收。" />
      {loading && <Typography.Paragraph type="secondary">正在读取 DataDev 当前项目任务版本和运行记录…</Typography.Paragraph>}
      {revisionProblem && <Alert showIcon type="error" className="!mb-3"
        message="任务已发布版本证据不可用" description={revisionProblem} />}
      {revisions && revisions.state === 'NO_PUBLISHED_REVISION' &&
        <Alert showIcon type="warning" className="!mb-3"
          message="DataDev 此 SQL 节点尚无可核对的已发布任务修订"
          description="草稿存在不代表任务版本发布，更不代表有运行结果。" />}
      {revisions && revisions.state === 'PUBLISHED_REVISION_EXISTS' && (
        <Space wrap className="!mb-3">
          <Tag color="blue">DataDev 已发布修订 {revisions.revisions.length} 个</Tag>
          <Tag>最新修订 V{revisions.revisions[0].revisionNo}</Tag>
          <Typography.Text type="secondary">
            最新修订证据：{revisions.revisions[0].checksum.slice(0, 16)}…
          </Typography.Text>
          <Typography.Text type="secondary">
            这是 DataDev 任务修订，不是 Modeling 物理版本，也不等于已上线
          </Typography.Text>
        </Space>
      )}
      {publishedEvidenceProblem && <Alert type="error" showIcon className="!mb-3"
        message="已发布 SQL 修订不可核验" description={publishedEvidenceProblem} />}
      {publishedEvidence && <Alert type="info" showIcon className="!mb-3"
        message={`TaskRevision #${publishedEvidence.taskRevisionId} · 冻结定义校验通过（未执行）`}
        description={<>
          <Typography.Text copyable={{ text: publishedEvidence.immutableDefinitionSha256 }}>
            SHA256：{publishedEvidence.immutableDefinitionSha256.slice(0, 20)}…
          </Typography.Text>
          <span> · {publishedEvidence.currentDraftMatchesPublishedDefinition
            ? '当前编辑器定义与冻结修订一致'
            : '当前编辑器已与冻结修订不同，禁止用编辑器运行冒充已发布任务运行'}</span>
          <span> · 未取得 DWD ModelVersion 绑定或冻结执行授权</span>
        </>} />}
      {runProblem && <Alert showIcon type="error" className="!mb-3"
        message="运行回执不可核查" description={runProblem} />}
      {runs && runs.state !== 'INVALID_PAGE' && (
        <>
          <Typography.Paragraph type="secondary" className="!mb-2">
            从当前项目 SQL 运行历史最新一页读取 {runs.scanned} 条，范围内共 {runs.total} 条。
            {runs.complete ? '当前这一查询范围已全部覆盖。' : '本次仅核查前 100 条，历史结果不完整。'}
            只展示 nodeId 完全匹配当前选中 SQL 节点的回执。
          </Typography.Paragraph>
          {runs.state === 'NOT_OBSERVED_IN_WINDOW' &&
            <Alert showIcon type="warning" className="!mb-3"
              message="当前查询范围没有观察到该节点的运行回执"
              description="这不是“从未运行”，也不是“运行失败”；若需核对更早历史，请进入完整运行记录。" />}
          {runs.state === 'OBSERVED' && (
            <Space direction="vertical" className="!w-full" size="small">
              <Tag>当前查询范围内匹配 {runs.matching.length} 条执行</Tag>
              {runs.matching.slice(0, 5).map(record => (
                <div key={record.id} className="rounded border border-[#e4e7ec] px-3 py-2">
                  <Space wrap>
                    <Typography.Text>运行 #{record.id}</Typography.Text>
                    <Tag color={runTone(record.status)}>{record.status}</Tag>
                    <Typography.Text type="secondary">
                      {record.startTime || '开始时间未知'}
                    </Typography.Text>
                    {record.failureReason && <Tag color="orange">{record.failureReason}</Tag>}
                  </Space>
                </div>
              ))}
              <Typography.Text type="secondary">
                仅显示前五条严格节点匹配的 DataDev 回执；SUCCESS 只表示该次运行成功，不能证明业务结果已验证。
              </Typography.Text>
            </Space>
          )}
        </>
      )}
    </Card>
  );
}
