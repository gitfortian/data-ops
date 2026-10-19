import { history } from '@umijs/max';
import { Alert, Button, Card, Space, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  inspectPhysicalStandardCoverage, type PhysicalStandardCoverageInspection,
} from '@/services/modeling/api';
import { physicalEvidencePath } from '@/utils/logicalPhysicalJourney';

type Props = {
  physicalModelId: number;
  physicalStructureSha256: string;
  processId: number;
  logicalModelId: number;
  logicalVersionNo: number;
};

type InspectionState = 'NOT_CHECKED' | 'READING' | 'ERROR' | 'NEEDS_REVIEW' | 'COVERED_NOW';

/**
 * Read-only prepublication *review*, never an authorization signal or a write
 * gate. A durable publish-time standard snapshot requires DR-06 signoff.
 */
export default function DwdStandardReviewCard({
  physicalModelId, physicalStructureSha256, processId, logicalModelId,
  logicalVersionNo,
}: Props) {
  const { currentProject } = useSecurityProject();
  const sequence = useRef(0);
  const [state, setState] = useState<InspectionState>('NOT_CHECKED');
  const [report, setReport] = useState<PhysicalStandardCoverageInspection>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    sequence.current += 1;
    setReport(undefined);
    setError(undefined);
    setState('NOT_CHECKED');
    return () => { sequence.current += 1; };
  }, [currentProject?.id, physicalModelId, physicalStructureSha256,
    logicalModelId, logicalVersionNo]);

  const checkCurrentStandards = async () => {
    const request = ++sequence.current;
    setReport(undefined);
    setError(undefined);
    setState('READING');
    try {
      const result = await inspectPhysicalStandardCoverage(physicalModelId);
      if (request !== sequence.current) return;
      if (result.modelId !== physicalModelId || result.layerCode !== 'DWD'
          || result.structureSha256 !== physicalStructureSha256
          || !/^[a-f0-9]{64}$/.test(result.evidenceSha256)
          || !['COVERED_NOW', 'NEEDS_REVIEW'].includes(result.status)) {
        throw new Error('标准证据与当前 DWD 目标或物理结构不一致，请重新预检物理模型');
      }
      setReport(result);
      setState(result.status);
    } catch (cause) {
      if (request !== sequence.current) return;
      setState('ERROR');
      setError(cause instanceof Error ? cause.message : '无法读取当前项目的标准证据');
    }
  };

  const coordinates = physicalEvidencePath({
    processId, logicalId: logicalModelId, logicalVersionNo,
    physicalId: physicalModelId,
  });
  const repairPath = coordinates ? `${coordinates}&review=standard-coverage` : null;

  return (
    <Card size="small" className="mt-3" title="发布前检查 · 当前标准与修复入口（只读）"
      extra={<Tag color={state === 'COVERED_NOW' ? 'blue'
        : state === 'NEEDS_REVIEW' || state === 'ERROR' ? 'warning' : 'default'}>
        {state === 'COVERED_NOW' ? '当前未发现覆盖缺口'
          : state === 'NEEDS_REVIEW' ? '需修复'
            : state === 'ERROR' ? '核验不可用'
              : state === 'READING' ? '读取中' : '尚未核验'}
      </Tag>}>
      <Typography.Paragraph type="secondary">
        对照当前已保存的 DWD 物理结构读取 StandardField 和关联数据标准。
        这是帮助人工修复的即时证据，**不是**发布资格、审批冻结快照或 DR-06 已批准的校验器。
      </Typography.Paragraph>
      <Space wrap>
        <Button onClick={() => void checkCurrentStandards()}
          loading={state === 'READING'}>核验当前物理标准</Button>
        <Button disabled={!repairPath} onClick={() => {
          if (repairPath) history.push(repairPath);
        }}>打开物理模型修复缺口</Button>
      </Space>
      {state === 'ERROR' && <Alert className="mt-3" type="error" showIcon
        message="标准证据无法核实" description={error} />}
      {report && <div className="mt-3">
        <Space wrap className="mb-2">
          <Tag>物理列 {report.columnCount}</Tag>
          <Tag color="orange">缺少字段 {report.missingCount}</Tag>
          <Tag color="red">字段失效 {report.invalidCount}</Tag>
          <Tag color="orange">缺少必需数据标准 {report.missingDataStandardCount}</Tag>
          <Tag color="red">数据标准失效 {report.invalidDataStandardCount}</Tag>
          <Tag color="orange">待人工审阅 {report.reviewCount}</Tag>
        </Space>
        <Alert showIcon type={state === 'NEEDS_REVIEW' ? 'warning' : 'info'}
          message={state === 'NEEDS_REVIEW'
            ? '当前存在治理缺口，建议进入物理模型逐列修复'
            : '本次读取未发现覆盖缺口，但不能据此声明已获正式发布授权'}
          description={report.findings.slice(0, 4).join('；')
            || '请继续检查逻辑映射与正式发布规则。'} />
        {report.findings.length > 4 && <Typography.Text type="secondary">
          另有 {report.findings.length - 4} 项，打开物理模型查看完整报告。
        </Typography.Text>}
        <Typography.Paragraph type="secondary" className="!mt-2 !mb-0">
          当前证据 SHA-256：<Typography.Text copyable={{ text: report.evidenceSha256 }}>
            {report.evidenceSha256.slice(0, 16)}…
          </Typography.Text>。证据未冻结，发布时仍须重新校验。
        </Typography.Paragraph>
      </div>}
    </Card>
  );
}
