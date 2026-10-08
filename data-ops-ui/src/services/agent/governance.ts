/** Context is a source selection only; the backend resolves all facts and permissions. */
export interface StandardMatchTarget { modelId: number; columnName: string; dataType: string; businessDescription: string; keyword: string }
export type GovernanceTarget = { assetId: number; qualityExecutionNo?: never; qualityMonitorId?: never; purpose?: 'ASSET_DESCRIPTION'; standardMatch?: never }
  | { assetId?: never; qualityExecutionNo: string; qualityMonitorId?: never; purpose?: never; standardMatch?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityMonitorId: number; purpose: 'QUALITY_RULES'; standardMatch?: never }
  | { assetId?: never; qualityExecutionNo?: never; qualityMonitorId?: never; purpose: 'STANDARD_MATCH'; standardMatch: StandardMatchTarget };

export function governanceEntryPath(target: GovernanceTarget): string {
  if (target.standardMatch) return '/ai-agent';
  if (target.qualityMonitorId !== undefined) return `/ai-agent?qualityMonitorId=${target.qualityMonitorId}`;
  const query = target.assetId !== undefined
    ? `assetId=${target.assetId}`
    : `qualityExecutionNo=${encodeURIComponent(target.qualityExecutionNo)}`;
  return `/ai-agent?${query}`;
}

export function parseGovernanceTarget(search: string): GovernanceTarget | null {
  const params = new URLSearchParams(search);
  const asset = params.get('assetId');
  const execution = params.get('qualityExecutionNo');
  const monitor = params.get('qualityMonitorId');
  if (monitor) {
    return !asset && !execution && /^[1-9]\d*$/.test(monitor) && Number.isSafeInteger(Number(monitor))
      ? { qualityMonitorId: Number(monitor), purpose: 'QUALITY_RULES' } : null;
  }
  if (!!asset === !!execution) return null;
  if (asset && /^[1-9]\d*$/.test(asset) && Number.isSafeInteger(Number(asset))) {
    return { assetId: Number(asset) };
  }
  if (execution && /^[A-Za-z0-9_-]{1,128}$/.test(execution)) return { qualityExecutionNo: execution };
  return null;
}

export function governanceSourcePath(target: GovernanceTarget): string {
  if (target.standardMatch) return `/modeling/models/${target.standardMatch.modelId}`;
  if (target.qualityMonitorId !== undefined) return `/data-quality/monitor/${target.qualityMonitorId}`;
  return target.assetId !== undefined ? `/data-asset/detail/${target.assetId}`
    : `/data-quality/execution/${encodeURIComponent(target.qualityExecutionNo)}`;
}

export function governanceQuestions(target: GovernanceTarget): string[] {
  if (target.standardMatch) return ['根据绑定的未保存字段草稿匹配类型标准；信息不足时列出待确认项。'];
  if (target.qualityMonitorId !== undefined) return ['根据当前字段与模板给出质量规则候选；缺业务阈值请先确认。'];
  if (target.purpose === 'ASSET_DESCRIPTION') return ['根据当前资产与字段证据给出资产描述候选；缺业务背景请先确认。'];
  return target.assetId !== undefined
    ? ['解释这个资产的含义、负责人和治理状态，并引用证据。', '这个资产有哪些已证实的治理问题？区分缺失证据和待验证假设。']
    : ['请围绕本次质量执行，区分未通过、执行异常和未执行规则，核验关键状态与实际值/期望值；按本次事实、关注规则、待验证假设与缺口、人工检查步骤、源页面下一步给出排查指引。', '针对本次质量排查，还缺哪些证据或业务背景？请区分源字段缺失与需要我补充的信息，必要时先问我；不要确认尚未验证的根因。'];
}
