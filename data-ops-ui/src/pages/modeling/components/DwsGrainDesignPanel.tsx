import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Steps,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { HistoryOutlined, ReloadOutlined, ExperimentOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getModelingVersion } from '@/services/modeling/api';
import type { ModelingStructureRecord } from '@/services/modeling/types';
import {
  getDwsGrain,
  getDwsGrainHistory,
  listDwsGrainCandidates,
  restoreDwsGrainRevision,
  saveDwsGrain,
  validateDwsGrain,
} from '@/services/modeling/dwsGrain';
import type {
  DwsGrainCandidate,
  DwsGrainHistoryEntry,
  DwsGrainSaved,
  DwsGrainValidationResult,
  SaveDwsGrainRequest,
} from '@/services/modeling/dwsGrain';

const WORKFLOW_STEPS = [
  { title: '设计对象', description: '业务用途' },
  { title: '可信来源', description: '精确版本' },
  { title: '统计粒度', description: '时间与分组' },
  { title: '度量与验证', description: '计算口径' },
  { title: '审核发布', description: '设计合同' },
  { title: '版本恢复', description: '历史回读' },
];

type StaticValidation = {
  signature: string;
  result: DwsGrainValidationResult;
};

type SampleValidation = {
  signature: string;
  status: 'SAMPLE_MATCH' | 'SAMPLE_WARNING' | 'UNAVAILABLE';
  reason: string;
  checkedAt: string;
};

interface Props {
  modelId: number;
  structure: ModelingStructureRecord;
  dirty: boolean;
  modelName?: string;
  modelCode?: string;
  processName?: string;
  latestVersionNo?: number;
  canEdit?: boolean;
  onRequestPublish?: () => void;
}

const isDateTimeType = (value?: string | null) =>
  Boolean(value && /(DATE|TIME|TIMESTAMP)/i.test(value));

const displayTime = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 19) : '—';

export default function DwsGrainDesignPanel({
  modelId,
  structure,
  dirty,
  modelName,
  modelCode,
  processName,
  latestVersionNo = 0,
  canEdit = true,
  onRequestPublish,
}: Props) {
  const [saved, setSaved] = useState<DwsGrainSaved | null>(null);
  const [history, setHistory] = useState<DwsGrainHistoryEntry[]>([]);
  const [candidates, setCandidates] = useState<DwsGrainCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [restoringRevision, setRestoringRevision] = useState<number>();
  const [sourceLoading, setSourceLoading] = useState(false);
  const [sourceLoadError, setSourceLoadError] = useState<string>();
  const [sourceStructure, setSourceStructure] = useState<ModelingStructureRecord>();
  const [step, setStep] = useState(0);
  const [contractDirty, setContractDirty] = useState(false);
  const [useLatestSource, setUseLatestSource] = useState(false);
  const [statPeriod, setStatPeriod] = useState('1d');
  const [timeZone, setTimeZone] = useState('Asia/Shanghai');
  const [businessTimeColumn, setBusinessTimeColumn] = useState('');
  const [periodBucketColumn, setPeriodBucketColumn] = useState<string>();
  const [dimensions, setDimensions] = useState<string[]>([]);
  const [measures, setMeasures] = useState<string[]>([]);
  const [upstreamIds, setUpstreamIds] = useState<number[]>([]);
  const [staticValidation, setStaticValidation] = useState<StaticValidation>();
  const [sampleValidation, setSampleValidation] = useState<SampleValidation>();

  const columns = structure.columns ?? [];
  const dimensionColumns = useMemo(() => columns.filter(
    (field) => field.columnName && field.fieldRole === 'DIMENSION' && field.stdFieldId,
  ), [columns]);
  const measureColumns = useMemo(() => columns.filter(
    (field) => field.columnName && field.fieldRole === 'MEASURE'
      && field.stdFieldId && field.aggregateFunc,
  ), [columns]);
  const dimensionOptions = useMemo(() => dimensionColumns.map((column) => ({
    label: String(column.columnName), value: String(column.columnName),
  })), [dimensionColumns]);
  const measureOptions = useMemo(() => measureColumns.map((column) => ({
    label: String(column.columnName) + ' · ' + String(column.aggregateFunc),
    value: String(column.columnName),
  })), [measureColumns]);
  const selectedUpstreamId = upstreamIds[0];
  const selectedCandidate = useMemo(
    () => candidates.find((item) => item.modelId === selectedUpstreamId),
    [candidates, selectedUpstreamId],
  );
  const pinnedUpstream = saved?.design.upstreamVersions.find(
    (item) => item.modelId === selectedUpstreamId,
  );
  const sourceDrifted = Boolean(
    pinnedUpstream
      && selectedCandidate?.publishedVersionNo
      && pinnedUpstream.versionNo !== selectedCandidate.publishedVersionNo,
  );
  const selectedSourceVersionNo = selectedCandidate
    ? (useLatestSource
      ? selectedCandidate.publishedVersionNo
      : pinnedUpstream?.versionNo ?? selectedCandidate.publishedVersionNo)
    : undefined;
  const sourceColumns = sourceStructure?.columns ?? [];
  const businessTimeOptions = useMemo(() => sourceColumns
    .filter((column) => column.columnName && isDateTimeType(column.dataType))
    .map((column) => ({
      label: String(column.columnName) + ' · ' + String(column.dataType),
      value: String(column.columnName),
    })), [sourceColumns]);

  const signature = useMemo(() => JSON.stringify({
    modelId,
    structure: structure.definition,
    structureDirty: dirty,
    contractDirty,
    statPeriod,
    timeZone,
    businessTimeColumn,
    periodBucketColumn,
    dimensions,
    measures,
    upstreamIds,
    selectedSourceVersionNo,
    useLatestSource,
    savedRevision: saved?.revision ?? 0,
  }), [
    modelId, structure.definition, dirty, contractDirty, statPeriod, timeZone,
    businessTimeColumn, periodBucketColumn, dimensions, measures, upstreamIds,
    selectedSourceVersionNo, useLatestSource, saved?.revision,
  ]);

  const staticStatus = !staticValidation
    ? 'NOT_RUN'
    : staticValidation.signature !== signature
      ? 'STALE'
      : staticValidation.result.valid ? 'STATIC_PASS' : 'STATIC_BLOCKED';
  const sampleStatus = !sampleValidation
    ? 'NOT_RUN'
    : sampleValidation.signature !== signature ? 'STALE' : sampleValidation.status;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [stored, sources, revisions] = await Promise.all([
        getDwsGrain(modelId),
        listDwsGrainCandidates(modelId),
        getDwsGrainHistory(modelId),
      ]);
      setSaved(stored);
      setCandidates(sources ?? []);
      setHistory(revisions ?? []);
      setUseLatestSource(false);
      setContractDirty(false);
      setStaticValidation(undefined);
      setSampleValidation(undefined);
      if (stored) {
        setStatPeriod(stored.design.statPeriod);
        setTimeZone(stored.design.timeZone);
        setBusinessTimeColumn(stored.design.businessTimeColumn);
        setPeriodBucketColumn(stored.design.periodBucketColumn);
        setDimensions(stored.design.dimensions);
        setMeasures(stored.design.measures.map((item) => item.column));
        setUpstreamIds(stored.design.upstreamVersions.map((item) => item.modelId).slice(0, 1));
        setStep(3);
      } else {
        setStatPeriod('1d');
        setTimeZone('Asia/Shanghai');
        setBusinessTimeColumn('');
        setPeriodBucketColumn(undefined);
        setDimensions([]);
        setMeasures([]);
        setUpstreamIds([]);
        setStep(0);
      }
    } catch (error) {
      message.error(error instanceof Error
        ? error.message : 'DWS 设计合同或历史修订读取失败，请核对项目权限');
    } finally {
      setLoading(false);
    }
  }, [modelId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let active = true;
    if (!selectedCandidate?.usable || !selectedCandidate.modelId || !selectedSourceVersionNo) {
      setSourceStructure(undefined);
      setSourceLoadError(undefined);
      setSourceLoading(false);
      return () => { active = false; };
    }
    setSourceLoading(true);
    setSourceLoadError(undefined);
    getModelingVersion(selectedCandidate.modelId, selectedSourceVersionNo)
      .then((version) => {
        if (active) setSourceStructure(version.structure);
      })
      .catch((error) => {
        if (!active) return;
        setSourceStructure(undefined);
        setSourceLoadError(error instanceof Error
          ? error.message : '无法读取该 DWD 正式版本结构');
      })
      .finally(() => {
        if (active) setSourceLoading(false);
      });
    return () => { active = false; };
  }, [selectedCandidate?.modelId, selectedCandidate?.usable, selectedSourceVersionNo]);

  const buildRequest = useCallback((): SaveDwsGrainRequest => ({
    statPeriod,
    timeZone: timeZone.trim(),
    businessTimeColumn: businessTimeColumn.trim(),
    periodBucketColumn: periodBucketColumn ?? '',
    dimensions,
    measures: measures.map((column) => ({
      column,
      function: String(measureColumns.find((item) => item.columnName === column)?.aggregateFunc ?? ''),
    })),
    upstreamModelIds: upstreamIds,
    expectedStructureFingerprint: structure.definition ?? '',
  }), [
    statPeriod, timeZone, businessTimeColumn, periodBucketColumn, dimensions,
    measures, measureColumns, upstreamIds, structure.definition,
  ]);

  const localBlockers = useMemo(() => {
    const issues: string[] = [];
    if (dirty) issues.push('当前物理表结构有未保存修改，请先保存结构');
    if (!saved) issues.push('尚未保存 DWS 粒度合同');
    if (!structure.definition) issues.push('缺少当前物理结构指纹，无法验证合同');
    if (!selectedCandidate) issues.push('请选择同业务过程的 DWD 正式来源');
    else if (!selectedCandidate.usable) issues.push('所选 DWD 来源没有可用正式版本');
    if (!businessTimeColumn.trim()) issues.push('请选择来源真实存在的业务时间字段');
    if (!periodBucketColumn) issues.push('请选择统计周期桶字段');
    if (!dimensions.length) issues.push('至少选择一个分组维度');
    if (periodBucketColumn && !dimensions.includes(periodBucketColumn)) {
      issues.push('统计周期桶必须属于分组维度');
    }
    if (!measures.length) issues.push('至少选择一个已配置聚合函数的标准度量');
    if (sourceLoading) issues.push('正在读取冻结的 DWD 来源结构');
    if (sourceLoadError) issues.push('DWD 来源结构读取失败：' + sourceLoadError);
    if (businessTimeColumn && sourceStructure
        && !sourceColumns.some((column) =>
          String(column.columnName).toLowerCase() === businessTimeColumn.toLowerCase()
          && isDateTimeType(column.dataType))) {
      issues.push('业务时间字段不存在于所选 DWD 精确版本，或不是日期时间类型');
    }
    if (saved && saved.structureFingerprint !== structure.definition) {
      issues.push('DWS 物理结构与已保存合同指纹不同，请重新核对并保存');
    }
    if (sourceDrifted && !useLatestSource) {
      issues.push('DWD 当前正式版本已变化。可保留旧草稿查看，但发布前必须明确采用当前正式版本并重新审核');
    }
    if (contractDirty) issues.push('粒度合同有未保存修改');
    return issues;
  }, [
    dirty, structure.definition, selectedCandidate, businessTimeColumn, periodBucketColumn,
    dimensions, measures, sourceLoading, sourceLoadError, sourceStructure, sourceColumns,
    saved, sourceDrifted, useLatestSource, contractDirty,
  ]);

  const saveContract = async () => {
    if (!canEdit) {
      message.warning('当前只有查看权限，不能保存 DWS 粒度合同');
      return;
    }
    if (dirty || !structure.definition) {
      message.warning('请先保存当前物理表结构，再保存 DWS 粒度合同');
      return;
    }
    if (sourceDrifted && !useLatestSource) {
      message.warning('上游正式版本已变化，请先明确采用当前正式版本并重新审阅');
      return;
    }
    if (!selectedCandidate?.usable || !businessTimeColumn.trim() || !periodBucketColumn
        || !dimensions.length || !measures.length || !upstreamIds.length) {
      message.warning('请补全可信来源、业务时间、周期桶、分组维度与度量');
      return;
    }
    setSaving(true);
    try {
      const recorded = await saveDwsGrain(modelId, buildRequest());
      setSaved(recorded);
      setContractDirty(false);
      setUseLatestSource(false);
      const revisions = await getDwsGrainHistory(modelId);
      setHistory(revisions ?? []);
      message.success('DWS 粒度合同已保存为修订 R' + recorded.revision + '；尚未执行 SQL 或真实数据验证');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'DWS 粒度合同保存失败，请重新读取核对');
    } finally {
      setSaving(false);
    }
  };

  const validateContract = async () => {
    if (dirty || !structure.definition) {
      setStaticValidation({
        signature,
        result: {
          valid: false,
          blockers: ['请先保存物理表结构，再运行服务端静态合同校验'],
          structureFingerprint: structure.definition ?? '',
          design: null,
        },
      });
      return;
    }
    if (sourceDrifted && !useLatestSource) {
      setStaticValidation({
        signature,
        result: {
          valid: false,
          blockers: ['冻结来源版本与当前正式发布指针不同。明确采用当前版本后才能重新校验'],
          structureFingerprint: structure.definition ?? '',
          design: null,
        },
      });
      return;
    }
    setLoading(true);
    try {
      const result = await validateDwsGrain(modelId, buildRequest());
      setStaticValidation({ signature, result });
      if (result.valid) {
        message.success('服务端静态校验通过；这不代表真实数据已查询或加工成功');
      } else {
        message.warning('静态校验发现 ' + result.blockers.length + ' 项问题，请查看审核清单');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '服务端静态校验失败');
    } finally {
      setLoading(false);
    }
  };

  const validateSample = () => {
    let status: SampleValidation['status'] = 'SAMPLE_MATCH';
    let reason = '固定合成订单样本：3 条明细对应 2 笔订单。该演示不读取真实业务库。';
    const orderCountMeasure = measureColumns.find((column) =>
      measures.includes(String(column.columnName))
      && /(order_count|ordercount|订单数量|订单数)/i.test(String(column.columnName)));
    if (dirty || staticStatus !== 'STATIC_PASS' || !selectedCandidate?.usable
        || sourceLoading || !sourceStructure) {
      status = 'UNAVAILABLE';
      reason = '结构未保存、静态合同未通过或来源版本不可读；不展示可能误导的样本结果。';
    } else if (!orderCountMeasure) {
      status = 'UNAVAILABLE';
      reason = '当前模型没有可识别的订单数量度量；固定订单样本不适用于此模型。';
    } else if (String(orderCountMeasure.aggregateFunc).toUpperCase() !== 'COUNT_DISTINCT') {
      status = 'SAMPLE_WARNING';
      reason = '当前订单数量度量不是 COUNT_DISTINCT；固定样本中 3 条明细不能当作 3 笔订单。';
    }
    setSampleValidation({ signature, status, reason, checkedAt: new Date().toISOString() });
    if (status === 'SAMPLE_WARNING') message.warning('固定样本发现计数风险，请修复聚合函数并重新验证');
    else if (status === 'SAMPLE_MATCH') message.success('固定合成样本演示完成；未查询真实数据');
    else message.info('当前设计不适用于固定订单样本或验证前置条件不足');
  };

  const restoreRevision = async (revision: number) => {
    if (!canEdit) {
      message.warning('当前只有查看权限，不能恢复历史修订');
      return;
    }
    setRestoringRevision(revision);
    try {
      const restored = await restoreDwsGrainRevision(modelId, revision);
      setSaved(restored);
      setStatPeriod(restored.design.statPeriod);
      setTimeZone(restored.design.timeZone);
      setBusinessTimeColumn(restored.design.businessTimeColumn);
      setPeriodBucketColumn(restored.design.periodBucketColumn);
      setDimensions(restored.design.dimensions);
      setMeasures(restored.design.measures.map((item) => item.column));
      setUpstreamIds(restored.design.upstreamVersions.map((item) => item.modelId).slice(0, 1));
      setUseLatestSource(false);
      setContractDirty(false);
      setStep(3);
      setHistory(await getDwsGrainHistory(modelId));
      message.success('已将历史内容恢复为新的设计修订 R' + restored.revision + '；原历史记录保持不变');
    } catch (error) {
      message.error(error instanceof Error
        ? error.message : '历史修订无法恢复；请核对结构和上游版本');
    } finally {
      setRestoringRevision(undefined);
    }
  };

  const changeContract = (callback: () => void) => {
    callback();
    setContractDirty(true);
  };

  const sourceLabel = selectedCandidate
    ? selectedCandidate.name + ' (' + selectedCandidate.code + ') · V' + String(selectedSourceVersionNo ?? '—')
    : '未选择来源';
  const countMeasure = measureColumns.find((column) =>
    measures.includes(String(column.columnName))
    && /(order_count|ordercount|订单数量|订单数)/i.test(String(column.columnName)));

  const blockers = [
    ...localBlockers,
    ...(staticStatus === 'NOT_RUN' ? ['尚未运行服务端静态合同校验'] : []),
    ...(staticStatus === 'STALE' ? ['静态校验证据已过期，必须重新校验'] : []),
    ...(staticStatus === 'STATIC_BLOCKED' && staticValidation
      ? staticValidation.result.blockers : []),
    ...(sampleStatus === 'STALE' ? ['固定样本证据已过期，请重新运行演示或明确不使用样本证据'] : []),
    ...(sampleStatus === 'SAMPLE_WARNING' ? ['固定订单样本发现聚合计数风险，需修复后重新验证'] : []),
  ];
  const staticIsCurrent = staticStatus === 'STATIC_PASS';
  const sampleDescription = sampleStatus === 'NOT_RUN'
    ? '尚未运行固定样本演示。该演示使用硬编码的三条合成订单明细，不会访问真实业务数据。'
    : sampleStatus === 'STALE'
      ? '设计或修订发生变化，旧样本证据已失效；请重新运行演示。'
      : sampleStatus === 'UNAVAILABLE'
        ? (sampleValidation?.reason ?? '当前不适用固定样本。')
        : sampleValidation?.reason ?? '';

  const renderStep = () => {
    if (step === 0) {
      return (
        <Card title="设计对象与业务过程" extra={<Tag color="blue">DWS 汇总模型</Tag>}>
          <Descriptions column={{ xs: 1, sm: 2 }} size="small">
            <Descriptions.Item label="模型名称">{modelName || '未命名 DWS 模型'}</Descriptions.Item>
            <Descriptions.Item label="模型编码">{modelCode || '—'}</Descriptions.Item>
            <Descriptions.Item label="模型 ID">#{modelId}</Descriptions.Item>
            <Descriptions.Item label="业务过程">{processName || '以模型绑定的业务过程为准'}</Descriptions.Item>
            <Descriptions.Item label="已发布版本">V{latestVersionNo || 0}</Descriptions.Item>
            <Descriptions.Item label="草稿修订">{saved ? 'R' + saved.revision : '尚未保存'}</Descriptions.Item>
          </Descriptions>
          <Divider />
          <Alert
            showIcon
            type="info"
            message="先确定业务问题，再设计汇总粒度"
            description="DWS 一行必须代表明确的业务时间窗口与分组组合。此工作台维护 Modeling 的粒度设计合同，不代替业务指标定义，也不会执行 SQL。"
          />
          <Row gutter={[16, 16]} className="mt-4">
            <Col xs={24} md={12}>
              <Card size="small" title="当前设计基线">
                <Typography.Paragraph>
                  {saved
                    ? '已保存粒度合同 R' + saved.revision + '，摘要 ' + saved.designSha256.slice(0, 12) + '…'
                    : '尚无粒度合同。请先完成来源、时间、维度与度量定义。'}
                </Typography.Paragraph>
                <Button onClick={() => setStep(saved ? 3 : 1)}>
                  {saved ? '继续当前设计' : '选择可信来源'}
                </Button>
              </Card>
            </Col>
            <Col xs={24} md={12}>
              <Card size="small" title="目标用途">
                <Typography.Paragraph>
                  按业务日与城市汇总订单数量、金额等度量，供后续报表和受治理指标消费。实际用途应由业务过程与模型描述确认。
                </Typography.Paragraph>
                <Tag>模型设计，不代表已落仓</Tag>
              </Card>
            </Col>
          </Row>
        </Card>
      );
    }
    if (step === 1) {
      return (
        <Card title="选择可信上游" extra={<Tag>{sourceLabel}</Tag>}>
          <Alert
            showIcon
            type="info"
            message="只选择同业务过程、已发布且当前项目可见的 DWD"
            description="保存时服务端重新读取精确版本并冻结版本 ID、版本号和摘要。不能用草稿、跨过程模型或名称相同的其他版本代替。"
          />
          <div className="mt-4">
            <Typography.Text strong>已发布 DWD 来源</Typography.Text>
            <Select
              className="mt-2 w-full"
              allowClear
              value={selectedUpstreamId}
              disabled={!canEdit}
              placeholder="请选择同业务过程的 DWD 正式模型"
              options={candidates.map((item) => ({
                value: item.modelId,
                label: item.name + ' (' + item.code + ') · '
                  + (item.usable ? '正式 V' + String(item.publishedVersionNo) : '未发布 / 不可用'),
                disabled: !item.usable,
              }))}
              onChange={(value) => {
                changeContract(() => {
                  setUpstreamIds(value ? [value] : []);
                  setUseLatestSource(true);
                  setBusinessTimeColumn('');
                });
              }}
            />
          </div>
          {sourceDrifted && (
            <Alert
              className="mt-4"
              showIcon
              type="warning"
              message={'上游正式版本发生变化：合同冻结 V' + pinnedUpstream?.versionNo
                + '，当前正式指针 V' + selectedCandidate?.publishedVersionNo}
              description="可以继续查看冻结版本，但不能基于旧快照直接发布。确认采用当前正式版本后，必须重新选择业务时间、重新校验并保存新的粒度修订。"
              action={!useLatestSource && canEdit ? (
                <Button size="small" type="primary" onClick={() => {
                  setUseLatestSource(true);
                  setBusinessTimeColumn('');
                  setContractDirty(true);
                }}>
                  采用当前正式版本 V{selectedCandidate?.publishedVersionNo}
                </Button>
              ) : null}
            />
          )}
          <div className="mt-4">
            <Typography.Text strong>来源精确版本中的业务时间字段</Typography.Text>
            <Spin spinning={sourceLoading}>
              <Select
                className="mt-2 w-full"
                allowClear
                value={businessTimeColumn || undefined}
                disabled={!canEdit || sourceLoading || !businessTimeOptions.length}
                placeholder="从真实已发布结构中的 DATE / TIME 字段选择"
                options={businessTimeOptions}
                onChange={(value) => changeContract(() => {
                  setBusinessTimeColumn(value || '');
                })}
              />
            </Spin>
            {sourceLoadError && <Alert className="mt-3" type="error" showIcon message={sourceLoadError} />}
            {!sourceLoading && selectedCandidate && sourceStructure && !businessTimeOptions.length && (
              <Alert className="mt-3" type="error" showIcon
                message="该精确版本没有可用的日期时间字段"
                description="请返回来源模型核对字段类型，不能用字符串或字段名猜测业务时间。"
              />
            )}
          </div>
          <Divider />
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="已读取结构">
              {sourceStructure
                ? String(sourceStructure.columns?.length ?? 0) + ' 个字段 · 来源版本 V' + String(selectedSourceVersionNo)
                : '尚未读取'}
            </Descriptions.Item>
            <Descriptions.Item label="事实粒度">
              {selectedCandidate?.name?.includes('明细') ? '每行可能是一条明细，订单统计必须按业务键去重' : '请根据来源业务定义确认一行代表的事实'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      );
    }
    if (step === 2) {
      return (
        <Card title="定义统计时间与分组">
          <Alert showIcon type="info"
            message="把“一行代表什么”定义清楚"
            description="统计周期、IANA 时区、业务时间、周期桶和维度共同决定目标行粒度；周期桶必须是分组维度。"
          />
          <Row gutter={[16, 16]} className="mt-4">
            <Col xs={24} md={12}>
              <Typography.Text strong>统计周期</Typography.Text>
              <Select className="mt-2 w-full" value={statPeriod} disabled={!canEdit}
                options={[
                  { value: '1h', label: '每小时' },
                  { value: '1d', label: '每天' },
                  { value: '1w', label: '每周' },
                  { value: '1m', label: '每月' },
                  { value: 'ALL', label: '全量（需业务单独确认）' },
                ]}
                onChange={(value) => changeContract(() => {
                  setStatPeriod(value);
                })}
              />
            </Col>
            <Col xs={24} md={12}>
              <Typography.Text strong>统计时区（IANA）</Typography.Text>
              <Input className="mt-2" value={timeZone} disabled={!canEdit}
                placeholder="Asia/Shanghai"
                onChange={(event) => changeContract(() => {
                  setTimeZone(event.target.value);
                })}
              />
            </Col>
            <Col xs={24} md={12}>
              <Typography.Text strong>周期桶字段</Typography.Text>
              <Select className="mt-2 w-full" allowClear value={periodBucketColumn}
                disabled={!canEdit} options={dimensionOptions}
                placeholder="必须属于目标分组维度"
                onChange={(value) => changeContract(() => {
                  setPeriodBucketColumn(value);
                })}
              />
            </Col>
            <Col xs={24} md={12}>
              <Typography.Text strong>分组维度（必须有标准字段）</Typography.Text>
              <Select className="mt-2 w-full" mode="multiple" value={dimensions}
                disabled={!canEdit} options={dimensionOptions}
                placeholder="例如 order_day、order_city"
                onChange={(value) => changeContract(() => {
                  setDimensions(value);
                })}
              />
            </Col>
          </Row>
          {periodBucketColumn && !dimensions.includes(periodBucketColumn) && (
            <Alert className="mt-4" type="error" showIcon
              message="周期桶必须同时参与分组，避免跨周期错误合并" />
          )}
          <Alert className="mt-4" type="warning" showIcon
            message="时间语义仍需业务确认"
            description="跨时区/DST、周起点、迟到事件、NULL 时间与重算策略不由当前粒度合同自动决定。"
          />
          <div className="mt-4 flex justify-end">
            <Button type="primary" onClick={() => void saveContract()} loading={saving}
              disabled={!canEdit || dirty || !structure.definition}>
              保存并冻结当前 DWS 粒度草稿
            </Button>
          </div>
        </Card>
      );
    }
    if (step === 3) {
      return (
        <Card title="定义与验证聚合度量" extra={<Tag color="orange">样本与真实查询分离</Tag>}>
          <Alert showIcon type="info"
            message="度量函数来自当前物理结构"
            description="此处展示并核对已保存表结构中的标准度量和聚合函数；如需修改函数，请先在表结构编辑区修改并保存。"
          />
          <Row gutter={[16, 16]} className="mt-4">
            <Col xs={24} md={12}>
              <Card size="small" title="标准维度">
                <Select className="w-full" mode="multiple" value={dimensions} disabled={!canEdit}
                  options={dimensionOptions}
                  onChange={(value) => changeContract(() => {
                    setDimensions(value);
                  })}
                />
              </Card>
            </Col>
            <Col xs={24} md={12}>
              <Card size="small" title="聚合度量">
                <Select className="w-full" mode="multiple" value={measures} disabled={!canEdit}
                  options={measureOptions}
                  placeholder="选择已有标准字段与聚合函数"
                  onChange={(value) => changeContract(() => {
                    setMeasures(value);
                  })}
                />
                <div className="mt-3">
                  {measures.length ? measures.map((name) => {
                    const field = measureColumns.find((item) => item.columnName === name);
                    return <div key={name} className="mb-2 flex items-center justify-between rounded-lg border border-[#EAECF0] px-3 py-2">
                      <span>{name}</span>
                      <Tag color={field?.aggregateFunc === 'COUNT_DISTINCT' ? 'blue' : 'default'}>
                        {field?.aggregateFunc || '函数未配置'}
                      </Tag>
                    </div>;
                  }) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未选择度量" />}
                </div>
              </Card>
            </Col>
          </Row>
          <Divider />
          <Row gutter={[16, 16]}>
            <Col xs={24} md={12}>
              <Card size="small" title={<Space><CheckCircleOutlined />V0 服务端静态校验</Space>}>
                <Typography.Paragraph type="secondary">
                  使用当前项目真实模型结构、来源发布指针和标准字段合同；只读校验，不保存、不执行 SQL。
                </Typography.Paragraph>
                <Space wrap>
                  <Tag color={staticStatus === 'STATIC_PASS' ? 'success' : staticStatus === 'STATIC_BLOCKED' ? 'error' : 'default'}>
                    {staticStatus}
                  </Tag>
                  <Button onClick={() => void validateContract()} loading={loading} disabled={dirty || !structure.definition}>
                    验证设计合同
                  </Button>
                </Space>
                {staticValidation && staticStatus !== 'STALE' && staticValidation.result.blockers.length > 0 && (
                  <ul className="mt-3 list-disc pl-5 text-sm text-[#B42318]">
                    {staticValidation.result.blockers.map((item) => <li key={item}>{item}</li>)}
                  </ul>
                )}
              </Card>
            </Col>
            <Col xs={24} md={12}>
              <Card size="small" title={<Space><ExperimentOutlined />V1 固定合成样本演示</Space>}>
                <Typography.Paragraph type="secondary">
                  仅用于说明订单去重规则。O001 两条明细 100/200，O002 一条明细 150；固定样本 3 行、2 笔订单、行金额合计 450。不是实际支付净额，不查询真实业务库。
                </Typography.Paragraph>
                <Space wrap>
                  <Tag color={sampleStatus === 'SAMPLE_MATCH' ? 'success' : sampleStatus === 'SAMPLE_WARNING' ? 'warning' : 'default'}>
                    {sampleStatus}
                  </Tag>
                  <Button onClick={validateSample} disabled={!staticIsCurrent || dirty}>
                    运行固定样本演示
                  </Button>
                </Space>
                <div className="mt-3">
                  {sampleStatus === 'SAMPLE_MATCH' || sampleStatus === 'SAMPLE_WARNING' ? (
                    <Table
                      size="small"
                      pagination={false}
                      rowKey="id"
                      dataSource={[
                        { id: '1', order: 'O001', item: '01', amount: 100 },
                        { id: '2', order: 'O001', item: '02', amount: 200 },
                        { id: '3', order: 'O002', item: '01', amount: 150 },
                      ]}
                      columns={[
                        { title: '订单', dataIndex: 'order' },
                        { title: '明细', dataIndex: 'item' },
                        { title: '行金额', dataIndex: 'amount', align: 'right' },
                      ]}
                      summary={() => (
                        <Table.Summary.Row>
                          <Table.Summary.Cell index={0} colSpan={2}>明细金额合计（非净支付额）</Table.Summary.Cell>
                          <Table.Summary.Cell index={2} align="right">450</Table.Summary.Cell>
                        </Table.Summary.Row>
                      )}
                    />
                  ) : null}
                  <Alert className="mt-3"
                    type={sampleStatus === 'SAMPLE_WARNING' ? 'warning' : sampleStatus === 'UNAVAILABLE' ? 'info' : 'success'}
                    showIcon
                    message={sampleDescription}
                  />
                </div>
              </Card>
            </Col>
          </Row>
        </Card>
      );
    }
    if (step === 4) {
      return (
        <Card title="设计审核与发布">
          <Alert showIcon type="info"
            message="发布只固化模型设计版本，不执行 SQL"
            description="发布入口继续使用 Modeling 已有的对比/审批流程。真实数据对账、质量结果、Dataset/Service 消费授权仍属于各自 Owner。"
          />
          <Descriptions className="mt-4" bordered size="small" column={{ xs: 1, sm: 2 }}>
            <Descriptions.Item label="来源版本">{sourceLabel}</Descriptions.Item>
            <Descriptions.Item label="统计周期 / 时区">{statPeriod + ' · ' + timeZone}</Descriptions.Item>
            <Descriptions.Item label="周期桶">{periodBucketColumn || '未设置'}</Descriptions.Item>
            <Descriptions.Item label="分组维度">{dimensions.join(' × ') || '未设置'}</Descriptions.Item>
            <Descriptions.Item label="度量">{measures.join('、') || '未设置'}</Descriptions.Item>
            <Descriptions.Item label="当前草稿">{saved ? 'R' + saved.revision : '未保存'}</Descriptions.Item>
          </Descriptions>
          <Divider />
          <Table
            size="small"
            pagination={false}
            rowKey="key"
            dataSource={[
              { key: 'V0', layer: 'V0 静态设计合同', status: staticStatus, detail: '真实结构、标准字段、上游版本和时区规则' },
              { key: 'V1', layer: 'V1 固定样本演示', status: sampleStatus, detail: '固定合成样本；不是业务库只读查询' },
              { key: 'V2', layer: 'V2 运行后对账', status: 'NOT_EXECUTED', detail: '没有真实 Runtime / Quality 回执' },
            ]}
            columns={[
              { title: '证据层', dataIndex: 'layer' },
              { title: '状态', dataIndex: 'status', render: (value: string) => <Tag>{value}</Tag> },
              { title: '证据边界', dataIndex: 'detail' },
            ]}
          />
          {blockers.length > 0 ? (
            <Alert className="mt-4" type="warning" showIcon
              message={'当前有 ' + blockers.length + ' 项待处理'}
              description={<ul className="list-disc pl-5">{blockers.map((item) => <li key={item}>{item}</li>)}</ul>}
            />
          ) : (
            <Alert className="mt-4" type="success" showIcon
              message="当前设计合同静态校验通过"
              description="这仅允许进入既有模型设计发布流程，不代表数据已运行、对账或可被消费。"
            />
          )}
          <div className="mt-4 flex justify-end">
            <Button type="primary" disabled={!canEdit || blockers.length > 0 || !onRequestPublish}
              onClick={() => onRequestPublish?.()}>
              审核与发布设计
            </Button>
          </div>
        </Card>
      );
    }
    return (
      <Card title={<Space><HistoryOutlined />版本与恢复</Space>} extra={
        <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>重新读取</Button>
      }>
        <Alert showIcon type="info"
          message="恢复历史修订会生成新的前向修订"
          description="不会把修订号倒退。若当前物理结构指纹或 DWD 精确发布版本已变化，服务端会拒绝直接恢复，需先审阅差异。"
        />
        <div className="mt-4">
          <Table
            rowKey="revision"
            size="small"
            loading={loading}
            pagination={{ pageSize: 8, hideOnSinglePage: true }}
            dataSource={history}
            locale={{ emptyText: '暂无可恢复的 DWS 粒度修订' }}
            columns={[
              { title: '修订', dataIndex: 'revision', render: (value: number) => <Tag color={value === saved?.revision ? 'blue' : 'default'}>R{value}{value === saved?.revision ? ' · 当前' : ''}</Tag> },
              { title: '更新时间', dataIndex: 'updatedAt', render: (value: string) => displayTime(value) },
              { title: '操作人', dataIndex: 'updatedBy' },
              { title: '来源版本', render: (_: unknown, item: DwsGrainHistoryEntry) => item.design.upstreamVersions.map((source) => '模型 #' + source.modelId + ' V' + source.versionNo).join('、') || '—' },
              { title: '摘要', dataIndex: 'designSha256', render: (value: string) => value.slice(0, 10) + '…' },
              { title: '操作', render: (_: unknown, item: DwsGrainHistoryEntry) => (
                <Button size="small" disabled={!canEdit || item.revision === saved?.revision}
                  loading={restoringRevision === item.revision}
                  onClick={() => void restoreRevision(item.revision)}>
                  恢复为新修订
                </Button>
              ) },
            ]}
          />
        </div>
        <Typography.Paragraph type="secondary" className="mt-3">
          历史版本最多展示最近 50 个已保存修订。已发布模型版本、真实运行状态和下游消费状态仍在各自证据面板中查询。
        </Typography.Paragraph>
      </Card>
    );
  };

  return (
    <Card bordered={false} className="!rounded-xl"
      title={<div>
        <Space wrap>
          <Typography.Text strong className="text-[#175CD3]">DWS / 汇总建模</Typography.Text>
          <Typography.Text type="secondary">{processName || '业务过程'} · 模型 #{modelId}</Typography.Text>
          {saved ? <Tag color="blue">设计草稿 · R{saved.revision}</Tag> : <Tag>尚未保存</Tag>}
        </Space>
        <Typography.Title level={4} className="!mb-1 !mt-2">{modelName || 'DWS 汇总模型'}</Typography.Title>
        <Typography.Paragraph type="secondary" className="!mb-0">
          先确定业务粒度和可信来源，再校验聚合设计，最后发布可追溯的模型设计。
        </Typography.Paragraph>
      </div>}
      extra={<Space wrap>
        <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>重新读取</Button>
        <Button onClick={() => void saveContract()} loading={saving}
          disabled={!canEdit || dirty || !structure.definition}>保存草稿</Button>
        <Button type="primary" onClick={() => setStep(4)}>审核与发布设计</Button>
      </Space>}
    >
      <Steps current={step} onChange={setStep} responsive items={WORKFLOW_STEPS} />
      <Row gutter={[16, 16]} className="mt-4">
        <Col xs={24} xl={16}>
          <Spin spinning={loading && step !== 3}>
            {renderStep()}
          </Spin>
          <Card size="small" className="mt-4">
            <div className="flex items-center justify-between gap-3">
              <Typography.Text type="secondary">步骤 {step + 1} / 6 · DWS 专项高保真实施版</Typography.Text>
              <Space>
                <Button disabled={step === 0} onClick={() => setStep((value) => Math.max(0, value - 1))}>上一步</Button>
                <Button type="primary" onClick={() => setStep((value) => Math.min(5, value + 1))}>
                  {step === 5 ? '返回版本恢复' : '下一步'}
                </Button>
              </Space>
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="设计完整性" size="small">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="项目 / 模型">{modelName || modelCode || 'DWS'} / #{modelId}</Descriptions.Item>
              <Descriptions.Item label="来源版本">{sourceLabel}</Descriptions.Item>
              <Descriptions.Item label="草稿">{saved ? 'R' + saved.revision : '未保存'}</Descriptions.Item>
              <Descriptions.Item label="V0 静态校验"><Tag>{staticStatus}</Tag></Descriptions.Item>
              <Descriptions.Item label="V1 固定样本"><Tag>{sampleStatus}</Tag></Descriptions.Item>
              <Descriptions.Item label="V2 运行对账"><Tag>NOT_EXECUTED</Tag></Descriptions.Item>
            </Descriptions>
            {blockers.length > 0 && (
              <>
                <Divider />
                <Typography.Text type="secondary">待处理项 · {blockers.length}</Typography.Text>
                <ul className="mt-2 list-disc pl-5 text-sm text-[#B54708]">
                  {blockers.slice(0, 5).map((item) => <li key={item}>{item}</li>)}
                </ul>
              </>
            )}
          </Card>
          <Card title="当前边界" size="small" className="mt-3">
            <Typography.Paragraph type="secondary" className="!mb-2">
              当前工作台执行真实服务端静态合同校验，并提供固定合成样本演示。不会查询业务库、生成 SQL、创建 DataDev 任务或声明目标数据已落仓。
            </Typography.Paragraph>
            <Tag>设计事实与运行事实分离</Tag>
          </Card>
        </Col>
      </Row>
    </Card>
  );
}
