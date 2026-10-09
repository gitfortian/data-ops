import { Alert, Button, Collapse, Select, Space, Table, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useLatestOperation } from '@/hooks/useLatestOperation';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { governanceEntryPath } from '@/services/agent/governance';
import { listModelingVersions } from '@/services/modeling/api';
import { matchesStructureReview, prepareModelStructureReview, type ModelStructureReviewContext } from '@/services/modeling/structureReview';

const REASONS: Record<string, string> = {
  ORPHAN_MAPPING_TARGET: '映射目标已不在保存结构中', UNMAPPED_SAVED_COLUMN: '当前保存字段未映射',
  CHANGED_TARGET_REVIEW: '变更字段的当前映射需核对', TRANSFORM_MANUAL_REVIEW: '转换表达式需人工复核',
};
const AREAS: Record<string, string> = { TABLE: '表', COLUMN: '字段', PRIMARY_KEY: '主键', INDEX: '索引', PARTITION: '分区' };

function describeChange(area: string, value: string | null): string {
  if (value === null) return '不存在';
  try {
    const fact = JSON.parse(value);
    if (area === 'TABLE' && typeof fact === 'string') return fact;
    if (area === 'PRIMARY_KEY' && Array.isArray(fact)) return fact.join('、') || '未设置';
    if (area === 'COLUMN' && fact && typeof fact.name === 'string' && typeof fact.dataType === 'string') {
      const size = fact.length == null ? '' : `（长度 ${fact.length}${fact.scale == null ? '' : `，小数位 ${fact.scale}`}）`;
      return `${fact.name} · ${fact.dataType}${size} · ${fact.nullable == null ? '可空性未记录' : fact.nullable ? '可空' : '不可空'} · 顺序 ${fact.sortOrder ?? '未记录'}`;
    }
    if (area === 'INDEX' && fact && Array.isArray(fact.columns)) return `${fact.name} · ${fact.type ?? '类型未记录'} · ${fact.unique == null ? '唯一性未记录' : fact.unique ? '唯一' : '非唯一'} · ${fact.columns.join('、')}`;
    if (area === 'PARTITION' && fact && Array.isArray(fact.columns)) return `${fact.type ?? '类型未记录'} · ${fact.columns.join('、')}`;
  } catch { /* Older display-only text remains literal React text. */ }
  return value;
}

export function requestedStructureReviewVersion(search: string): number | undefined {
  const query = new URLSearchParams(search);
  const value = query.get('reviewVersion');
  return query.getAll('reviewVersion').length === 1 && value && /^[1-9][0-9]{0,9}$/.test(value)
    && Number(value) <= 2147483647 ? Number(value) : undefined;
}

/** Only server-prepared saved inputs can open an Agent task; no automatic sending or business writes. */
export default function ModelStructureReviewPanel({ modelId, search }: { modelId: string; search: string }) {
  const { currentProject } = useSecurityProject();
  const { canAll } = usePermissionAccess();
  const allowed = canAll(['modeling:read', 'agent:chat:run']);
  const projectId = currentProject?.id == null ? '' : String(currentProject.id);
  const resource = JSON.stringify([projectId, modelId, allowed, search]);
  const [selection, setSelection] = useState<{ resource: string; version?: number }>();
  const versionNo = selection?.resource === resource ? selection.version : requestedStructureReviewVersion(search);
  const [versions, setVersions] = useState<{ resource: string; items: number[] }>();
  const [prepared, setPrepared] = useState<{ scope: string; data: ModelStructureReviewContext }>();
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState<{ scope: string; text: string }>();
  const scope = JSON.stringify([resource, versionNo]);
  const beginVersions = useLatestOperation(resource);
  const beginPrepare = useLatestOperation(scope);
  const visibleVersions = versions?.resource === resource ? versions.items : [];
  const selected = versionNo !== undefined && visibleVersions.includes(versionNo);
  const ready = prepared?.scope === scope ? prepared.data : undefined;

  useEffect(() => {
    const live = beginVersions();
    if (!allowed || !projectId || !/^[1-9][0-9]{0,18}$/.test(modelId)) return;
    void listModelingVersions(modelId).then((items) => {
      if (live()) setVersions({ resource, items: (items ?? []).map((item) => item.versionNo).filter((value) => Number.isInteger(value) && value > 0 && value <= 2147483647) });
    }).catch(() => { if (live()) setError({ scope, text: '基准版本列表不可用，请刷新核对。' }); });
  }, [resource, beginVersions]);

  const prepare = async () => {
    const live = beginPrepare();
    setPrepared(undefined); setError(undefined); setBusy(scope);
    try {
      if (!allowed || !projectId || !selected || versionNo === undefined) throw new Error('请选择有效基准版本');
      const data = await prepareModelStructureReview(modelId, versionNo);
      if (!live()) return;
      if (!matchesStructureReview(data, projectId, modelId, versionNo)) throw new Error('比较目标或返回范围不匹配');
      setPrepared({ scope, data });
    } catch { if (live()) setError({ scope, text: '结构比较不可用、已变化或超出范围，请在原模型页核对后重新准备。' }); }
    finally { if (live()) setBusy(undefined); }
  };

  if (!allowed || !projectId) return null;
  const requested = new URLSearchParams(search).has('reviewVersion');
  const hasWork = ready && (ready.changes.length > 0 || ready.mappingChecks.length > 0);
  return <section aria-label="模型结构变更核对" className="space-y-3 rounded-lg border border-[#e7e9ec] p-4">
    <Typography.Title level={5}>模型结构变更与映射检查</Typography.Title>
    <Typography.Paragraph type="secondary">选择发布基准，与已保存结构比较；未保存编辑不包含。映射仅为当前状态，没有历史映射快照。</Typography.Paragraph>
    {requested && !selected && selection?.resource !== resource && <Alert type="warning" showIcon message="指定基准版本尚未确认或已失效，请重新选择；不会替换为当前版本。" />}
    <Space wrap>
      <Select aria-label="结构比较基准版本" style={{ width: 220 }} placeholder="选择发布基准版本" value={selected ? versionNo : undefined}
        options={visibleVersions.map((value) => ({ value, label: `发布版本 V${value}` }))}
        onChange={(value: number) => { setSelection({ resource, version: value }); setPrepared(undefined); setError(undefined); }} />
      <Button disabled={!selected || busy === scope} loading={busy === scope} onClick={() => void prepare()}>准备结构比较</Button>
    </Space>
    {error?.scope === scope && <Alert type="warning" showIcon message={error.text} />}
    {ready && <>
      <Typography.Paragraph>基准 V{ready.baselineVersionNo}（{ready.baselineColumnCount} 字段）→ 已保存结构（{ready.savedColumnCount} 字段）；结构差异 {ready.changes.length} 项，当前映射待检查 {ready.mappingChecks.length} 项。</Typography.Paragraph>
      <Alert type="info" showIcon message="未检查源字段存在性或类型兼容；未比较默认值、表达式、表属性、描述、标准语义及模型关系。清单不代表完整影响或允许发布。" />
      <Collapse items={[{ key: 'changes', label: '查看结构差异', children: <Table rowKey={(_, index) => String(index)} pagination={false} size="small" dataSource={ready.changes}
        columns={[{ title: '范围', dataIndex: 'area', render: (area: string) => AREAS[area] }, { title: '对象', dataIndex: 'name' }, { title: '基准', dataIndex: 'before', render: (value: string | null, item) => describeChange(item.area, value) }, { title: '已保存', dataIndex: 'after', render: (value: string | null, item) => describeChange(item.area, value) }]} /> },
      { key: 'mappings', label: '查看当前映射检查清单', children: <Table rowKey="targetColumn" pagination={false} size="small" dataSource={ready.mappingChecks}
        columns={[{ title: '目标字段', dataIndex: 'targetColumn' }, { title: '待检查项', dataIndex: 'reasons', render: (values: string[]) => values.map((value) => REASONS[value]).join('；') }]} /> }]} />
      <Space wrap>
        {hasWork ? <Button href={governanceEntryPath({ purpose: 'MODEL_STRUCTURE_REVIEW', modelStructureReview: { modelId, baselineVersionNo: ready.baselineVersionNo, definition: ready.definition } })}>AI 结构变更说明</Button>
          : <Typography.Text type="secondary">白名单内未发现结构差异或映射待检查项；仍需人工核对未覆盖范围。</Typography.Text>}
        <Button href={`/modeling/models/${modelId}/mapping`}>打开来源映射核对</Button>
      </Space>
    </>}
  </section>;
}
