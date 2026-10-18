import { Alert, Button, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { comparePhysicalGovernance, type PhysicalGovernanceBaseline } from '../governanceRecheck';
import {
  inspectPhysicalStandardCoverage,
  type PhysicalStandardCoverageInspection,
  type PhysicalStandardReferenceEvidence,
} from '@/services/modeling/api';

interface Props {
  modelId: number;
  open: boolean;
  onClose: () => void;
  /** Navigation only; report column identity is revalidated in the editor. */
  onLocateColumn?: (column: PhysicalStandardCoverageInspection['columns'][number], report: PhysicalStandardCoverageInspection) => void;
  /** Previous evidence exists only in the current editor session after explicit repair navigation. */
  baseline?: PhysicalGovernanceBaseline;
}

/** Read-only evidence of current physical-field standards. NOT a publish gate. */
export default function PhysicalStandardCoveragePanel({ modelId, open, onClose, onLocateColumn, baseline }: Props) {
  const { currentProject } = useSecurityProject();
  const [report, setReport] = useState<PhysicalStandardCoverageInspection>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setReport(undefined);
    setError(undefined);
    if (!open) { setLoading(false); return () => { active = false; }; }
    setLoading(true);
    void inspectPhysicalStandardCoverage(modelId).then((result) => {
      if (active) {
        if (result.modelId !== modelId) setError('标准覆盖报告模型身份与当前编辑器不一致，不能用于治理');
        else setReport(result);
      }
    }).catch((error: unknown) => {
      if (active) {
        const reason = error instanceof Error ? error.message
          : '标准目录或项目模型不可访问，不能将错误视作覆盖合格';
        setError(reason);
        message.error(reason);
      }
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [open, modelId, currentProject?.id, reloadKey]);

  const feedback = report && baseline ? comparePhysicalGovernance(baseline, report) : undefined;
  const referenceLabel = (ref: PhysicalStandardReferenceEvidence): string => {
    const name = ref.name || ref.code || (ref.id ? `#${ref.id}` : '未配置');
    const kind = ref.id ? ` (${ref.kind})` : '';
    const definition = ref.id && ref.definition ? ` · ${ref.definition}` : '';
    const status = ref.evidenceStatus === 'ENABLED_NOW' ? `当前启用 v${ref.version}`
      : ref.evidenceStatus === 'MISSING_REQUIRED_REFERENCE' ? '缺少必需引用'
        : ref.evidenceStatus === 'INVALID_REFERENCE' ? '引用无效/停用'
          : '未配置';
    return `${name}${kind}${definition} · ${status}`;
  };
  const dataStandardStatusLabel = (status: string): string => {
    switch (status) {
      case 'COMPLETE_NOW': return '当前适用引用完整';
      case 'MISSING_REQUIRED_REFERENCE': return '缺少必需引用';
      case 'INVALID_REFERENCE': return '存在失效引用';
      case 'NEEDS_REVIEW': return '需要人工核验';
      default: return '需要人工核验';
    }
  };
  const codeSetStatusLabel = (status: string): string => {
    switch (status) {
      case 'NOT_APPLICABLE': return '不适用';
      case 'NOT_CONFIGURED': return '未配置（选填）';
      case 'ENABLED_NOW': return '当前全部启用';
      case 'DISABLED': return '码集全部停用';
      case 'MIXED': return '存在停用码值';
      case 'INVALID_REFERENCE': return '码集不存在或不可访问';
      default: return '需要人工核验';
    }
  };

  return (
    <Modal title={report
      ? `${report.layerCode} 当前字段标准覆盖与治理缺口（只读）`
      : '非 ODS 当前字段标准覆盖与治理缺口（只读）'}
      width={1080} open={open} destroyOnHidden
      footer={<Space>
        <Button disabled={loading} onClick={() => setReloadKey(key => key + 1)}>重新读取当前治理证据</Button>
        <Button onClick={onClose}>关闭</Button>
      </Space>} onCancel={onClose}>
      {error && <Alert className="mb-3" showIcon type="error"
        message="本次治理报告不可用" description={error} />}
      {feedback && <Alert className="mb-3" showIcon
        type={feedback.state === 'WRONG_OWNER' ? 'error' : 'info'}
        message={feedback.state === 'CHANGED' ? '已重新核验：本次证据与修正前不同'
          : feedback.state === 'UNCHANGED' ? '已重新核验：证据尚未变化'
            : '无法比较不同对象的证据'}
        description={feedback.explanation} />}
      {loading && <Typography.Paragraph type="secondary">正在核验已保存的模型与正式标准字段…</Typography.Paragraph>}
      {report && <>
        <Alert showIcon className="mb-3"
          type={report.status === 'COVERED_NOW' ? 'info' : 'warning'}
          message={report.status === 'COVERED_NOW'
            ? '当前字段的标准字段及适用数据标准引用均无待核验项；此结果仅供人工设计审阅'
            : '当前物理设计存在标准字段、关联数据标准或业务含义待核实项'}
          description={['DWS', 'ADS'].includes(report.layerCode)
            ? '本报告核验当前标准字段及其已配置的数据标准引用；不核验 DWS 粒度/上游版本/MetricVersionRef，也不核验 ADS 消费合同/脱敏/消费者授权；正式发布资格不会因本报告改变。'
            : '核验已保存的物理结构、标准字段与当前关联的数据标准引用。正式发布强门禁仍待相关 Owner 完成签收，技术列豁免尚未获批；本报告不会自动改动任何发布状态。'} />
        <Space wrap className="mb-3">
          <Tag>{report.layerCode} · {report.modelStatus}</Tag>
          {['DWD', 'DIM'].includes(report.layerCode)
            ? <Tag>正式业务过程：{report.processName || '未绑定或不可访问'}</Tag>
            : <Tag>分层专项合同：需单独核验</Tag>}
          <Tag>物理列 {report.columnCount}</Tag>
          <Tag color="green">标准字段有效 {report.enabledCount}</Tag>
          <Tag color={report.missingCount ? 'orange' : 'default'}>缺少标准字段 {report.missingCount}</Tag>
          <Tag color={report.invalidCount ? 'red' : 'default'}>失效标准字段 {report.invalidCount}</Tag>
          <Tag color={report.missingDataStandardCount ? 'orange' : 'default'}>
            缺少必需数据标准 {report.missingDataStandardCount}
          </Tag>
          <Tag color={report.invalidDataStandardCount ? 'red' : 'default'}>
            失效数据标准 {report.invalidDataStandardCount}
          </Tag>
          <Tag color={report.reviewCount ? 'orange' : 'default'}>聚合待确认 {report.reviewCount}</Tag>
        </Space>
        <Typography.Paragraph type="secondary">
          本次核验指纹：
          <Typography.Text copyable={{ text: report.evidenceSha256 }}>
            {report.evidenceSha256.slice(0, 20)}…
          </Typography.Text>
        </Typography.Paragraph>
        {report.findings.map((finding, i) => (
          <Typography.Paragraph key={i} type="danger" className="!mb-1">{finding}</Typography.Paragraph>
        ))}
        <Table size="small" rowKey="columnId" pagination={{ pageSize: 15 }}
          dataSource={report.columns} columns={[
            { title: '物理列', dataIndex: 'columnName', width: 130 },
            { title: '物理类型', dataIndex: 'dataType', width: 105 },
            { title: '正式标准 ID', dataIndex: 'stdFieldId', width: 120,
              render: (id?: number) => id ?? '未绑定' },
            { title: '当前标准名称', dataIndex: 'standardName', width: 150,
              render: (name?: string) => name || '—' },
            { title: '关联数据标准', key: 'dataStandards', width: 260,
              render: (_: unknown, column: PhysicalStandardCoverageInspection['columns'][number]) => {
                const evidence = column.dataStandards;
                if (!evidence) {
                  return <Typography.Text type="secondary">标准字段无效，未继续读取</Typography.Text>;
                }
                return <Space direction="vertical" size={0}>
                  {<Typography.Text>类型：{referenceLabel(evidence.typeStandard)}</Typography.Text>}
                  {evidence.unitStandard
                    && <Typography.Text>单位：{referenceLabel(evidence.unitStandard)}</Typography.Text>}
                  {evidence.caliberStandard
                    && <Typography.Text>口径：{referenceLabel(evidence.caliberStandard)}</Typography.Text>}
                  {evidence.codeSetStatus !== 'NOT_APPLICABLE'
                    && <Typography.Text>
                      码集：{evidence.codeSetCode || '未配置'} · {codeSetStatusLabel(evidence.codeSetStatus)}
                      {typeof evidence.codeSetItemCount === 'number'
                        && typeof evidence.codeSetEnabledItemCount === 'number'
                        ? `（启用 ${evidence.codeSetEnabledItemCount}/${evidence.codeSetItemCount}）` : ''}
                    </Typography.Text>}
                  {evidence.codeSetEvidenceSha256
                    && <Typography.Text type="secondary"
                      copyable={{ text: evidence.codeSetEvidenceSha256 }}>
                      码集指纹：{evidence.codeSetEvidenceSha256.slice(0, 12)}…
                    </Typography.Text>}
                  {evidence.securityStandard
                    && <Typography.Text>安全：{referenceLabel(evidence.securityStandard)}</Typography.Text>}
                  <Tag color={evidence.status === 'COMPLETE_NOW' ? 'green'
                    : evidence.status === 'INVALID_REFERENCE' || evidence.status === 'NEEDS_REVIEW'
                      ? 'red' : 'orange'}>{dataStandardStatusLabel(evidence.status)}</Tag>
                  <Typography.Text type="secondary">{evidence.explanation}</Typography.Text>
                </Space>;
              } },
            { title: 'Semantic 角色', dataIndex: 'semanticRole', width: 115,
              render: (role?: string) => role || '—' },
            { title: '物理分析角色', dataIndex: 'physicalRole', width: 115,
              render: (role?: string) => role || '—' },
            { title: '覆盖证据', dataIndex: 'status', width: 155,
              render: (status: string) => <Tag color={status === 'ENABLED_NOW'
                ? 'green' : status === 'INVALID_STANDARD' ? 'red' : 'orange'}>{status}</Tag> },
            { title: '为何需要复核', dataIndex: 'explanation' },
            { title: '下一步', key: 'action', width: 135,
              render: (_: unknown, column: PhysicalStandardCoverageInspection['columns'][number]) =>
                onLocateColumn && column.status !== 'ENABLED_NOW'
                  ? <Button type="link" size="small"
                      onClick={() => onLocateColumn(column, report)}>定位当前物理列</Button>
                  : <Typography.Text type="secondary">—</Typography.Text> },
          ]} />
        <Typography.Title level={5}>核验适用范围</Typography.Title>
        {report.limitations.map((value, i) => (
          <Typography.Paragraph key={i} type="secondary" className="!mb-1">{value}</Typography.Paragraph>
        ))}
      </>}
    </Modal>
  );
}
