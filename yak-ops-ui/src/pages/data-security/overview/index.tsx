import { YakButton, YakEmpty } from '@/components/ui';
import {
  getSecurityOverview,
} from '@/services/data-security/api';
import type { SecurityOverview } from '@/services/data-security/types';
import { Spin, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'umi';
import { PageHeader, StatCard, rankColor } from '../shared';

const num = (value: unknown): number => {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
};

const DataSecurityOverviewPage = () => {
  const navigate = useNavigate();
  const [data, setData] = useState<SecurityOverview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      setData(await getSecurityOverview());
    } catch {
      setError(true);
      message.error('加载数据安全总览失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const distribution = data?.levelDistribution ?? [];
  const maxCount = distribution.reduce((acc, item) => Math.max(acc, item.count), 0) || 1;
  const summary = (data?.complianceSummary ?? {}) as Record<string, unknown>;

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <PageHeader
        title="数据安全总览"
        subtitle="分级覆盖、权限策略、脱敏命中、访问审计与合规缺口的统一快照"
        extra={
          <YakButton
            className="!h-9 !rounded-lg"
            onClick={() => void load()}
            loading={loading}
          >
            刷新
          </YakButton>
        }
      />

      <Spin spinning={loading}>
        {error && !data ? (
          <div className="mt-8">
            <YakEmpty compact title="暂无数据" description="无法加载数据安全总览，请稍后重试" />
          </div>
        ) : (
          <>
            <div className="mt-5 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
              <StatCard label="安全等级" value={data?.levelCount ?? 0} onClick={() => navigate('/data-security/classification')} />
              <StatCard label="数据分类" value={data?.categoryCount ?? 0} onClick={() => navigate('/data-security/classification')} />
              <StatCard
                label="已定级资产"
                value={data?.classifiedTotal ?? 0}
                hint={`生效 ${data?.activeClassification ?? 0} · 候选 ${data?.candidateClassification ?? 0}`}
                onClick={() => navigate('/data-security/classification')}
              />
              <StatCard label="生效访问策略" value={data?.enabledPolicyCount ?? 0} onClick={() => navigate('/data-security/access')} />
              <StatCard label="脱敏策略" value={data?.maskingPolicyCount ?? 0} onClick={() => navigate('/data-security/masking')} />
              <StatCard
                label="合规缺口"
                value={num(summary.openGaps)}
                hint={num(summary.openGaps) > 0 ? `最近体检存在 ${num(summary.openGaps)} 项风险` : '暂无风险'}
                accent={num(summary.openGaps) > 0 ? '#f5222d' : '#52c41a'}
                onClick={() => navigate('/data-security/compliance')}
              />
            </div>

            <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-2">
              <section className="rounded-xl border border-solid border-[#eceef2] bg-white p-4">
                <div className="text-[15px] font-semibold">定级分布</div>
                <div className="mt-1 text-[12px] text-[#98a2b3]">各安全等级已定级的资产数量</div>
                <div className="mt-4 space-y-3">
                  {distribution.length === 0 ? (
                    <YakEmpty compact title="暂无定级数据" description="到「分级分类」为资产打级后展示" />
                  ) : (
                    distribution.map((item) => (
                      <div key={item.levelCode} className="flex items-center gap-3">
                        <div className="w-24 shrink-0 truncate text-[13px]" title={item.levelName}>
                          {item.levelName}
                        </div>
                        <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-[#f2f3f5]">
                          <div
                            className="h-full rounded-full"
                            style={{
                              width: `${Math.max(4, (item.count / maxCount) * 100)}%`,
                              background: rankColor(item.rank),
                            }}
                          />
                        </div>
                        <div className="w-12 shrink-0 text-right text-[13px] font-medium">{item.count}</div>
                      </div>
                    ))
                  )}
                </div>
              </section>

              <section className="rounded-xl border border-solid border-[#eceef2] bg-white p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-[15px] font-semibold">近期访问动态</div>
                    <div className="mt-1 text-[12px] text-[#98a2b3]">拒绝与脱敏命中趋势、访问热点</div>
                  </div>
                  <YakButton type="link" size="small" onClick={() => navigate('/data-security/audit')}>
                    查看审计
                  </YakButton>
                </div>
                <div className="mt-4 grid grid-cols-2 gap-3">
                  <StatCard label="近期拒绝" value={data?.accessDenyRecent ?? 0} accent="#f5222d" />
                  <StatCard label="近期脱敏" value={data?.accessMaskedRecent ?? 0} accent="#fa8c16" />
                </div>
                <div className="mt-4">
                  <div className="text-[13px] text-[#667085]">访问热点主体</div>
                  <div className="mt-2 space-y-2">
                    {(data?.topActors ?? []).length === 0 ? (
                      <div className="text-[13px] text-[#98a2b3]">暂无访问记录</div>
                    ) : (
                      (data?.topActors ?? []).slice(0, 6).map((actor, index) => (
                        <div
                          key={`${actor.actor ?? 'actor'}-${index}`}
                          className="flex items-center justify-between rounded-lg bg-[#f7f8fa] px-3 py-2 text-[13px]"
                        >
                          <span className="truncate">{String(actor.actor ?? actor.subject ?? '-')}</span>
                          <span className="ml-2 shrink-0 font-medium">{num(actor.count ?? actor.total)}</span>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </section>
            </div>
          </>
        )}
      </Spin>
    </div>
  );
};

export default DataSecurityOverviewPage;
