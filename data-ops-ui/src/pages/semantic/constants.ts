import type { SemanticStandardKind, SemanticStandardStatus } from '@/services/semantic/types';

/** 六类标准的展示名与标签色(ticket 30)。 */
export const SEMANTIC_STANDARD_KINDS: readonly {
  value: SemanticStandardKind;
  label: string;
  color: string;
}[] = [
  { value: 'NAMING', label: '命名标准', color: 'geekblue' },
  { value: 'TYPE', label: '类型标准', color: 'cyan' },
  { value: 'CODE', label: '码值标准', color: 'orange' },
  { value: 'UNIT', label: '单位标准', color: 'purple' },
  { value: 'CALIBER', label: '口径标准', color: 'green' },
  { value: 'SECURITY', label: '安全标准', color: 'red' },
];

export const SEMANTIC_STANDARD_KIND_LABELS: Record<SemanticStandardKind, string> = Object.fromEntries(
  SEMANTIC_STANDARD_KINDS.map((item) => [item.value, item.label]),
) as Record<SemanticStandardKind, string>;

export const SEMANTIC_STANDARD_KIND_COLORS: Record<SemanticStandardKind, string> = Object.fromEntries(
  SEMANTIC_STANDARD_KINDS.map((item) => [item.value, item.color]),
) as Record<SemanticStandardKind, string>;

export const SEMANTIC_STATUS_LABELS: Record<SemanticStandardStatus, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
};

export const SEMANTIC_STATUS_COLORS: Record<SemanticStandardStatus, string> = {
  ENABLED: 'green',
  DISABLED: 'default',
};

export const formatSemanticTime = (value?: string): string => {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
};
