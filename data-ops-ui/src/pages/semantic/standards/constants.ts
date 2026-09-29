import type { SemanticStandardRecord } from '@/services/semantic/types';

/** 类别专有字段的展示名(详情抽屉中仅展示非空项)。 */
export const STANDARD_KIND_FIELD_LABELS: readonly {
  key: keyof SemanticStandardRecord;
  label: string;
}[] = [
  { key: 'scope', label: '适用范围' },
  { key: 'layer', label: '适用分层' },
  { key: 'ruleExpr', label: '规则表达式' },
  { key: 'example', label: '示例' },
  { key: 'typeCode', label: '类型编码' },
  { key: 'stdType', label: '标准类型' },
  { key: 'sourceMapping', label: '源库类型映射' },
  { key: 'codeSetCode', label: '码集编码' },
  { key: 'codeValue', label: '码值' },
  { key: 'codeLabel', label: '码值标签' },
  { key: 'unitCode', label: '单位编码' },
  { key: 'unitType', label: '单位类型' },
  { key: 'caliberCode', label: '口径编码' },
  { key: 'calRule', label: '口径规则' },
  { key: 'businessDesc', label: '业务说明' },
  { key: 'levelCode', label: '等级编码' },
  { key: 'maskRule', label: '脱敏规则' },
];
