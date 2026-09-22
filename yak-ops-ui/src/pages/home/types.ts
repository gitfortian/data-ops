import type { HomeDataCenterPeriod } from '@/services/home';

export type HomeDataCenterTabKey = 'overview' | 'recent' | 'schedule';
export type HomeDataCenterPeriodKey = HomeDataCenterPeriod;

export interface HomeOverviewMetric {
  label: string;
  value: string;
  compareLabel: string;
  compareValue: string;
  tone?: 'positive' | 'negative' | 'neutral';
}
