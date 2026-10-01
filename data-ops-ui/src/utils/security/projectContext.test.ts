import {
  PROJECT_ID_HEADER,
  applyCurrentProjectHeader,
  clearStoredProjectId,
  readStoredProjectId,
  resolveProjectRequestMode,
  storeProjectId,
  type ProjectRequestRule,
} from './projectContext';

const rules: ProjectRequestRule[] = [
  { prefix: '/api/v1/data-source', mode: 'PROJECT_OPTIONAL' },
  { prefix: '/api/v1/data-source/admin', mode: 'PROJECT_REQUIRED' },
];

describe('Project Space request context', () => {
  afterEach(() => clearStoredProjectId());

  it('requires a project for completed project planes', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-source/1')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-source/catalog/1/tables')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/sql-executions/page')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/resources/tree')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/resources/42/download')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/datasets/1')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/analyses/1')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/dashboards/1/versions')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/digital-screens/1/published')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/home/cockpit')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/home/data-center/overview?period=7d')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-development/nodes')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-service')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-service/7')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-service/overview')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/consumption/products')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/task-catalog/assets')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/tasks')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/job/batch-definition/page')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/job/batch-execution/11/execute')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/job/batch-instance/11')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/job/batch-control/executions/11/events')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/executor/batch-execute')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/realtime-sync/11')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/realtime-sync/11/events')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/workflows/definitions')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/workflows/instances')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/workflows/schedules')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/workflows/instances/run-1/events')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-quality/table-asset/page')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-quality/monitor/42')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-quality/execution/page')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-quality/overview')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/assets/page')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/assets/directories/tree')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/assets/rules/preview')).toBe('PROJECT_REQUIRED');
    // Approval center controllers are PROJECT_REQUIRED; a missing rule means whole-module 999.
    expect(resolveProjectRequestMode('/api/v1/approvals/todo')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/approvals/flows')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/approvals/1/approve')).toBe('PROJECT_REQUIRED');
    // Missing rule = every metadata endpoint answers 999, so assert the whole prefix not one path.
    expect(resolveProjectRequestMode('/api/v1/metadata/search?q=id')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/metadata/collect-jobs/7/run')).toBe('PROJECT_REQUIRED');
    // Agent had no rule at all while its controllers are PROJECT_REQUIRED: every call 999'd.
    expect(resolveProjectRequestMode('/api/v1/agent/sessions')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/agent/chat/turns')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/agent/skills/3/active')).toBe('PROJECT_REQUIRED');
  });

  it('keeps platform capabilities, public runtimes and engine health global', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source/plugin/config?pluginType=MYSQL')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-source/plugin/config/install')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/resources/storage-plugins')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-service/runtime/orders')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-service/runtime/orders/by-id')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/job/batch-execution/health')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/executor/health')).toBe('LEGACY_GLOBAL');
    // Longest-prefix must win over the broader PROJECT_REQUIRED rules of these families.
    expect(resolveProjectRequestMode('/api/v1/job/batch-execution/connectors')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/job/batch-execution/connectors/2/schema')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/metadata/types')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/metadata/types/table/fields')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/metadata/slots')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/compute-environments')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-quality/template')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-quality/template/42')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-quality/template/custom')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-quality/template/folder')).toBe('LEGACY_GLOBAL');
  });

  it('keeps unrelated global capabilities outside the rollout table', () => {
    expect(resolveProjectRequestMode('/api/v1/quality/templates')).toBe('LEGACY_GLOBAL');
    // Prefix matching is per path segment: a lookalike must not inherit the metadata rule.
    expect(resolveProjectRequestMode('/api/v1/metadata-assets/page')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/yak-ops/apix/metadata/types')).toBe('LEGACY_GLOBAL');
  });

  it('uses the most specific project-aware route rule', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source/1', rules)).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/data-source/admin/1', rules)).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-quality/template/1')).toBe('LEGACY_GLOBAL');
  });

  it('never attaches a project header to a legacy-global route', () => {
    expect(applyCurrentProjectHeader('/api/v1/compute-environments', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/data-source/plugin/config', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/resources/storage-plugins', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/data-service/runtime/orders', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/job/batch-execution/health', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/job/batch-execution/connectors', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/metadata/types', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/metadata/slots', {}, '7')).toEqual({});
    expect(applyCurrentProjectHeader('/api/v1/data-quality/template/custom', {}, '7')).toEqual({});
  });

  it('attaches the stored project to required management routes', () => {
    storeProjectId(7);
    expect(readStoredProjectId()).toBe('7');

    for (const url of [
      '/api/v1/data-source/42',
      '/api/v1/sql-executions/page',
      '/api/v1/resources/42/download',
      '/api/v1/datasets/42',
      '/api/v1/analyses/42',
      '/api/v1/dashboards/42/publish',
      '/api/v1/digital-screens/42/versions',
      '/api/v1/home/cockpit',
      '/api/v1/data-development/nodes',
      '/api/v1/data-service/7',
      '/api/v1/consumption/products',
      '/api/v1/tasks',
      '/api/v1/job/batch-definition/page',
      '/api/v1/job/batch-instance/42',
      '/api/v1/realtime-sync/42',
      '/api/v1/realtime-sync/42/observability',
      '/api/v1/workflows/definitions',
      '/api/v1/workflows/instances',
      '/api/v1/workflows/schedules',
      '/api/v1/workflows/instances/run-1/events',
      '/api/v1/data-quality/table-asset/page',
      '/api/v1/data-quality/monitor/42',
      '/api/v1/data-quality/execution/page',
      '/api/v1/data-quality/overview',
      '/api/v1/agent/sessions',
      '/api/v1/metadata/catalog/tree',
    ]) {
      expect(applyCurrentProjectHeader(url, {})).toEqual({ [PROJECT_ID_HEADER]: '7' });
    }
  });

  it('still attaches the stored project to optional migrated routes', () => {
    storeProjectId(7);
    expect(applyCurrentProjectHeader('/api/v1/task-catalog/assets', {})).toEqual({
      [PROJECT_ID_HEADER]: '7',
    });
  });

  it('drops invalid persisted project identifiers', () => {
    storeProjectId('not-a-project');
    expect(readStoredProjectId()).toBeUndefined();
  });
});
