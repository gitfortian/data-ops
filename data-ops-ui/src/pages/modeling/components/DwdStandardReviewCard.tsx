import { history } from '@umijs/max';
import { Alert, Card, Collapse, Descriptions, Space, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { YakButton } from '@/components/ui';
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

/** Reads current standard coverage without authorizing or publishing a design. */
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
          || !['COVERED_NOW', 'NEEDS_REVIEW'].includes(result.status)
          || !Array.isArray(result.findings)
          || !Number.isSafeInteger(result.columnCount) || result.columnCount <= 0
          || !Number.isSafeInteger(result.missingCount) || result.missingCount < 0
          || !Number.isSafeInteger(result.invalidCount) || result.invalidCount < 0
          || !Number.isSafeInteger(result.reviewCount) || result.reviewCount < 0
          || !Number.isSafeInteger(result.missingDataStandardCount)
          || result.missingDataStandardCount < 0
          || !Number.isSafeInteger(result.invalidDataStandardCount)
          || result.invalidDataStandardCount < 0
          || (result.status === 'COVERED_NOW' && (
            result.findings.length > 0 || result.missingCount > 0
            || result.invalidCount > 0 || result.missingDataStandardCount > 0
            || result.invalidDataStandardCount > 0 || result.reviewCount > 0))) {
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


  const gaps = report ? [
    { label: '未关联标准字段', count: report.missingCount },
    { label: '标准字段不可用', count: report.invalidCount },
    { label: '缺少数据标准', count: report.missingDataStandardCount },
    { label: '数据标准不可用', count: report.invalidDataStandardCount },
    { label: '需人工确认', count: report.reviewCount },
  ] : [];

  return (
    <Card size="small" className="mt-3" title="物理字段标准检查"
      extra={<Tag color={state === 'NEEDS_REVIEW' ? 'gold' : 'default'}>
        {state === 'COVERED_NOW' ? '已检查'
          : state === 'NEEDS_REVIEW' ? '需完善'
            : state === 'ERROR' ? '检查失败'
              : state === 'READING' ? '检查中' : '待检查'}
      </Tag>}>
      <Typography.Paragraph type="secondary" className="!mb-3">
        检查物理字段的标准引用，定位需要完善的字段。
      </Typography.Paragraph>
      <Space wrap>
        <YakButton onClick={() => void checkCurrentStandards()}
          loading={state === 'READING'}>{report ? '重新检查' : '检查标准'}</YakButton>
        <YakButton disabled={!repairPath} onClick={() => {
          if (repairPath) history.push(repairPath);
        }}>打开物理模型</YakButton>
      </Space>
      {state === 'ERROR' && <Alert className="mt-3" type="error" showIcon
        message="标准检查失败，请重试" description={error} />}
      {report && <div className="mt-3">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <Typography.Text type="secondary">已检查 {report.columnCount} 个物理字段</Typography.Text>
          {gaps.filter(x => x.count > 0).map(x => <Tag key={x.label} color="gold">{x.label} {x.count}</Tag>)}
        </div>
        {state === 'NEEDS_REVIEW' ? <Alert showIcon type="warning"
          message="请在物理模型中完善标准引用"
          description={report.findings.length ? <ul className="m-0 pl-4">{report.findings.slice(0, 4).map((finding, index) => <li key={index}>{finding}</li>)}</ul> : '存在需人工确认的字段，请打开物理模型查看。'} />
          : <Typography.Text>当前标准覆盖完整</Typography.Text>}
        {report.findings.length > 4 && <Typography.Paragraph type="secondary" className="!mt-2 !mb-0">
          另有 {report.findings.length - 4} 项，请在物理模型中查看。
        </Typography.Paragraph>}
        <Typography.Paragraph type="secondary" className="!mt-3 !mb-1 text-xs">
          当前检查结果供设计参考。
        </Typography.Paragraph>
        <Collapse ghost size="small" items={[{ key: 'details', label: '查看核验详情', children: (
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="物理模型">{report.modelCode} · #{report.modelId}</Descriptions.Item>
            {gaps.map(x => <Descriptions.Item key={x.label} label={x.label}>{x.count}</Descriptions.Item>)}
            <Descriptions.Item label="结构摘要"><Typography.Text className="break-all" copyable>{report.structureSha256}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="核验摘要"><Typography.Text className="break-all" copyable>{report.evidenceSha256}</Typography.Text></Descriptions.Item>
          </Descriptions>
        ) }]} />
      </div>}
    </Card>
  );
}
