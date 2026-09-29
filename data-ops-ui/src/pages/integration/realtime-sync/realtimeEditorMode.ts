import type { CdcPipelineSpec } from './types';

export type RealtimeEditorMode = 'wizard' | 'yaml';

export interface WizardCompatibility {
  supported: boolean;
  reason?: string;
}

/**
 * The wizard is intentionally a safe subset of CdcPipelineSpec. Keep this check centralized so
 * switching editor modes never drops configuration that the wizard cannot represent.
 */
export const wizardCompatibility = (spec?: CdcPipelineSpec): WizardCompatibility => {
  if (!spec) return { supported: true };

  const unsupportedRoutes = spec.tables.filter((route) => route.matchMode !== 'EXACT');
  if (unsupportedRoutes.length > 0) {
    const examples = unsupportedRoutes
      .slice(0, 3)
      .map((route) => route.sourceTable)
      .join('、');
    const suffix = unsupportedRoutes.length > 3 ? ` 等 ${unsupportedRoutes.length} 条` : '';
    return {
      supported: false,
      reason: `当前配置包含向导模式暂不支持的 REGEX 表规则：${examples}${suffix}。请继续使用 YAML 模式编辑。`,
    };
  }

  return { supported: true };
};
