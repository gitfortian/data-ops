/**
 * FE-A2: exact live source -> ODS -> live source coordinates.
 * These are URL hints only. DataSource and Modeling Owners revalidate current
 * Project, source, schema and table before reading or mutating anything.
 */
export interface LiveSourceCoordinate {
  dataSourceId: number;
  database: string;
  schema?: string;
  table: string;
}

const validPart = (value: unknown): value is string =>
  typeof value === 'string' && value.trim() === value
  && value.length > 0 && value.length <= 128
  && !/[\u0000-\u001f\u007f]/.test(value);

const validId = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value > 0;

export function isValidLiveSourceCoordinate(value: LiveSourceCoordinate): boolean {
  return validId(value.dataSourceId) && validPart(value.database)
    && validPart(value.table)
    && (value.schema === undefined || validPart(value.schema));
}

/** Only the trusted live-source origin mode controls the return destination. */
export function buildLiveSourceOdsPath(value: LiveSourceCoordinate): string | null {
  if (!isValidLiveSourceCoordinate(value)) return null;
  const q = new URLSearchParams({
    sourceJourney: 'ods', origin: 'live-source',
    datasourceId: String(value.dataSourceId),
    database: value.database, table: value.table,
  });
  if (value.schema) q.set('schema', value.schema);
  return `/modeling?${q}`;
}

export function buildLiveSourceReturnPath(value: LiveSourceCoordinate): string | null {
  if (!isValidLiveSourceCoordinate(value)) return null;
  const q = new URLSearchParams({
    sourceId: String(value.dataSourceId),
    database: value.database,
    table: value.table,
  });
  if (value.schema) q.set('schema', value.schema);
  return `/data-source?${q}`;
}

/** An invalid or partial deep link must not open a different source silently. */
export function parseLiveSourceReturn(
  q: Pick<URLSearchParams, 'get'>,
): LiveSourceCoordinate | null {
  const raw = q.get('sourceId');
  if (!raw || !/^[1-9]\d*$/.test(raw)) return null;
  const id = Number(raw);
  const database = q.get('database') ?? '';
  const table = q.get('table') ?? '';
  const schema = q.get('schema') || undefined;
  const result = { dataSourceId: id, database, schema, table };
  return isValidLiveSourceCoordinate(result) ? result : null;
}

/**
 * Model detail may show a "return to source" action only after loading the
 * persisted ODS record and matching its exact source evidence to the URL hint.
 * Legacy models without an exact Schema must not claim an exact return link.
 */
export function verifiedOdsModelSourceReturn(
  model: {
    layerCode?: string | null;
    sourceDatasourceId?: number | null;
    sourceDatabase?: string | null;
    sourceSchema?: string | null;
    sourceTable?: string | null;
  } | null | undefined,
  params: Pick<URLSearchParams, 'get'>,
): string | null {
  if (params.get('origin') !== 'live-source' || model?.layerCode !== 'ODS') return null;
  const query = new URLSearchParams({
    sourceId: params.get('datasourceId') ?? '',
    database: params.get('database') ?? '',
    table: params.get('table') ?? '',
  });
  const schema = params.get('schema');
  if (schema) query.set('schema', schema);
  const coordinate = parseLiveSourceReturn(query);
  if (!coordinate
      || model.sourceDatasourceId !== coordinate.dataSourceId
      || model.sourceDatabase !== coordinate.database
      || (model.sourceSchema ?? undefined) !== coordinate.schema
      || model.sourceTable !== coordinate.table) return null;
  return buildLiveSourceReturnPath(coordinate);
}
