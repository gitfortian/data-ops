/**
 * The source stores invocation IDs as SQL BIGINT. Evidence JSON parsing keeps
 * values > Number.MAX_SAFE_INTEGER as strings, and comparison must stay exact.
 * No invocation should be attributed to a different source revision merely
 * because the exact matching record was not found in the bounded log window.
 */
function stableId(value) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'number') {
    if (!Number.isSafeInteger(value) || value <= 0) {
      throw new Error('Invocation audit contains an unsafe numeric identity');
    }
    return BigInt(value);
  }
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) return BigInt(value);
  throw new Error('Invocation audit contains a non-decimal stable identity');
}

export function maxRecordId(records) {
  return records.reduce((highest, record) => {
    const id = stableId(record?.id);
    return id !== null && id > highest ? id : highest;
  }, 0n);
}

export function findNewInvocation(records, previousMaxId, service, apiId) {
  const cutoff = typeof previousMaxId === 'bigint'
    ? previousMaxId : stableId(previousMaxId) ?? 0n;
  const candidates = records
    .filter((record) => {
      const id = stableId(record?.id);
      return id !== null && id > cutoff;
    })
    .filter((record) => String(record?.apiId ?? '') === String(apiId))
    .filter((record) => record?.success === true)
    .filter((record) =>
      String(record?.sourceRevisionId ?? '') === String(service?.sourceRevisionId ?? '')
        && String(record?.sourceRevisionNo ?? '') === String(service?.sourceRevisionNo ?? ''))
    .sort((left, right) => {
      const a = stableId(left.id);
      const b = stableId(right.id);
      return a === b ? 0 : a > b ? -1 : 1;
    });
  return candidates[0] ?? null;
}
