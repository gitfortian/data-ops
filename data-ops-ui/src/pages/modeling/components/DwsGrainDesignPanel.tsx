import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Steps,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getDwsGrain, listDwsGrainCandidates, saveDwsGrain,
} from '@/services/modeling/dwsGrain';
import type {
  DwsGrainCandidate, DwsGrainSaved, SaveDwsGrainRequest,
} from '@/services/modeling/dwsGrain';
import type { ModelingStructureRecord } from '@/services/modeling/types';

const { Text, Title } = Typography;
const ALLOWED_AGGREGATES = new Set(['SUM', 'COUNT', 'COUNT_DISTINCT', 'MAX', 'MIN', 'AVG']);

interface Props {
  modelId: number;
  structure: ModelingStructureRecord;
  dirty: boolean;
}

type StaticValidation = {
  signature: string;
  status: 'PASS' | 'BLOCKED';
  issues: string[];
  checkedAt: string;
};

const sortNumbers = (values: number[]) => [...values].sort((a, b) => a - b);
const sameNumbers = (left: number[], right: number[]) =>
  JSON.stringify(sortNumbers(left)) === JSON.stringify(sortNumbers(right));

const validTimeZone = (value: string) => {
  if (!value.trim()) return false;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value.trim() });
    return true;
  } catch {
    return false;
  }
};

function sameSavedDesign(saved: DwsGrainSaved, payload: SaveDwsGrainRequest) {
  const design = saved.design;
  const sameMeasures = JSON.stringify(design.measures.map((item) => ({
    column: item.column, function: item.function,
  }))) === JSON.stringify(payload.measures);
  return saved.structureFingerprint === payload.expectedStructureFingerprint
    && design.statPeriod === payload.statPeriod
    && design.timeZone === payload.timeZone
    && design.businessTimeColumn === payload.businessTimeColumn
    && design.periodBucketColumn === payload.periodBucketColumn
    && JSON.stringify(design.dimensions) === JSON.stringify(payload.dimensions)
    && sameMeasures
    && sameNumbers(design.upstreamVersions.map((item) => item.modelId), payload.upstreamModelIds);
}

/** DWS design contract in the existing model editor. Sample figures are illustrative only. */
export default function DwsGrainDesignPanel({ modelId, structure, dirty }: Props) {
  const [saved, setSaved] = useState<DwsGrainSaved | null>(null);
  const [candidates, setCandidates] = useState<DwsGrainCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statPeriod, setStatPeriod] = useState('1d');
  const [timeZone, setTimeZone] = useState('Asia/Shanghai');
  const [businessTimeColumn, setBusinessTimeColumn] = useState('event_time');
  const [periodBucketColumn, setPeriodBucketColumn] = useState<string>();
  const [dimensions, setDimensions] = useState<string[]>([]);
  const [measures, setMeasures] = useState<string[]>([]);
  const [upstreamIds, setUpstreamIds] = useState<number[]>([]);
  const [validation, setValidation] = useState<StaticValidation | null>(null);
  const [sampleExampleOpen, setSampleExampleOpen] = useState(false);
  const [sourceMigrationConfirmed, setSourceMigrationConfirmed] = useState(false);
  const [keepFrozen, setKeepFrozen] = useState(false);

  const columns = structure.columns ?? [];
  const dimensionColumns = useMemo(() => columns.filter(
    (field) => field.columnName && field.fieldRole === 'DIMENSION' && field.stdFieldId,
  ), [columns]);
  const measureColumns = useMemo(() => columns.filter(
    (field) => field.columnName && field.fieldRole === 'MEASURE' && field.stdFieldId
      && field.aggregateFunc && ALLOWED_AGGREGATES.has(field.aggregateFunc),
  ), [columns]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [stored, sources] = await Promise.all([
        getDwsGrain(modelId), listDwsGrainCandidates(modelId),
      ]);
      setSaved(stored);
      setCandidates(sources ?? []);
      setValidation(null);
      setSourceMigrationConfirmed(false);
      setKeepFrozen(false);
      if (stored) {
        setStatPeriod(stored.design.statPeriod);
        setTimeZone(stored.design.timeZone);
        setBusinessTimeColumn(stored.design.businessTimeColumn);
        setPeriodBucketColumn(stored.design.periodBucketColumn);
        setDimensions(stored.design.dimensions);
        setMeasures(stored.design.measures.map((item) => item.column));
        setUpstreamIds(stored.design.upstreamVersions.map((item) => item.modelId));
      } else {
        setStatPeriod('1d');
        setTimeZone('Asia/Shanghai');
        setBusinessTimeColumn('event_time');
        setPeriodBucketColumn(undefined);
        setDimensions([]);
        setMeasures([]);
        setUpstreamIds([]);
      }
    } catch {
      message.error('DWS 设计合同读取失败；未修改当前表结构，请检查项目权限后重试');
    } finally {
      setLoading(false);
    }
  }, [modelId]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectedCandidates = useMemo(
    () => candidates.filter((item) => upstreamIds.includes(item.modelId)),
    [candidates, upstreamIds],
  );
  const savedUpstreamIds = saved?.design.upstreamVersions.map((item) => item.modelId) ?? [];
  const upstreamSelectionChanged = Boolean(saved)
    && !sameNumbers(savedUpstreamIds, upstreamIds);
  const sourceVersionDrifts = useMemo(() => {
    if (!saved) return [];
    return saved.design.upstreamVersions.flatMap((pinned) => {
      const current = candidates.find((item) => item.modelId === pinned.modelId);
      if (!current || !current.usable || !current.publishedVersionNo) {
        return [{
          modelId: pinned.modelId,
          name: current?.name ?? ('模型 #' + pinned.modelId),
          pinnedVersionNo: pinned.versionNo,
          currentVersionNo: null as number | null,
          unavailable: true,
        }];
      }
      if (current.publishedVersionNo !== pinned.versionNo) {
        return [{
          modelId: pinned.modelId,
          name: current.name,
          pinnedVersionNo: pinned.versionNo,
          currentVersionNo: current.publishedVersionNo,
          unavailable: false,
        }];
      }
      return [];
    });
  }, [candidates, saved]);
  const hasSourceChange = upstreamSelectionChanged || sourceVersionDrifts.length > 0;

  const signature = JSON.stringify({
    modelId,
    structureFingerprint: structure.definition ?? null,
    dirty,
    savedRevision: saved?.revision ?? null,
    statPeriod,
    timeZone,
    businessTimeColumn,
    periodBucketColumn,
    dimensions,
    measures,
    upstreamIds: sortNumbers(upstreamIds),
    selectedPublishedVersions: selectedCandidates.map((item) => [
      item.modelId, item.publishedVersionId ?? null, item.publishedVersionNo ?? null, item.usable,
    ]),
    sourceMigrationConfirmed,
    keepFrozen,
  });

  const validationIssues = useMemo(() => {
    const issues: string[] = [];
    if (dirty || !structure.definition) {
      issues.push('物理表结构尚未保存；请先保存上方字段与标准配置，再运行设计校验');
    }
    if (!businessTimeColumn.trim()) {
      issues.push('必须填写业务时间列；列是否真实存在及其类型仍由服务端按上游发布快照复核');
    }
    if (!validTimeZone(timeZone)) {
      issues.push('统计时区不是有效的 IANA 时区');
    }
    if (!periodBucketColumn) {
      issues.push('请选择统计周期桶字段');
    } else if (!dimensions.includes(periodBucketColumn)) {
      issues.push('统计周期桶字段必须包含在分组维度中');
    }
    if (!dimensions.length) {
      issues.push('至少选择一个分组维度');
    }
    if (dimensions.some((name) => !dimensionColumns.some((field) => field.columnName === name))) {
      issues.push('分组维度必须来自当前结构中已绑定标准字段的 DIMENSION 列');
    }
    if (!measures.length) {
      issues.push('至少选择一个聚合度量');
    }
    if (measures.some((name) => !measureColumns.some((field) =>
      field.columnName === name && Boolean(field.aggregateFunc)
      && ALLOWED_AGGREGATES.has(field.aggregateFunc as string)))) {
      issues.push('所有度量必须来自已绑定标准字段且具有白名单聚合函数的 MEASURE 列');
    }
    if (!upstreamIds.length) {
      issues.push('至少选择一个已发布的同过程 DWD 上游版本');
    }
    if (selectedCandidates.length !== upstreamIds.length
      || selectedCandidates.some((item) => !item.usable || !item.publishedVersionId
        || !item.publishedVersionNo)) {
      issues.push('所选上游中存在未发布、不可用或已变化的来源；请重新读取候选版本');
    }
    if (saved && saved.structureFingerprint !== structure.definition) {
      issues.push('已保存粒度合同对应的物理结构指纹已变化，必须重新确认合同');
    }
    if (hasSourceChange && !sourceMigrationConfirmed) {
      issues.push(keepFrozen
        ? '当前选择保留旧冻结合同；由于上游版本已漂移，不能将其标记为可发布'
        : '检测到来源选择或发布版本变化；请明确迁移到当前版本并重新校验，或保留冻结合同');
    }
    return issues;
  }, [
    dirty, structure.definition, businessTimeColumn, timeZone, periodBucketColumn, dimensions,
    dimensionColumns, measures, measureColumns, upstreamIds, selectedCandidates, saved,
    hasSourceChange, sourceMigrationConfirmed, keepFrozen,
  ]);

  const validationStatus = !validation
    ? 'NOT_RUN'
    : validation.signature !== signature
      ? 'STALE'
      : validation.status;
  const validationIsCurrentPass = validationStatus === 'PASS';
  const canSave = validationIsCurrentPass && !dirty && Boolean(structure.definition)
    && !keepFrozen && (!hasSourceChange || sourceMigrationConfirmed);

  const validateDesign = () => {
    const issues = validationIssues;
    setValidation({
      signature,
      status: issues.length ? 'BLOCKED' : 'PASS',
      issues,
      checkedAt: new Date().toLocaleString(),
    });
    if (issues.length) {
      message.warning('设计校验发现 ' + issues.length + ' 项待处理问题；已定位到右侧清单');
    } else {
      message.success('静态设计校验通过。该结果不代表真实数据已预演或落仓');
    }
  };

  const restoreFrozenDesign = () => {
    if (!saved) return;
    setStatPeriod(saved.design.statPeriod);
    setTimeZone(saved.design.timeZone);
    setBusinessTimeColumn(saved.design.businessTimeColumn);
    setPeriodBucketColumn(saved.design.periodBucketColumn);
    setDimensions(saved.design.dimensions);
    setMeasures(saved.design.measures.map((item) => item.column));
    setUpstreamIds(saved.design.upstreamVersions.map((item) => item.modelId));
    setSourceMigrationConfirmed(false);
    setKeepFrozen(true);
    setValidation(null);
    message.info('已恢复保存时的冻结合同。当前上游已变化，后端仍会阻止按旧指针发布；未执行迁移或数据查询');
  };

  const confirmMigration = () => {
    if (selectedCandidates.length !== upstreamIds.length
      || selectedCandidates.some((item) => !item.usable || !item.publishedVersionNo)) {
      message.warning('当前选择的来源不可用，不能确认迁移');
      return;
    }
    setKeepFrozen(false);
    setSourceMigrationConfirmed(true);
    setValidation(null);
    message.info('已显式确认使用当前发布来源；保存时服务端将重新冻结精确版本，需重新运行设计校验');
  };

  const store = async () => {
    if (!canSave) {
      message.warning('请先完成当前修订的静态设计校验；修订、来源或物理结构变化后必须重新校验');
      return;
    }
    const payload: SaveDwsGrainRequest = {
      statPeriod,
      timeZone: timeZone.trim(),
      businessTimeColumn: businessTimeColumn.trim(),
      periodBucketColumn: periodBucketColumn as string,
      dimensions,
      measures: measures.map((column) => ({
        column,
        function: measureColumns.find((row) => row.columnName === column)?.aggregateFunc ?? '',
      })),
      upstreamModelIds: upstreamIds,
      expectedStructureFingerprint: structure.definition as string,
    };
    setSaving(true);
    try {
      const recorded = await saveDwsGrain(modelId, payload);
      setSaved(recorded);
      setSourceMigrationConfirmed(false);
      setKeepFrozen(false);
      setValidation(null);
      message.success('DWS 设计合同已保存为修订 ' + recorded.revision + '；发布前仍需后端重新检查来源与结构');
    } catch (error) {
      // A network timeout may hide a successful write. Read back the same object before retrying.
      try {
        const recorded = await getDwsGrain(modelId);
        if (recorded) setSaved(recorded);
        if (recorded && sameSavedDesign(recorded, payload)) {
          setSourceMigrationConfirmed(false);
          setKeepFrozen(false);
          setValidation(null);
          message.success('保存响应未确认，但已回读同一模型并核对合同内容；当前修订为 ' + recorded.revision);
          return;
        }
        message.warning('保存结果未知：回读未能确认本次合同已写入。请检查当前记录，不要重复创建模型');
      } catch {
        message.error(error instanceof Error ? error.message : '保存结果未知，回读也未成功；请先重新读取，不要重复创建');
      }
    } finally {
      setSaving(false);
    }
  };

  const dimensionOptions = dimensionColumns.map((column) => ({
    label: column.columnName as string, value: column.columnName as string,
  }));
  const measureOptions = measureColumns.map((column) => ({
    label: (column.columnName as string) + ' · ' + column.aggregateFunc,
    value: column.columnName as string,
  }));
  const currentStatusColor = validationStatus === 'PASS' ? 'success'
    : validationStatus === 'BLOCKED' ? 'error'
      : validationStatus === 'STALE' ? 'warning' : 'default';

  return (
    <Card
      bordered={false}
      className="dws-grain-workbench-card"
      style={{ borderRadius: 16, boxShadow: '0 2px 12px rgba(16, 24, 40, 0.04)' }}
      title={
        <Space size={12} align="start">
          <span style={{
            width: 40, height: 40, display: 'inline-flex', alignItems: 'center',
            justifyContent: 'center', borderRadius: 12, color: '#175CD3',
            background: '#EFF8FF', fontSize: 18,
          }}>
            <DatabaseOutlined />
          </span>
          <span>
            <Title level={4} style={{ margin: 0 }}>DWS 汇总设计工作台</Title>
            <Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>
              维护粒度合同、校验设计约束并回读精确上游版本
            </Text>
          </span>
        </Space>
      }
      extra={
        <Space wrap>
          {saved
            ? <Tag color="blue">设计修订 R{saved.revision}</Tag>
            : <Tag>尚未保存</Tag>}
          <Tooltip title="重新读取服务端已保存合同与当前上游候选">
            <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>
              重新读取
            </Button>
          </Tooltip>
        </Space>
      }
    >
      <div style={{ display: 'grid', gap: 20 }}>
        <Steps
          size="small"
          responsive
          current={2}
          items={[
            { title: '来源版本', description: '同过程 DWD' },
            { title: '统计粒度', description: '时间与维度' },
            { title: '度量验证', description: '口径与约束' },
            { title: '审核证据', description: '设计状态' },
          ]}
        />
        <Alert
          showIcon
          type="info"
          message="这里确认的是 DWS 设计合同，不是实际加工结果"
          description="保存只记录统计周期、时区、分组维度、度量与精确上游版本。当前接口没有真实数据预演能力；本面板的静态校验只检查可见设计约束，服务端仍是保存与发布的最终校验者。"
        />

        <Row gutter={[16, 16]} align="stretch">
          <Col xs={24} xl={15}>
            <div style={{ display: 'grid', gap: 16 }}>
              <Card
                size="small"
                title={<Space><DatabaseOutlined /><span>01 来源与版本</span></Space>}
                extra={<Tag color={selectedCandidates.length ? 'blue' : 'default'}>
                  已选 {selectedCandidates.length} 个
                </Tag>}
              >
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Text type="secondary">
                    只允许同业务过程的已发布 DWD。保存后固定具体版本与摘要；不会自动切换到新版本。
                  </Text>
                  <Select
                    aria-label="已发布的同过程 DWD 上游版本"
                    className="w-full"
                    mode="multiple"
                    value={upstreamIds}
                    onChange={(values) => {
                      setUpstreamIds(values);
                      setSourceMigrationConfirmed(false);
                      setKeepFrozen(false);
                    }}
                    options={candidates.map((item) => ({
                      value: item.modelId,
                      label: item.name + '（' + item.code + '） · '
                        + (item.usable ? 'V' + item.publishedVersionNo : '未发布/不可用'),
                      disabled: !item.usable,
                    }))}
                    placeholder="选择已发布的同过程 DWD"
                    optionFilterProp="label"
                    loading={loading}
                  />
                  {selectedCandidates.length ? (
                    <Row gutter={[8, 8]}>
                      {selectedCandidates.map((item) => (
                        <Col xs={24} md={12} key={item.modelId}>
                          <div style={{
                            border: '1px solid #D1E9FF', background: '#F5FAFF',
                            borderRadius: 10, padding: 12, minHeight: 74,
                          }}>
                            <Space direction="vertical" size={4}>
                              <Text strong>{item.name}</Text>
                              <Space wrap size={6}>
                                <Tag color="blue">{item.code}</Tag>
                                <Tag color={item.usable ? 'success' : 'error'}>
                                  {item.usable ? '当前发布 V' + item.publishedVersionNo : '不可用'}
                                </Tag>
                              </Space>
                            </Space>
                          </div>
                        </Col>
                      ))}
                    </Row>
                  ) : (
                    <Alert type="warning" showIcon message="尚未选择可用的已发布 DWD 来源" />
                  )}
                  {hasSourceChange && !sourceMigrationConfirmed ? (
                    <Alert
                      type="warning"
                      showIcon
                      icon={<WarningOutlined />}
                      message="检测到上游来源或发布版本变化"
                      description={
                        <Space direction="vertical" size={8}>
                          {sourceVersionDrifts.map((item) => (
                            <Text key={item.modelId}>
                              {item.name}：冻结 V{item.pinnedVersionNo} → {
                                item.currentVersionNo ? '当前 V' + item.currentVersionNo : '当前不可用'
                              }
                            </Text>
                          ))}
                          {upstreamSelectionChanged
                            ? <Text>当前选中来源与保存合同中的来源集合不同。</Text>
                            : null}
                          <Text type="secondary">
                            选择迁移会在保存时冻结当前发布版本；保留冻结版不会改写合同，但旧指针可能无法通过正式发布门禁。
                          </Text>
                          <Space wrap>
                            <Button
                              type="primary"
                              size="small"
                              disabled={selectedCandidates.length !== upstreamIds.length
                                || selectedCandidates.some((item) => !item.usable || !item.publishedVersionNo)}
                              onClick={confirmMigration}
                            >
                              确认迁移到当前发布版本
                            </Button>
                            <Button size="small" onClick={restoreFrozenDesign}>
                              保留冻结合同
                            </Button>
                          </Space>
                        </Space>
                      }
                    />
                  ) : null}
                  {keepFrozen ? (
                    <Alert
                      type="warning"
                      showIcon
                      message="已保留冻结合同：当前修订只读"
                      description="本面板不会把旧 Vn 静默改成新 Vn。若要继续发布，必须由负责人解决上游漂移或显式迁移并重新校验。"
                    />
                  ) : null}
                </Space>
              </Card>

              <Card
                size="small"
                title={<Space><ClockCircleOutlined /><span>02 统计粒度</span></Space>}
                extra={<Tag>一行 = 一个统计周期 × 所选维度</Tag>}
              >
                <Row gutter={[16, 16]}>
                  <Col xs={24} md={12}>
                    <label style={{ display: 'block', marginBottom: 6 }}>统计周期</label>
                    <Select
                      aria-label="统计周期"
                      style={{ width: '100%' }}
                      value={statPeriod}
                      onChange={setStatPeriod}
                      options={[
                        { value: '1h', label: '每小时' },
                        { value: '1d', label: '每天' },
                        { value: '1w', label: '每周' },
                        { value: '1m', label: '每月' },
                        { value: 'ALL', label: '全量' },
                      ]}
                    />
                  </Col>
                  <Col xs={24} md={12}>
                    <label style={{ display: 'block', marginBottom: 6 }}>统计时区（IANA）</label>
                    <Input
                      aria-label="统计时区（IANA）"
                      value={timeZone}
                      onChange={(event) => setTimeZone(event.target.value)}
                      placeholder="Asia/Shanghai"
                    />
                  </Col>
                  <Col xs={24} md={12}>
                    <label style={{ display: 'block', marginBottom: 6 }}>上游业务时间列</label>
                    <Input
                      aria-label="上游业务时间列"
                      value={businessTimeColumn}
                      onChange={(event) => setBusinessTimeColumn(event.target.value)}
                      placeholder="例如 event_time"
                    />
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      真实列存在性与日期时间类型由服务端按上游冻结快照复核。
                    </Text>
                  </Col>
                  <Col xs={24} md={12}>
                    <label style={{ display: 'block', marginBottom: 6 }}>统计周期桶字段</label>
                    <Select
                      aria-label="统计周期桶字段"
                      allowClear
                      style={{ width: '100%' }}
                      value={periodBucketColumn}
                      onChange={setPeriodBucketColumn}
                      options={dimensionOptions}
                      placeholder="必须属于分组维度"
                    />
                  </Col>
                  <Col xs={24}>
                    <label style={{ display: 'block', marginBottom: 6 }}>分组维度（需绑定标准字段）</label>
                    <Select
                      aria-label="分组维度"
                      className="w-full"
                      mode="multiple"
                      value={dimensions}
                      onChange={setDimensions}
                      options={dimensionOptions}
                      placeholder="例如 order_day、order_city"
                    />
                  </Col>
                  <Col xs={24}>
                    <label style={{ display: 'block', marginBottom: 6 }}>聚合度量（函数来自当前表结构）</label>
                    <Select
                      aria-label="聚合度量"
                      className="w-full"
                      mode="multiple"
                      value={measures}
                      onChange={setMeasures}
                      options={measureOptions}
                      placeholder="至少一项 COUNT / SUM / COUNT DISTINCT 等度量"
                    />
                  </Col>
                </Row>
                <Divider style={{ margin: '18px 0 12px' }} />
                <div style={{
                  borderRadius: 10, padding: 12, background: '#F8FAFC',
                  display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8,
                }}>
                  <Text type="secondary">当前粒度预览：</Text>
                  <Tag color="blue">{statPeriod === 'ALL' ? '全量' : statPeriod}</Tag>
                  <Tag>{periodBucketColumn || '未选择周期桶'}</Tag>
                  {dimensions.filter((name) => name !== periodBucketColumn).map((name) => (
                    <Tag key={name}>{name}</Tag>
                  ))}
                  <Text type="secondary">→ 每个分组输出一行</Text>
                </div>
              </Card>

              <Card
                size="small"
                title={<Space><ExperimentOutlined /><span>03 度量与计算验证</span></Space>}
                extra={<Tag color="processing">V0 静态设计</Tag>}
              >
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {measures.length ? (
                    <Table
                      size="small"
                      pagination={false}
                      rowKey="column"
                      dataSource={measures.map((name) => {
                        const field = measureColumns.find((item) => item.columnName === name);
                        return {
                          column: name,
                          function: field?.aggregateFunc ?? '未配置',
                          standard: field?.stdFieldId ? '已绑定标准字段' : '缺少标准字段',
                        };
                      })}
                      columns={[
                        { title: '输出度量', dataIndex: 'column', key: 'column' },
                        { title: '聚合函数', dataIndex: 'function', key: 'function',
                          render: (value: string) => <Tag color="geekblue">{value}</Tag> },
                        { title: '标准状态', dataIndex: 'standard', key: 'standard' },
                      ]}
                    />
                  ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="先在上方物理结构中配置 MEASURE 角色、标准字段与聚合函数" />
                  )}
                  <Alert
                    showIcon
                    type="info"
                    message="静态设计检查不等于真实数据计算"
                    description="本轮已有接口只保存和回读粒度合同。它不执行 SQL，也不读取生产数据。请先运行下方设计校验；真实只读样本预演需要后续接入有权限、限流和审计的查询执行服务。"
                  />
                  <Space wrap>
                    <Button
                      icon={<FileSearchOutlined />}
                      type="primary"
                      onClick={validateDesign}
                    >
                      运行设计校验
                    </Button>
                    <Button
                      icon={<ExperimentOutlined />}
                      onClick={() => setSampleExampleOpen((value) => !value)}
                    >
                      {sampleExampleOpen ? '收起合成样本示例' : '查看口径示例（合成数据）'}
                    </Button>
                  </Space>
                  {sampleExampleOpen ? (
                    <Card
                      size="small"
                      style={{ background: '#FCFCFD', borderColor: '#EAECF0' }}
                      title={<Space><ExperimentOutlined /><span>订单去重示例 · 非真实查询</span></Space>}
                      extra={<Tag color="warning">固定合成数据</Tag>}
                    >
                      <Alert
                        type="warning"
                        showIcon
                        message="以下只解释业务口径陷阱，不验证当前模型，也不连接任何数据源"
                        description="示例假定同一统计日与城市：O001 有两条明细，O002 有一条。订单数与明细行数不是同一个指标。"
                      />
                      <Table
                        style={{ marginTop: 12 }}
                        size="small"
                        pagination={false}
                        rowKey="lineId"
                        dataSource={[
                          { lineId: 'O001-01', orderId: 'O001', amount: 100 },
                          { lineId: 'O001-02', orderId: 'O001', amount: 200 },
                          { lineId: 'O002-01', orderId: 'O002', amount: 150 },
                        ]}
                        columns={[
                          { title: '明细行', dataIndex: 'lineId', key: 'lineId' },
                          { title: '订单 ID', dataIndex: 'orderId', key: 'orderId' },
                          { title: '行金额示例', dataIndex: 'amount', key: 'amount', align: 'right' },
                        ]}
                      />
                      <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
                        <Col xs={24} sm={8}>
                          <Card size="small"><Statistic title="明细行数 COUNT(*)" value={3} /></Card>
                        </Col>
                        <Col xs={24} sm={8}>
                          <Card size="small"><Statistic title="订单数 DISTINCT" value={2} /></Card>
                        </Col>
                        <Col xs={24} sm={8}>
                          <Card size="small"><Statistic title="行金额合计" value={450} /></Card>
                        </Col>
                      </Row>
                      <Text type="secondary" style={{ display: 'block', marginTop: 10 }}>
                        450 只是明细行金额示例和，不代表实际支付净额。真实验证还需确认来源快照、过滤范围、退款/折扣口径与执行权限。
                      </Text>
                    </Card>
                  ) : null}
                </Space>
              </Card>
            </div>
          </Col>

          <Col xs={24} xl={9}>
            <div style={{ display: 'grid', gap: 16 }}>
              <Card
                size="small"
                title={<Space><SafetyCertificateOutlined /><span>设计校验结果</span></Space>}
                extra={
                  <Badge
                    status={validationStatus === 'PASS' ? 'success'
                      : validationStatus === 'BLOCKED' ? 'error'
                        : validationStatus === 'STALE' ? 'warning' : 'default'}
                    text={
                      validationStatus === 'PASS' ? '通过'
                        : validationStatus === 'BLOCKED' ? '存在阻断'
                          : validationStatus === 'STALE' ? '结果已过期' : '未执行'
                    }
                  />
                }
              >
                {validationStatus === 'NOT_RUN' ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="尚未运行静态设计校验" />
                ) : null}
                {validationStatus === 'STALE' ? (
                  <Alert
                    type="warning"
                    showIcon
                    message="校验结果已过期"
                    description="设计字段、来源版本或草稿修订已变化。请重新运行设计校验；旧结果不能用于保存门禁。"
                  />
                ) : null}
                {validationStatus === 'PASS' ? (
                  <Alert
                    type="success"
                    showIcon
                    message="当前设计的静态约束通过"
                    description="只证明当前可见设计满足本地结构规则，不证明业务指标正确，也不代表真实数据已计算。"
                  />
                ) : null}
                {validationStatus === 'BLOCKED' ? (
                  <Alert type="error" showIcon message="请先处理以下阻断项" />
                ) : null}
                {validationStatus === 'BLOCKED' || validationStatus === 'PASS' ? (
                  <div style={{ display: 'grid', gap: 8, marginTop: 12 }}>
                    {(validation?.issues ?? []).length
                      ? validation?.issues.map((issue) => (
                        <div key={issue} style={{
                          display: 'flex', alignItems: 'flex-start', gap: 8,
                          padding: '8px 10px', borderRadius: 8, background: '#FEF3F2',
                        }}>
                          <WarningOutlined style={{ color: '#B42318', marginTop: 3 }} />
                          <Text>{issue}</Text>
                        </div>
                      ))
                      : [
                        '物理结构已保存并有稳定指纹',
                        '业务时间列、时区与统计周期已填写',
                        '周期桶属于分组维度',
                        '所选维度/度量具有标准字段与有效角色',
                        '上游来源已显式选择且无未确认版本漂移',
                      ].map((item) => (
                        <div key={item} style={{
                          display: 'flex', alignItems: 'flex-start', gap: 8,
                          padding: '8px 10px', borderRadius: 8, background: '#ECFDF3',
                        }}>
                          <CheckCircleOutlined style={{ color: '#039855', marginTop: 3 }} />
                          <Text>{item}</Text>
                        </div>
                      ))}
                  </div>
                ) : null}
                {validation?.checkedAt && validationStatus !== 'STALE' ? (
                  <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
                    最近校验：{validation.checkedAt}
                  </Text>
                ) : null}
              </Card>

              <Card size="small" title={<Space><FileSearchOutlined /><span>验证证据状态</span></Space>}>
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <Space><CheckCircleOutlined style={{ color: '#039855' }} /><span>V0 静态设计</span></Space>
                    <Tag color={validationStatus === 'PASS' ? 'success'
                      : validationStatus === 'BLOCKED' ? 'error'
                        : validationStatus === 'STALE' ? 'warning' : 'default'}>
                      {validationStatus}
                    </Tag>
                  </div>
                  <Divider style={{ margin: 0 }} />
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <Space><ExperimentOutlined /><span>V1 真实只读样本预演</span></Space>
                    <Tag>NOT_CONNECTED</Tag>
                  </div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    当前没有真实查询服务合同；合成示例不能改写此状态。
                  </Text>
                  <Divider style={{ margin: 0 }} />
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <Space><ClockCircleOutlined /><span>V2 运行后数据对账</span></Space>
                    <Tag>NOT_EXECUTED</Tag>
                  </div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    必须来自真实 DataDev/Runtime 批次与目标数据证据；模型设计发布不会自动通过。
                  </Text>
                </Space>
              </Card>

              <Card size="small" title="冻结合同摘要">
                {saved ? (
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="设计修订">R{saved.revision}</Descriptions.Item>
                    <Descriptions.Item label="结构指纹">
                      <Tooltip title={saved.structureFingerprint}>
                        <Text code>{saved.structureFingerprint.slice(0, 12)}…</Text>
                      </Tooltip>
                    </Descriptions.Item>
                    <Descriptions.Item label="合同 SHA-256">
                      <Tooltip title={saved.designSha256}>
                        <Text code>{saved.designSha256.slice(0, 12)}…</Text>
                      </Tooltip>
                    </Descriptions.Item>
                    <Descriptions.Item label="冻结上游">
                      {saved.design.upstreamVersions.map((item) =>
                        '模型 #' + item.modelId + ' V' + item.versionNo).join('、') || '无'}
                    </Descriptions.Item>
                  </Descriptions>
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无已保存合同" />
                )}
              </Card>
            </div>
          </Col>
        </Row>

        <Divider style={{ margin: '0' }} />
        <Row align="middle" justify="space-between" gutter={[12, 12]}>
          <Col xs={24} md={15}>
            <Space wrap>
              <Tag color={dirty ? 'warning' : 'success'}>
                {dirty ? '物理结构有未保存修改' : '物理结构已保存'}
              </Tag>
              <Tag color={validationStatus === 'PASS' ? 'success' : 'default'}>
                {validationStatus === 'PASS' ? '设计校验通过' : '需完成当前修订校验'}
              </Tag>
              {keepFrozen ? <Tag color="warning">保留旧冻结合同</Tag> : null}
            </Space>
            <Text type="secondary" style={{ display: 'block', marginTop: 6, fontSize: 12 }}>
              保存此合同不会运行 SQL、创建 DataDev 任务、发布指标或授权 Dataset/Service。
            </Text>
          </Col>
          <Col xs={24} md={9}>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }} wrap>
              <Button onClick={() => void load()} disabled={saving} icon={<ReloadOutlined />}>
                放弃本地修改并回读
              </Button>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving}
                disabled={!canSave}
                onClick={() => void store()}
              >
                保存 DWS 设计合同
              </Button>
            </Space>
          </Col>
        </Row>
      </div>
    </Card>
  );
}
