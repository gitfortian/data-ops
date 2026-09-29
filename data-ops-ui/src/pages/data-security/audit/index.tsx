import { YakEmpty } from '@/components/ui';
import { pageAccessLogs, topAccessActors } from '@/services/data-security/api';
import type { AccessLog } from '@/services/data-security/types';
import { Card, DatePicker, Input, Select, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../shared';

const { RangePicker } = DatePicker;

const DECISION_META: Record<string, { label: string; color: string }> = {
  ALLOW: { label: '放行', color: 'green' },
  DENY: { label: '拒绝', color: 'red' },
  NEED_APPROVAL: { label: '需审批', color: 'gold' },
};

const fmt = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-');
const num = (value: unknown) => { const n = Number(value); return Number.isFinite(n) ? n : 0; };

const DataSecurityAuditPage = () => {
  const [records, setRecords] = useState<AccessLog[]>([]);
  const [actors, setActors] = useState<Record<string, unknown>[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [actor, setActor] = useState('');
  const [decision, setDecision] = useState<string | undefined>();
  const [resourceKey, setResourceKey] = useState('');
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageAccessLogs({
        pageNo: p,
        pageSize: s,
        actor: actor.trim() || undefined,
        decision,
        resourceKey: resourceKey.trim() || undefined,
        start: range?.[0] ? range[0].format('YYYY-MM-DDTHH:mm:ss') : undefined,
        end: range?.[1] ? range[1].format('YYYY-MM-DDTHH:mm:ss') : undefined,
      });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载访问审计日志失败');
    } finally {
      setLoading(false);
    }
  }, [actor, decision, resourceKey, range]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, load]);
  useEffect(() => {
    topAccessActors().then((a) => setActors(a ?? [])).catch(() => undefined);
  }, []);

  const columns: ColumnsType<AccessLog> = [
    { title: '时间', dataIndex: 'accessTime', width: 170, render: fmt },
    { title: '主体', dataIndex: 'actor', width: 140 },
    { title: '资源', dataIndex: 'resourceKey', ellipsis: true, render: (v: string, r) => v || r.resourceName || '-' },
    { title: '操作', dataIndex: 'action', width: 90, render: (v?: string) => v || '-' },
    { title: '等级', dataIndex: 'levelCode', width: 100, render: (v?: string) => v || '-' },
    {
      title: '裁决', dataIndex: 'decision', width: 100,
      render: (v: string) => { const m = DECISION_META[v] ?? { label: v, color: 'default' }; return <Tag color={m.color}>{m.label}</Tag>; },
    },
    {
      title: '脱敏', dataIndex: 'masked', width: 110,
      render: (v: number, r) => (v === 1 ? <Tag color="orange">{r.algoCode || '已脱敏'}</Tag> : <span className="text-[#98a2b3]">明文</span>),
    },
    { title: '来源', dataIndex: 'source', width: 110, render: (v?: string) => v || '-' },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <PageHeader title="访问审计" subtitle="数据访问留痕：谁、访问了什么、如何裁决与脱敏" />

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Input allowClear placeholder="按主体搜索" className="!w-44" value={actor} onChange={(e) => setActor(e.target.value)} />
        <Input allowClear placeholder="按资源键搜索" className="!w-52" value={resourceKey} onChange={(e) => setResourceKey(e.target.value)} />
        <Select allowClear placeholder="按裁决" className="!w-32" value={decision} onChange={setDecision}
          options={Object.entries(DECISION_META).map(([value, v]) => ({ label: v.label, value }))} />
        <RangePicker showTime value={range} onChange={(v) => setRange(v as [dayjs.Dayjs, dayjs.Dayjs] | null)} />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[1fr_260px]">
        <Table<AccessLog> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading}
          locale={{ emptyText: <YakEmpty compact title="暂无访问记录" description="数据访问链路接入后自动留痕" /> }}
          pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />
        <Card title="访问热点主体" size="small">
          {actors.length === 0 ? (
            <div className="text-[13px] text-[#98a2b3]">暂无统计数据</div>
          ) : (
            <Space direction="vertical" style={{ width: '100%' }} size={8}>
              {actors.slice(0, 12).map((a, i) => (
                <div key={`${String(a.actor ?? a.subject ?? '')}-${i}`} className="flex items-center justify-between text-[13px]">
                  <span className="truncate">{String(a.actor ?? a.subject ?? '-')}</span>
                  <Tag color={num(a.count ?? a.total) > 0 ? 'blue' : 'default'}>{num(a.count ?? a.total)}</Tag>
                </div>
              ))}
            </Space>
          )}
        </Card>
      </div>
    </div>
  );
};

export default DataSecurityAuditPage;
