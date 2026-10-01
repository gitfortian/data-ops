export const PROJECT_ID_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
export const CURRENT_PROJECT_STORAGE_KEY = 'yak-security.current-project-id';

export type ProjectMigrationMode =
  | 'LEGACY_GLOBAL'
  | 'PROJECT_OPTIONAL'
  | 'PROJECT_REQUIRED';

export type ProjectRequestRule = {
  prefix: string;
  mode: ProjectMigrationMode;
};

/** Project-aware API rollout table kept in lockstep with backend @ProjectScope adoption. */
export const PROJECT_REQUEST_RULES: readonly ProjectRequestRule[] = [
  // Connector/plugin definitions are platform capabilities shared by every workspace.
  { prefix: '/api/v1/data-source/plugin/config', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/data-source', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/sql-executions', mode: 'PROJECT_REQUIRED' },
  // Storage plugin metadata is platform-global; resource namespace/content is workspace-owned.
  { prefix: '/api/v1/resources/storage-plugins', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/resources', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/datasets', mode: 'PROJECT_REQUIRED' },
  // BI definition planes are Project-owned; published snapshots remain authenticated management APIs.
  { prefix: '/api/v1/analyses', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/dashboards', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/digital-screens', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/home', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/data-development', mode: 'PROJECT_REQUIRED' },
  // Warehouse modeling models are Project-owned assets.
  { prefix: '/api/v1/modeling', mode: 'PROJECT_REQUIRED' },
  // Business-semantic standards/domain/process/layer are Project-owned catalogs.
  { prefix: '/api/v1/semantic', mode: 'PROJECT_REQUIRED' },
  // Data Service management is project-scoped, but the external runtime URL is intentionally
  // global and protected by the published NONE/API_KEY contract rather than Yak console headers.
  { prefix: '/api/v1/data-service/runtime', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/data-service', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/consumption', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/task-catalog', mode: 'PROJECT_OPTIONAL' },
  // Generic workflow task discovery includes Project-owned providers such as Offline Sync.
  { prefix: '/api/v1/tasks', mode: 'PROJECT_REQUIRED' },
  // Link-Up engine health is platform-global; Offline definitions and runtime facts are Project data.
  { prefix: '/api/v1/job/batch-execution/health', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/executor/health', mode: 'LEGACY_GLOBAL' },
  // Connector catalogue and its schema are engine capabilities, same as the health probe.
  { prefix: '/api/v1/job/batch-execution/connectors', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/job/batch-definition', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/job/batch-execution', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/job/batch-instance', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/job/batch-control', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/executor', mode: 'PROJECT_REQUIRED' },
  // Flink compute environments are platform runtime capabilities; Realtime jobs are Project-owned.
  { prefix: '/api/v1/compute-environments', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/realtime-sync', mode: 'PROJECT_REQUIRED' },
  { prefix: '/api/v1/workflows', mode: 'PROJECT_REQUIRED' },
  // Rule templates remain platform-global; monitor, execution and reports are Project-owned.
  { prefix: '/api/v1/data-quality/template', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/data-quality', mode: 'PROJECT_REQUIRED' },
  // Master data entities/attributes/records/clean/distribution/approval are Project-owned.
  { prefix: '/api/v1/mdm', mode: 'PROJECT_REQUIRED' },
  // Metric catalog, lineage, usage and impact analysis are Project-owned.
  { prefix: '/api/v1/metrics', mode: 'PROJECT_REQUIRED' },
  // Data-security classification/access/masking/audit/compliance are Project-owned.
  { prefix: '/api/v1/data-security', mode: 'PROJECT_REQUIRED' },
  // TTL policies/model bindings/dispatch records for data lifecycle are Project-owned.
  { prefix: '/api/v1/lifecycle', mode: 'PROJECT_REQUIRED' },
  // Asset ledger/directories/rules/inventory are Project-owned.
  { prefix: '/api/v1/assets', mode: 'PROJECT_REQUIRED' },
  // The metamodel (entity types, fields, slot board) is a platform capability shared by every
  // workspace; catalog/search/collect under the same prefix are Project-owned data.
  { prefix: '/api/v1/metadata/types', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/metadata/slots', mode: 'LEGACY_GLOBAL' },
  { prefix: '/api/v1/metadata', mode: 'PROJECT_REQUIRED' },
  // Conversational agent sessions, turns, reports and skills are Project-owned.
  { prefix: '/api/v1/agent', mode: 'PROJECT_REQUIRED' },
  // Approval flows/instances/todo are Project-owned.
  { prefix: '/api/v1/approvals', mode: 'PROJECT_REQUIRED' },
];

const normalizePath = (url: string): string => {
  try {
    return new URL(url, 'http://yak-ops.local').pathname.replace(/\/+$/, '') || '/';
  } catch {
    return url.split(/[?#]/, 1)[0].replace(/\/+$/, '') || '/';
  }
};

export const resolveProjectRequestMode = (
  url: string,
  rules: readonly ProjectRequestRule[] = PROJECT_REQUEST_RULES,
): ProjectMigrationMode => {
  const path = normalizePath(url);
  const matched = rules
    .filter(({ prefix }) => {
      const normalizedPrefix = normalizePath(prefix);
      return path === normalizedPrefix || path.startsWith(`${normalizedPrefix}/`);
    })
    .sort((left, right) => normalizePath(right.prefix).length - normalizePath(left.prefix).length)[0];

  return matched?.mode ?? 'LEGACY_GLOBAL';
};

const normalizeProjectId = (value: unknown): string | undefined => {
  const normalized = String(value ?? '').trim();
  if (!/^\d+$/.test(normalized) || Number(normalized) <= 0) return undefined;
  return normalized;
};

export const readStoredProjectId = (): string | undefined => {
  if (typeof window === 'undefined') return undefined;
  return normalizeProjectId(window.localStorage.getItem(CURRENT_PROJECT_STORAGE_KEY));
};

export const storeProjectId = (projectId: unknown): void => {
  if (typeof window === 'undefined') return;
  const normalized = normalizeProjectId(projectId);
  if (normalized) window.localStorage.setItem(CURRENT_PROJECT_STORAGE_KEY, normalized);
  else window.localStorage.removeItem(CURRENT_PROJECT_STORAGE_KEY);
};

export const clearStoredProjectId = (): void => {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(CURRENT_PROJECT_STORAGE_KEY);
};

export const applyCurrentProjectHeader = (
  url: string,
  headers: HeadersInit | undefined,
  projectId = readStoredProjectId(),
  rules: readonly ProjectRequestRule[] = PROJECT_REQUEST_RULES,
): HeadersInit => {
  const mode = resolveProjectRequestMode(url, rules);
  const normalizedProjectId = normalizeProjectId(projectId);
  if (mode === 'LEGACY_GLOBAL' || !normalizedProjectId) return headers ?? {};

  if (typeof Headers !== 'undefined' && headers instanceof Headers) {
    const next = new Headers(headers);
    next.set(PROJECT_ID_HEADER, normalizedProjectId);
    return next;
  }

  return {
    ...(headers ?? {}),
    [PROJECT_ID_HEADER]: normalizedProjectId,
  };
};
