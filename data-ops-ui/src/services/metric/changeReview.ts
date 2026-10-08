import HttpUtils from '@/utils/HttpUtils';
import type { MetricChangeReviewSource } from '@/services/agent/metricChangeReview';

export interface MetricChangeReviewContext extends Omit<MetricChangeReviewSource, 'definition'> {
  status: 'READY' | 'NO_BASELINE' | 'UNCHANGED'; metricId: number; version: number;
  publishedVersionId: number | null; publishedVersion: number | null; publicationEventId: number | null; definition: string | null;
}
export const getMetricChangeReviewContext = (id: number, version: number) =>
  HttpUtils.getData<MetricChangeReviewContext>(`/api/v1/metrics/${id}/versions/${version}/change-review-context`);
