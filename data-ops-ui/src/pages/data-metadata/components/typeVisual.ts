import type { EntityTypeView, MetadataSearchItem } from '@/services/metadata/types';

/** 治理状态字面量（后端 MetadataEntityStatus 的 value 面,含空格的值照原样当契约用）。 */
export const ENTITY_STATUS_OPTIONS = [
  'Draft',
  'In Review',
  'Approved',
  'Archived',
  'Deprecated',
  'Rejected',
  'Unprocessed',
].map((value) => ({ value, label: value }));

export const PROVIDER_OPTIONS = [
  { value: 'HARVESTED', label: '物理采集' },
  { value: 'REGISTERED', label: '投影对账' },
];

/** 类型未配 color 时的兜底色板：按类型名稳定散列,同一类型的颜色在列表/徽标/抽屉里始终一致。 */
const FALLBACK_COLORS = ['#2f6fed', '#7a5af8', '#0e9f6e', '#c2410c', '#be3455', '#0a7c96'];

const hashKey = (key: string): number => {
  let hash = 0;
  for (let i = 0; i < key.length; i += 1) hash = (hash * 31 + key.charCodeAt(i)) | 0;
  return Math.abs(hash);
};

export interface TypeVisual {
  label: string;
  color: string;
}

/** typeName → 展示令牌：label 出自 type_def.display_name,前端不写死类型清单。 */
export const resolveTypeVisual = (
  types: EntityTypeView[],
  typeName?: string | null,
): TypeVisual => {
  const key = typeName ?? '';
  const type = types.find((item) => item.typeName === key);
  const color = type?.color?.trim();
  return {
    label: type?.displayName || key || '未知类型',
    color: color || FALLBACK_COLORS[hashKey(key) % FALLBACK_COLORS.length],
  };
};

export const assetTitle = (item: MetadataSearchItem): string =>
  item.displayName || item.name || item.columnName || item.tableName || item.assetKey;

/** 副行：物理侧给「库 · schema · 表 · 列」,投影侧回落到 FQN。 */
export const assetSubtitle = (item: MetadataSearchItem): string => {
  const path = [item.databaseName, item.schemaName, item.tableName, item.columnName].filter(
    (part): part is string => Boolean(part),
  );
  return path.length > 0 ? path.join(' · ') : item.fullyQualifiedName || item.assetKey;
};

/** 属性的值面：JSON 标量直出,对象/数组压成一行人能读的短串,不让 [object Object] 上生产。 */
export const describeAttributeValue = (value: unknown): string => {
  if (value == null) return '-';
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.length > 0 ? value.map((item) => describeAttributeValue(item)).join('、') : '-';
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};
