export { PageHeader, StatCard } from '@/components/ui/PagePresentation';

export const LEVEL_RANK_COLORS = [
  '#52c41a',
  '#1677ff',
  '#faad14',
  '#fa8c16',
  '#f5222d',
];

/** 按序位取色（0 基）。 */
export const rankColor = (rank?: number) =>
  LEVEL_RANK_COLORS[Math.max(0, (rank ?? 1) - 1) % LEVEL_RANK_COLORS.length];
