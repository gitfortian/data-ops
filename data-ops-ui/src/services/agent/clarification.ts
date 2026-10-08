export interface QueryClarificationContext {
  kind: 'FIELD' | 'TIME' | 'CALIBER';
  datasetId: number;
  versionNo: number;
  truncated: boolean;
  fields: Array<{ fieldId: string; displayName: string; dataType: string; role: string; description: string }>;
}

export interface ClarificationQuestion {
  question: string;
  options: string[];
  queryContext?: QueryClarificationContext;
}

function bounded(value: unknown, max: number, optional = false): value is string {
  return typeof value === 'string' && value.length <= max && (optional || !!value.trim());
}

/** The same bounded projection is used for live events and persisted continuation. */
export function readClarificationQuestion(raw: string): ClarificationQuestion {
  if (!bounded(raw, 12000)) throw new Error('待答问题无法核对');
  if (!/^[{[]/.test(raw.trim())) return { question: raw, options: [] };
  const value: unknown = JSON.parse(raw);
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('待答问题无效');
  const item = value as Record<string, unknown>;
  if (!bounded(item.question, 2048)) throw new Error('待答问题无效');
  const options = item.options ?? [];
  if (!Array.isArray(options) || options.length > 8 || options.some(v => !bounded(v, 512))
    || new Set(options).size !== options.length) throw new Error('待答选项无效');
  const result: ClarificationQuestion = { question: item.question, options };
  if (item.queryContext == null) return result;
  if (typeof item.queryContext !== 'object' || Array.isArray(item.queryContext)) throw new Error('字段依据无效');
  const context = item.queryContext as Record<string, unknown>;
  if (!['FIELD', 'TIME', 'CALIBER'].includes(String(context.kind))
    || ![context.datasetId, context.versionNo].every(v => Number.isSafeInteger(v) && Number(v) > 0)
    || typeof context.truncated !== 'boolean' || !Array.isArray(context.fields)
    || context.fields.length < (context.kind === 'FIELD' ? 2 : 1) || context.fields.length > 8) throw new Error('字段依据无效');
  const fields: QueryClarificationContext['fields'] = context.fields.map((rawField: unknown) => {
    if (!rawField || typeof rawField !== 'object' || Array.isArray(rawField)) throw new Error('字段依据无效');
    const field = rawField as Record<string, unknown>;
    if (!bounded(field.fieldId, 128) || !bounded(field.displayName, 256, true)
      || !bounded(field.dataType, 64) || !bounded(field.role, 32) || !bounded(field.description, 512, true)) throw new Error('字段依据无效');
    return { fieldId: field.fieldId, displayName: field.displayName, dataType: field.dataType, role: field.role, description: field.description };
  });
  if (new Set(fields.map(f => f.fieldId)).size !== fields.length) throw new Error('字段依据重复');
  if (context.kind === 'FIELD' && (options.length !== fields.length || fields.some((f, i) =>
    options[i] !== `${f.displayName.trim() ? f.displayName : f.fieldId}（fieldId=${f.fieldId}）`))) throw new Error('字段选项无法核对');
  result.queryContext = { kind: context.kind as QueryClarificationContext['kind'], datasetId: Number(context.datasetId),
    versionNo: Number(context.versionNo), truncated: context.truncated, fields };
  return result;
}
