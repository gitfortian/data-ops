import HttpUtils from '@/utils/HttpUtils';
import type { MetricFact } from '@/services/agent/metricExplanation';

export interface MetricExplanationContext { versionId: number; metricId: number; version: number; definition: string; facts: MetricFact[] }
export const getMetricExplanationContext = (id: number, version: number) =>
  HttpUtils.getData<MetricExplanationContext>(`/api/v1/metrics/${id}/versions/${version}/explanation-context`);
