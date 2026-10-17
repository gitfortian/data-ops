import { Alert, Button, Card, Input, Select, Space, Tag, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getDwsGrain, listDwsGrainCandidates, saveDwsGrain,
} from '@/services/modeling/dwsGrain';
import type {
  DwsGrainCandidate, DwsGrainSaved,
} from '@/services/modeling/dwsGrain';
import type { ModelingStructureRecord } from '@/services/modeling/types';

interface Props {
  modelId: number;
  structure: ModelingStructureRecord;
  dirty: boolean;
}

/** In the existing model editor; no new design truth or separate SQL editor. */
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

  const columns = structure.columns ?? [];
  const dimensionColumns = useMemo(() => columns.filter(
    (field) => field.columnName && field.fieldRole === 'DIMENSION' && field.stdFieldId,
  ), [columns]);
  const measureColumns = useMemo(() => columns.filter(
    (field) => field.columnName && field.fieldRole === 'MEASURE'
      && field.stdFieldId && field.aggregateFunc,
  ), [columns]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [stored, sources] = await Promise.all([
        getDwsGrain(modelId), listDwsGrainCandidates(modelId),
      ]);
      setSaved(stored);
      setCandidates(sources ?? []);
      if (stored) {
        setStatPeriod(stored.design.statPeriod);
        setTimeZone(stored.design.timeZone);
        setBusinessTimeColumn(stored.design.businessTimeColumn);
        setPeriodBucketColumn(stored.design.periodBucketColumn);
        setDimensions(stored.design.dimensions);
        setMeasures(stored.design.measures.map((item) => item.column));
        setUpstreamIds(stored.design.upstreamVersions.map((item) => item.modelId));
      }
    } catch {
      message.error('DWS 粒度合同加载失败，请核对项目权限和模型状态');
    } finally {
      setLoading(false);
    }
  }, [modelId]);

  useEffect(() => {
    void load();
  }, [load]);

  const store = async () => {
    if (dirty || !structure.definition) {
      message.warning('请先保存当前表结构，再确认 DWS 粒度');
      return;
    }
    if (!businessTimeColumn.trim() || !periodBucketColumn || !dimensions.length
        || !measures.length || !upstreamIds.length) {
      message.warning('请补全业务时间、周期桶、维度、度量与已发布 DWD 上游');
      return;
    }
    setSaving(true);
    try {
      const recorded = await saveDwsGrain(modelId, {
        statPeriod,
        timeZone: timeZone.trim(),
        businessTimeColumn: businessTimeColumn.trim(),
        periodBucketColumn,
        dimensions,
        measures: measures.map((column) => ({
          column,
          function: measureColumns.find((row) => row.columnName === column)?.aggregateFunc ?? '',
        })),
        upstreamModelIds: upstreamIds,
        expectedStructureFingerprint: structure.definition,
      });
      setSaved(recorded);
      message.success('DWS 粒度设计已保存。正式发布仍会重新检查上游版本和字段');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '粒度保存失败，请重新核对来源');
    } finally {
      setSaving(false);
    }
  };

  const dimensionOptions = dimensionColumns.map((column) => ({
    label: column.columnName as string, value: column.columnName as string,
  }));
  return (
    <Card bordered={false} title="DWS 汇总粒度与版本合同"
      extra={<Space>
        {saved ? <Tag color="blue">设计修订 {saved.revision}</Tag> : <Tag>未确认</Tag>}
        <Button onClick={() => void load()} loading={loading}>重新读取</Button>
      </Space>}
    >
      <div className="space-y-4">
        <Alert type="info" showIcon
          title="保存的是可复现的汇总设计，不是计算成功"
          description="先在上方完成维度/度量标准字段和聚合函数，再保存表结构。这里确认 DWD 已发布版本、时间时区、统计粒度和度量口径；发布版本前服务端会重新验证。"
        />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-[13px] text-[#475467]">统计周期</span>
            <Select className="w-full" value={statPeriod} onChange={setStatPeriod}
              options={[
                { value: '1h', label: '每小时' }, { value: '1d', label: '每天' },
                { value: '1w', label: '每周' }, { value: '1m', label: '每月' },
                { value: 'ALL', label: '全量' },
              ]}
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-[13px] text-[#475467]">统计时区（IANA）</span>
            <Input value={timeZone} onChange={(event) => setTimeZone(event.target.value)}
              placeholder="Asia/Shanghai" />
          </label>
          <label className="block">
            <span className="mb-1 block text-[13px] text-[#475467]">上游已发布版本中的业务时间列</span>
            <Input value={businessTimeColumn}
              onChange={(event) => setBusinessTimeColumn(event.target.value)}
              placeholder="例如 event_time；必须是上游真实日期/时间字段" />
          </label>
          <label className="block">
            <span className="mb-1 block text-[13px] text-[#475467]">本汇总表的统计周期桶字段</span>
            <Select className="w-full" allowClear value={periodBucketColumn}
              onChange={setPeriodBucketColumn} options={dimensionOptions}
              placeholder="必须属于分组维度，避免跨天错误合并" />
          </label>
        </div>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-[13px] text-[#475467]">分组维度（需标准字段）</span>
            <Select className="w-full" mode="multiple" value={dimensions}
              onChange={setDimensions} options={dimensionOptions}
              placeholder="例如 order_day、order_city" />
          </label>
          <label className="block">
            <span className="mb-1 block text-[13px] text-[#475467]">聚合度量（函数继承表结构）</span>
            <Select className="w-full" mode="multiple" value={measures}
              onChange={setMeasures}
              options={measureColumns.map((col) => ({
                label: `${col.columnName} · ${col.aggregateFunc}`, value: col.columnName as string,
              }))}
              placeholder="至少一项 COUNT / SUM 等度量" />
          </label>
        </div>
        <label className="block">
          <span className="mb-1 block text-[13px] text-[#475467]">已发布的同过程 DWD 上游版本</span>
          <Select className="w-full" mode="multiple" value={upstreamIds}
            onChange={setUpstreamIds} options={candidates.map((item) => ({
              value: item.modelId,
              label: `${item.name}（${item.code}） · ${item.usable ? `V${item.publishedVersionNo}` : '未发布，不可选'}`,
              disabled: !item.usable,
            }))}
            placeholder="请确认精确发布版本，不能使用草稿" />
        </label>
        {saved && saved.structureFingerprint !== structure.definition ? (
          <Alert showIcon type="warning" title="模型表结构与上次粒度确认不同，请重新保存粒度合同" />
        ) : null}
        {saved ? (
          <div className="text-[12px] text-[#667085]">
            已保存摘要：{saved.designSha256.slice(0, 16)}… · 上游版本
            {saved.design.upstreamVersions.map((source) =>
              ` 模型 #${source.modelId} V${source.versionNo}`).join('、')}
          </div>
        ) : null}
        <div className="flex justify-end">
          <Button type="primary" loading={saving} disabled={dirty || !structure.definition}
            onClick={() => void store()}>保存并冻结当前 DWS 粒度草稿</Button>
        </div>
      </div>
    </Card>
  );
}
