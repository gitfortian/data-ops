import HttpUtils from '@/utils/HttpUtils';
import type { MetricFact } from '@/services/agent/metricExplanation';

export interface MetricExplanationContext { versionId: number; metricId: number; version: number; definition: string; facts: MetricFact[] }
export const getMetricExplanationContext = (id: number, version: number, snapshot = false) =>
  HttpUtils.getData<MetricExplanationContext>(`/api/v1/metrics/${id}/versions/${version}/${snapshot ? 'snapshot-explanation-context' : 'explanation-context'}`);
