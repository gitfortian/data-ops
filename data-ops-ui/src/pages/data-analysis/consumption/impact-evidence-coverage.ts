import type { ConsumerImpact } from '@/services/consumption';

/** Interpret bounded evidence without treating an exhausted window as a broken provider. */
export const impactEvidenceGaps = (impact: ConsumerImpact): string[] => {
  const gaps: string[] = [];
  if (impact.subscriptionState === 'UNAVAILABLE' || impact.subscriptionState === 'FORBIDDEN') {
    gaps.push('Subscription 来源 ' + impact.subscriptionState + '：向来源 Owner 补核声明依赖');
  }
  if (impact.usageState === 'UNAVAILABLE' || impact.usageState === 'FORBIDDEN') {
    gaps.push('Usage 证据 ' + impact.usageState + '：向来源 Owner 核对失败或缺失的消费记录');
  }
  const coverage = impact.coverage;
  if (coverage?.sourceReadUnavailable) {
    gaps.push('来源审计读取失败：排查来源系统/权限并重试，已归一化的历史证据不能当作最新全集');
  }
  if (coverage?.normalizationGapCount) {
    gaps.push('来源证据归一化有 ' + coverage.normalizationGapCount
      + ' 条缺口：排查缺失身份、精确版本或来源信息');
  }
  if (coverage?.sourceWindowLimitReached) {
    gaps.push('来源审计查询已达到 ' + coverage.requestedUsageLimit
      + ' 条窗口上限：继续核查更早历史与未覆盖的 Consumer');
  }
  if (coverage?.normalizedUsageWindowLimitReached) {
    gaps.push('归一化成功使用证据已达到 ' + coverage.requestedUsageLimit
      + ' 条窗口上限：继续核查窗口外的 Consumer 和版本');
  }
  return gaps;
};

export const impactEvidenceIncomplete = (impact: ConsumerImpact): boolean =>
  impactEvidenceGaps(impact).length > 0;

/** Numbers are diagnostic for this request only, not all-time totals. */
export const impactEvidenceWindowFacts = (impact: ConsumerImpact): string[] => {
  const coverage = impact.coverage;
  if (!coverage) return ['来源窗口指标未提供（兼容旧版响应）；不得推断已覆盖全部历史'];
  return [
    '本次请求 Usage 上限：' + coverage.requestedUsageLimit,
    '本次来源审计读取条数：' + coverage.sourceRecordCount,
    '本次归一化 Usage 返回条数：' + coverage.normalizedUsageCount,
    '来源窗口达到上限：' + (coverage.sourceWindowLimitReached ? '是' : '否'),
    '归一化窗口达到上限：' + (coverage.normalizedUsageWindowLimitReached ? '是' : '否'),
    '归一化缺口条数：' + coverage.normalizationGapCount,
    '来源审计读取失败：' + (coverage.sourceReadUnavailable ? '是' : '否'),
  ];
};
