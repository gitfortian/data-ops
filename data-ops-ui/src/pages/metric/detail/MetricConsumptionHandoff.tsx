import { useEffect, useState } from 'react';
import { Alert, Button, Space } from 'antd';
import { useNavigate } from '@umijs/max';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { getMetricUsageList } from '@/services/metric/api';
import type { MetricUsageRecord, PublishedMetricContract } from '@/services/metric/types';
import { consumptionProductPath } from '@/services/consumption/navigation';

/** Only a declared Dataset identity at the exact published version has a canonical mapping. */
export function metricConsumptionPath(metricId: number, version: number, usage: MetricUsageRecord): string | undefined {
  if (usage.usageType !== 'DATASET' || usage.metricVersion !== version
    || !Number.isSafeInteger(usage.usageId) || usage.usageId <= 0) return undefined;
  return `${consumptionProductPath('DATASET', String(usage.usageId))}?returnMetricId=${metricId}`;
}

export default function MetricConsumptionHandoff({ metricId, publication }: {
  metricId: number; publication: PublishedMetricContract | null;
}) {
  const navigate = useNavigate();
  const { currentProject } = useSecurityProject();
  const scope = JSON.stringify([currentProject?.id, metricId, publication?.publicationEventId]);
  const [result, setResult] = useState<{ scope: string; usages: MetricUsageRecord[]; error?: string }>();
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let cancelled = false;
    if (!publication) return;
    getMetricUsageList(metricId).then(usages => {
      if (!cancelled) setResult({ scope, usages });
    }).catch(() => { if (!cancelled) setResult({ scope, usages: [], error: '消费引用暂不可读或无权限，请恢复后重试。' }); });
    return () => { cancelled = true; };
  }, [scope, refresh]);
  if (!publication) return null;
  const current = result?.scope === scope ? result : undefined;
  const links = current?.usages.flatMap(usage => {
    const path = metricConsumptionPath(metricId, publication.metricVersion, usage);
    return path ? [{ usage, path }] : [];
  }) ?? [];
  return <div className="mt-4" aria-label="继续消费已发布指标">
    <Alert type="info" message={`继续消费已发布版本 v${publication.metricVersion}`}
      description="以下入口来自已登记的精确版本引用。消费页会重新核对产品与访问权限；实际查询及运行证据由消费产品提供。" />
    <Space wrap className="mt-2">
      <Button onClick={() => setRefresh(value => value + 1)}>刷新消费引用</Button>
      {links.map(({ usage, path }) => <Button key={usage.id} onClick={() => navigate(path)}>
        核对数据集：{usage.usageName || usage.usageId}
      </Button>)}
    </Space>
    <p>{!current ? '正在读取消费引用…' : current.error || (!links.length
      ? '尚无可映射到该发布版本的消费目标。请先在 Dataset 原页面登记引用，再刷新。' : '')}</p>
    {!!current?.usages.some(usage => !metricConsumptionPath(metricId, publication.metricVersion, usage)) &&
      <p>其余引用属于其他版本、版本未知或尚无稳定消费映射；请在使用情况中核对。</p>}
  </div>;
}
