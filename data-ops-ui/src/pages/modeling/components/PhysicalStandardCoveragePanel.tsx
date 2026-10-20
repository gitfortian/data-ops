import { Alert, Button, Collapse, Descriptions, Empty, Input, Modal, Segmented, Space, Table, Tag, Typography, message } from 'antd';
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
  /** Opt-in presentation for the DWD editor; other layers retain their existing view. */
  layerCode?: string;
  open: boolean;
  onClose: () => void;
  /** Navigation only; report column identity is revalidated in the editor. */
  onLocateColumn?: (column: PhysicalStandardCoverageInspection['columns'][number], report: PhysicalStandardCoverageInspection) => void;
  /** Previous evidence exists only in the current editor session after explicit repair navigation. */
  baseline?: PhysicalGovernanceBaseline;
}

/** Read-only evidence of current physical-field standards. NOT a publish gate. */
export default function PhysicalStandardCoveragePanel({ modelId, layerCode, open, onClose, onLocateColumn, baseline }: Props) {
  const { currentProject } = useSecurityProject();
  const isDwd = layerCode?.toUpperCase() === 'DWD';
  const scope = JSON.stringify([modelId, currentProject?.id, open, layerCode]);
  const [loadedScope, setLoadedScope] = useState<string>();
  const [report, setReport] = useState<PhysicalStandardCoverageInspection>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [columnView, setColumnView] = useState<'issues' | 'all'>('issues');

  const needsReview = (column: PhysicalStandardCoverageInspection['columns'][number]) =>
    column.status !== 'ENABLED_NOW' || Boolean(column.dataStandards && column.dataStandards.status !== 'COMPLETE_NOW');

  useEffect(() => {
    let active = true;
    setReport(undefined);
    setError(undefined);
    setKeyword('');
    if (!open) { setLoading(false); return () => { active = false; }; }
    setLoading(true);
    void inspectPhysicalStandardCoverage(modelId).then((result) => {
      if (active) {
        setLoadedScope(scope);
        if (result.modelId !== modelId) setError('标准覆盖报告模型身份与当前编辑器不一致，不能用于治理');
        else if (isDwd && result.layerCode !== 'DWD') setError('报告分层与当前 DWD 模型不一致，请重新检查。');
        else if (isDwd && (!Array.isArray(result.columns) || !Array.isArray(result.findings) || !Array.isArray(result.limitations))) {
          setError('标准检查结果不完整，请重新检查。');
        }
        else {
          setReport(result);
          if (isDwd) setColumnView(result.columns.some(needsReview) ? 'issues' : 'all');
        }
      }
    }).catch((error: unknown) => {
      if (active) {
        const reason = error instanceof Error ? error.message
          : '标准目录或项目模型不可访问，不能将错误视作覆盖合格';
        setLoadedScope(scope);
        setError(reason);
        if (!isDwd) message.error(reason);
      }
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [open, modelId, currentProject?.id, layerCode, reloadKey]);

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

  if (isDwd) {
    const current = loadedScope === scope ? report : undefined;
    const currentError = loadedScope === scope ? error : undefined;
    const currentFeedback = current && baseline ? comparePhysicalGovernance(baseline, current) : undefined;
    const issueColumns = current?.columns.filter(needsReview) ?? [];
    const search = keyword.trim().toLowerCase();
    const visibleColumns = (columnView === 'issues' ? issueColumns : current?.columns ?? [])
      .filter(column => `${column.columnName} ${column.standardName || ''} ${column.dataType}`.toLowerCase().includes(search));
    const statusLabels: Record<string, string> = {
      ENABLED_NOW: '标准字段有效', MISSING_STANDARD: '未关联标准字段',
      INVALID_STANDARD: '标准字段不可用', AGGREGATE_REVIEW: '需确认业务角色',
    };
    const renderStandards = (column: PhysicalStandardCoverageInspection['columns'][number]) => {
      const evidence = column.dataStandards;
      return <Descriptions size="small" column={1}>
        <Descriptions.Item label="物理列身份">{column.columnId}</Descriptions.Item>
        <Descriptions.Item label="标准字段身份">{column.stdFieldId ?? '未关联'}</Descriptions.Item>
        <Descriptions.Item label="业务语义角色">{column.semanticRole || '未设置'}</Descriptions.Item>
        <Descriptions.Item label="物理分析角色">{column.physicalRole || '未设置'}</Descriptions.Item>
        {column.aggregateFunc && <Descriptions.Item label="聚合方式">{column.aggregateFunc}</Descriptions.Item>}
        {evidence ? <>
          <Descriptions.Item label="类型标准">{referenceLabel(evidence.typeStandard)}</Descriptions.Item>
          {evidence.unitStandard && <Descriptions.Item label="单位标准">{referenceLabel(evidence.unitStandard)}</Descriptions.Item>}
          {evidence.caliberStandard && <Descriptions.Item label="口径标准">{referenceLabel(evidence.caliberStandard)}</Descriptions.Item>}
          {evidence.securityStandard && <Descriptions.Item label="安全标准">{referenceLabel(evidence.securityStandard)}</Descriptions.Item>}
          {evidence.codeSetStatus !== 'NOT_APPLICABLE' && <Descriptions.Item label="码集">
            {evidence.codeSetCode || '未配置'} · {codeSetStatusLabel(evidence.codeSetStatus)}
            {typeof evidence.codeSetItemCount === 'number' && typeof evidence.codeSetEnabledItemCount === 'number'
              ? `（启用 ${evidence.codeSetEnabledItemCount}/${evidence.codeSetItemCount}）` : ''}
          </Descriptions.Item>}
          {evidence.codeSetEvidenceSha256 && <Descriptions.Item label="码集内容摘要">
            <Typography.Text style={{ overflowWrap: 'anywhere' }} copyable>{evidence.codeSetEvidenceSha256}</Typography.Text>
          </Descriptions.Item>}
          <Descriptions.Item label="数据标准状态">{dataStandardStatusLabel(evidence.status)}</Descriptions.Item>
          <Descriptions.Item label="数据标准核对结果">{evidence.explanation}</Descriptions.Item>
        </> : <Descriptions.Item label="关联数据标准">标准字段未确认，暂未读取关联数据标准。</Descriptions.Item>}
      </Descriptions>;
    };
    return <Modal title="DWD 字段标准检查" width={1080} open={open} destroyOnHidden style={{ top: 24 }}
      styles={{ content: { display: 'flex', flexDirection: 'column', maxHeight: 'calc(100dvh - 48px)' },
        header: { flexShrink: 0 }, body: { minHeight: 0, overflowY: 'auto' }, footer: { flexShrink: 0, paddingTop: 12 } }}
      footer={<Space wrap>
        <Button loading={loading} onClick={() => setReloadKey(key => key + 1)}>重新检查</Button>
        <Button onClick={onClose}>关闭</Button>
      </Space>} onCancel={onClose}>
      {currentError && <Alert className="mb-3" showIcon type="error"
        message="标准检查未完成" description={currentError}
        action={<Button size="small" disabled={loading} onClick={() => setReloadKey(key => key + 1)}>重试</Button>} />}
      {loading && <Typography.Paragraph type="secondary">正在检查已保存字段的标准引用…</Typography.Paragraph>}
      {current && <>
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            <Typography.Text strong>{current.modelCode}</Typography.Text>
            <Typography.Paragraph type="secondary" className="!mb-0 !mt-1">
              {current.processName ? `业务过程：${current.processName} · ` : ''}共 {current.columnCount} 个字段，{issueColumns.length} 个字段待处理
            </Typography.Paragraph>
          </div>
          <Tag>{current.status === 'COVERED_NOW' ? '当前引用完整' : '有待确认项'}</Tag>
        </div>
        {currentFeedback && <Alert className="mb-3" showIcon
          type={currentFeedback.state === 'WRONG_OWNER' ? 'error' : 'info'}
          message={currentFeedback.state === 'CHANGED' ? '检查结果已更新'
            : currentFeedback.state === 'UNCHANGED' ? '检查结果与上次一致' : '无法比较不同模型的检查结果'}
          description={currentFeedback.explanation} />}
        {current.findings.length > 0 && <Alert className="mb-4" showIcon type="warning"
          message="请完善以下标准引用"
          description={<ul className="!mb-0 !pl-4">{current.findings.map((finding, index) => <li key={index}>{finding}</li>)}</ul>} />}
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <Segmented aria-label="字段检查范围" value={columnView}
            options={[{ value: 'issues', label: `待处理 (${issueColumns.length})` }, { value: 'all', label: `全部字段 (${current.columns.length})` }]}
            onChange={value => setColumnView(value as 'issues' | 'all')} />
          <Input.Search aria-label="搜索标准检查字段" placeholder="搜索字段、类型或标准名称" allowClear
            style={{ width: 280, maxWidth: '100%' }} value={keyword} onChange={event => setKeyword(event.target.value)} />
        </div>
        <Table size="small" rowKey="columnId" scroll={{ x: 800 }}
          pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }} dataSource={visibleColumns}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={search ? '没有匹配的字段' : columnView === 'issues' ? '当前没有待处理字段' : '模型暂无字段'}>
            {search && <Button size="small" onClick={() => setKeyword('')}>清空搜索</Button>}
          </Empty> }}
          expandable={{ expandedRowRender: renderStandards, columnTitle: '详情', columnWidth: 55,
            expandIcon: ({ expanded, onExpand, record }) => <Button type="link" size="small"
              aria-label={`${expanded ? '收起' : '查看'} ${record.columnName} 标准详情`}
              onClick={event => onExpand(record, event)}>{expanded ? '收起' : '展开'}</Button> }}
          columns={[
            { title: '物理字段', dataIndex: 'columnName', width: 160,
              render: (_: unknown, column) => <div><Typography.Text>{column.columnName}</Typography.Text>
                <div className="text-xs text-gray-500">{column.dataType}</div></div> },
            { title: '标准字段', dataIndex: 'standardName', width: 150,
              render: (_: unknown, column) => column.standardName || (column.stdFieldId == null ? '未关联' : `标准字段 #${column.stdFieldId}`) },
            { title: '当前状态', dataIndex: 'status', width: 150,
              render: (_: unknown, column) => <Space direction="vertical" size={2}>
                <Tag color={needsReview(column) ? 'gold' : 'default'}>{statusLabels[column.status] || column.status}</Tag>
                {column.dataStandards && column.dataStandards.status !== 'COMPLETE_NOW'
                  && <Typography.Text type="secondary" className="text-xs">{dataStandardStatusLabel(column.dataStandards.status)}</Typography.Text>}
              </Space> },
            { title: '核对结果', dataIndex: 'explanation',
              render: (_: unknown, column) => column.explanation },
            { title: '操作', key: 'action', width: 120,
              render: (_: unknown, column) => onLocateColumn && needsReview(column)
                ? <Button type="link" size="small" onClick={() => onLocateColumn(column, current)}>定位字段</Button> : '—' },
          ]} />
        <Collapse ghost className="mt-3" items={[{ key: 'details', label: '核验详情', children: <>
          <Typography.Paragraph type="secondary">本报告对照当前已保存字段与标准引用，供设计核对使用。</Typography.Paragraph>
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="模型身份">{current.modelId} · {current.layerCode} · {current.modelStatus}</Descriptions.Item>
            <Descriptions.Item label="标准字段">有效 {current.enabledCount} · 缺少 {current.missingCount} · 失效 {current.invalidCount}</Descriptions.Item>
            <Descriptions.Item label="数据标准">缺少 {current.missingDataStandardCount} · 失效 {current.invalidDataStandardCount} · 待确认 {current.reviewCount}</Descriptions.Item>
            <Descriptions.Item label="结构摘要"><Typography.Text style={{ overflowWrap: 'anywhere' }} copyable>{current.structureSha256}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="检查内容摘要"><Typography.Text style={{ overflowWrap: 'anywhere' }} copyable>{current.evidenceSha256}</Typography.Text></Descriptions.Item>
          </Descriptions>
          {current.limitations.map((value, index) => <Typography.Paragraph key={index} type="secondary" className="!mb-1">{value}</Typography.Paragraph>)}
        </> }]} />
      </>}
    </Modal>;
  }

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
