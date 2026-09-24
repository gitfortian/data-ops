import { Button, Tooltip } from 'antd';
import { history, useParams } from '@umijs/max';
import { Check, Minus, TriangleAlert } from 'lucide-react';
import { useEffect, useState } from 'react';

import { resolveProductFromAsset } from '@/services/consumption';
import type {
  AssetSection,
  AssetStatusFlowData,
  StatusFlowStep,
} from '@/services/data-asset/types';

const RESULT_STYLE: Record<StatusFlowStep['result'], { color: string; bg: string }> = {
  PASS: { color: '#52c41a', bg: 'rgba(82,196,26,0.10)' },
  FAIL: { color: '#f5222d', bg: 'rgba(245,34,45,0.08)' },
  NA: { color: '#98a2b3', bg: 'rgba(152,162,179,0.10)' },
  UNKNOWN: { color: '#fa8c16', bg: 'rgba(250,140,22,0.10)' },
};

const FACT_LABELS: Record<string, string> = {
  assetKey: '账本键',
  missing: '缺失项',
  layerCode: '分层',
  stdMandatory: '定标强制',
  columnTotal: '字段总数',
  stdBoundColumns: '已落标字段',
  monitorId: '监控',
  monitorCount: '监控数',
  lastResult: '最近结果',
  lastRunTime: '最近执行',
  securityLevelCode: '安全等级',
  status: '台账状态',
  approval: '上架审批单',
  firstListedAt: '首次上架',
  lastListedAt: '最近上架',
  downstreamCount: '下游引用',
  policyCode: 'TTL 策略',
  bindingSource: '策略来源',
  state: '下发状态',
};

const formatFacts = (facts?: Record<string, unknown>) => {
  const entries = Object.entries(facts ?? {}).filter(([, value]) => value !== null && value !== undefined);
  if (entries.length === 0) return null;
  return (
    <div className="text-[12px] leading-5">
      {entries.map(([key, value]) => (
        <div key={key}>
          {FACT_LABELS[key] ?? key}:{Array.isArray(value) ? value.join('、') : String(value)}
        </div>
      ))}
    </div>
  );
};

/** 资产状态条(M2-1 只读):七格 = 各域盖章事实的达成进度,并提供稳定 ProductKey 消费入口。 */
const StatusFlowStrip = ({ section }: { section?: AssetSection<AssetStatusFlowData> }) => {
  const { id } = useParams<{ id: string }>();
  const [canonicalHref, setCanonicalHref] = useState<string>();
  const steps = section?.status === 'OK' ? section.data?.steps ?? [] : [];

  useEffect(() => {
    let active = true;
    if (!id) {
      setCanonicalHref(undefined);
      return () => { active = false; };
    }
    void resolveProductFromAsset(id)
      .then((resolution) => {
        if (!active) return;
        setCanonicalHref(
          resolution.state === 'FOUND' && resolution.canonicalHref
            ? resolution.canonicalHref
            : undefined,
        );
      })
      .catch(() => {
        if (active) setCanonicalHref(undefined);
      });
    return () => { active = false; };
  }, [id]);

  if (steps.length === 0 && !canonicalHref) return null;
  return (
    <div className="mt-4 flex flex-wrap items-center gap-2 rounded-lg border border-[#f0f0f0] bg-[#fafafa] p-3">
      <div className="flex flex-1 flex-wrap items-center gap-2">
        {steps.map((step, index) => {
          const style = RESULT_STYLE[step.result] ?? RESULT_STYLE.UNKNOWN;
          return (
            <div key={step.key} className="flex items-center gap-2">
              {index > 0 && <span className="text-[12px] text-[#c4c9d4]">→</span>}
              <Tooltip
                title={(
                  <div>
                    {step.note && <div className="mb-1">{step.note}</div>}
                    {formatFacts(step.facts)}
                  </div>
                )}
              >
                <div
                  className="flex cursor-default items-center gap-1.5 rounded-full px-3 py-1"
                  style={{ background: style.bg }}
                >
                  <span style={{ color: style.color }}>
                    {step.result === 'PASS'
                      ? <Check size={14} strokeWidth={2.4} />
                      : step.result === 'FAIL'
                        ? <TriangleAlert size={14} strokeWidth={2} />
                        : <Minus size={14} strokeWidth={2} />}
                  </span>
                  <span className="text-[13px] font-medium" style={{ color: style.color }}>
                    {step.title}
                  </span>
                </div>
              </Tooltip>
            </div>
          );
        })}
      </div>
      {canonicalHref ? (
        <Button size="small" type="link" onClick={() => history.push(canonicalHref)}>
          消费视图
        </Button>
      ) : null}
    </div>
  );
};

export default StatusFlowStrip;
