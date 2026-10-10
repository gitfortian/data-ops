import React from 'react';
import { Alert, Button, Space, Tag, Typography } from 'antd';
import { history } from '@umijs/max';
import {
  sourceSemanticCandidates, type AdoptionReceipt, type CandidateReview,
} from '@/services/agent/sourceSemanticCandidates';
import {
  modelingMetricNextSteps, verifiedSourceSemanticHandoff,
} from '@/services/agent/sourceSemanticHandoff';

/** F-039 5/5: only committed authoritative Semantic facts can navigate to original owners. */
const SourceSemanticHandoffPanel: React.FC<{
  taskId: string; review: CandidateReview; receipts: AdoptionReceipt[];
}> = ({ taskId, review, receipts }) => {
  const [busy, setBusy] = React.useState(false);
  const [problem, setProblem] = React.useState('');
  const [verifiedProcessId, setVerifiedProcessId] = React.useState<number | null>(null);
  const saved = receipts.filter((r) =>
    ['CREATED', 'REUSED', 'LINKED'].includes(r.status) &&
    Number.isSafeInteger(r.semanticId) && (r.semanticId ?? 0) > 0);

  const verify = async (receipt: AdoptionReceipt, goToOwner: boolean) => {
    setBusy(true); setProblem('');
    try {
      // The list in local React state is NOT a grant; re-read original owner on each action.
      const authoritative = await sourceSemanticCandidates.receipts(taskId);
      const result = await verifiedSourceSemanticHandoff(receipt, review, authoritative);
      if (!result) {
        setVerifiedProcessId(null);
        setProblem('正式回执已变化，或目标定义/关联未在当前项目中核对成功；请刷新回执并检查权限。');
        return;
      }
      if (receipt.kind === 'PROCESS' && receipt.semanticId != null)
        setVerifiedProcessId(receipt.semanticId);
      if (goToOwner) history.push(result.path);
    } catch {
      setVerifiedProcessId(null);
      setProblem('原 Semantic 目标不可读、当前项目不匹配或权限已撤销；不能跳转或宣称已交接。');
    } finally { setBusy(false); }
  };

  const openProfessionalStep = async (path: string) => {
    if (!verifiedProcessId) return;
    setBusy(true);setProblem('');
    try {
      const latest = await sourceSemanticCandidates.receipts(taskId);
      const processReceipt = latest.find((r) => r.kind === 'PROCESS' &&
        r.semanticId === verifiedProcessId &&
        ['CREATED', 'REUSED'].includes(r.status));
      if (!processReceipt || !await verifiedSourceSemanticHandoff(processReceipt, review, latest)) {
        setVerifiedProcessId(null);
        setProblem('业务过程已漂移或当前权限不可核对，禁止交接。');
        return;
      }
      // Destination pages enforce Modeling/Metric project+read permissions independently.
      // No model/metric IDs have been produced by this feature.
      history.push(path);
    } catch {
      setVerifiedProcessId(null);
      setProblem('当前身份无权核对原业务过程；请重新授权后从原页面继续。');
    } finally { setBusy(false); }
  };

  return (
    <section aria-label="F-039 正式对象及建模指标交接"
      style={{ borderTop: '1px solid #e6eaf0', marginTop: 12, paddingTop: 12 }}>
      <Typography.Text strong>5/5 · 正式语义成果及专业页面交接</Typography.Text>
      <Typography.Paragraph type="secondary">
        仅对 Semantic 原域已提交的正式 ID/关联提供入口，跳转前重读原回执和目标对象。
        这不是模型、指标的自动创建、发布、使用或质量验收。
      </Typography.Paragraph>
      {problem && <Alert type="warning" showIcon style={{ marginBottom: 8 }} message={problem} />}
      {saved.length ? saved.map((r) => (
        <div key={r.candidateId} style={{ marginBottom: 8 }}>
          <Space wrap>
            <Tag color="success">{r.status}</Tag>
            <span>{r.kind} · #{r.semanticId}</span>
            <Button disabled={busy} size="small" onClick={() => void verify(r, true)}>
              重新核对正式对象并进入原页面
            </Button>
            {r.kind === 'PROCESS' &&
              <Button disabled={busy} size="small" onClick={() => void verify(r, false)}>
                核验后提供建模/指标下一步
              </Button>}
          </Space>
        </div>
      )) : <Alert type="info" showIcon
        message="目前没有可证明成功提交的正式对象。候选和预检不能作为专业页面交接依据。" />}
      {verifiedProcessId && (
        <div style={{ marginTop: 10 }}>
          <Typography.Text strong>已核验过程 #{verifiedProcessId} · 原专业工作区</Typography.Text>
          <div style={{ marginTop: 6 }}>
            <Space wrap>
              {modelingMetricNextSteps(verifiedProcessId)
                .map((step) => <Button key={step.path} size="small" disabled={busy}
                  onClick={() => void openProfessionalStep(step.path)}>
                  {step.label}
                </Button>)}
            </Space>
          </div>
          <Typography.Paragraph type="secondary" style={{ marginTop: 5 }}>
            建模主线仅展示已有模型；指标管理仅以原正式业务过程过滤。目标页分别重新执行自身权限检查，
            若无模型或指标，需由治理人员在原页面建立与核验。
          </Typography.Paragraph>
        </div>
      )}
    </section>
  );
};
export default SourceSemanticHandoffPanel;
