import { inspectTaskRevisions, observeTaskExecutions } from './taskDeliveryEvidence';
import type {
  DevelopmentTaskExecutionPage, DevelopmentTaskExecutionSummary,
  DevelopmentTaskRevisionSummary,
} from '@/services/data-development';

const rev = { id: 'version-1', nodeId: '12', revisionNo: 1,
  checksum: 'a'.repeat(64), sourceDraftRevision: 2 } as DevelopmentTaskRevisionSummary;
const run = { id: '43', nodeId: '12', taskName: '订单清洗', taskType: 'SQL', status: 'SUCCESS',
  schemaVersion: 1, triggerType: 'MANUAL', startTime: '2026-10-11T10:00:00' } as DevelopmentTaskExecutionSummary;
const page: DevelopmentTaskExecutionPage = { records: [run], pageNo: 1, pageSize: 100, total: 1 };

describe('F-040 C5 DataDev task revision vs runtime evidence', () => {
  it('does not mistake no published revision for no draft or no execution', () => {
    expect(inspectTaskRevisions('12', [])).toEqual({
      state: 'NO_PUBLISHED_REVISION', revisions: [],
    });
    expect(inspectTaskRevisions('12', [rev]).state).toBe('PUBLISHED_REVISION_EXISTS');
    const rs = inspectTaskRevisions('12', [rev, { ...rev, id: 'version-3', revisionNo: 3 }]);
    expect(rs.state).toBe('PUBLISHED_REVISION_EXISTS');
    if (rs.state === 'PUBLISHED_REVISION_EXISTS') expect(rs.revisions[0].revisionNo).toBe(3);
  });

  it('does not allow foreign revision, duplicate version or missing identity', () => {
    expect(inspectTaskRevisions('12', [{ ...rev, nodeId: '99' }]).state).toBe('INVALID_OWNER');
    expect(inspectTaskRevisions('12', [rev, rev]).state).toBe('INVALID_OWNER');
    expect(inspectTaskRevisions('12', [{ ...rev, revisionNo: 0 }]).state).toBe('INVALID_OWNER');
    expect(inspectTaskRevisions('12', [{ ...rev, id: '' }]).state).toBe('INVALID_OWNER');
  });

  it('only displays true nodeId and taskType matches, retaining success as run-only fact', () => {
    const evidence = observeTaskExecutions('12', { ...page, records: [
      { ...run, nodeId: '123', id: '44' }, // exact scope cannot prefix-match
      { ...run, taskType: 'PYTHON', id: '45' },
      run,
      { ...run, id: '46', status: 'FAILED', startTime: '2026-10-11T11:00:00' },
    ], total: 4 });
    expect(evidence.state).toBe('OBSERVED');
    if (evidence.state === 'OBSERVED') {
      expect(evidence.matching.map(row => row.id)).toEqual(['46', '43']);
      expect(evidence.complete).toBe(true);
      expect(evidence.matching[1].status).toBe('SUCCESS');
    }
  });

  it('never interprets no match in a bounded recent page as no historic execution', () => {
    const current = observeTaskExecutions('12', { ...page, records: [{ ...run, nodeId: '99' }], total: 1000 });
    expect(current.state).toBe('NOT_OBSERVED_IN_WINDOW');
    if (current.state === 'NOT_OBSERVED_IN_WINDOW') {
      expect(current.matching).toEqual([]);
      expect(current.complete).toBe(false);
      expect(current.scanned).toBe(1);
    }
  });

  it('rejects broken pagination and missing execution identity rather than saying no runs', () => {
    const inputs = [
      { ...page, pageNo: 2 },
      { ...page, pageSize: 0 },
      { ...page, total: 0 },
      { ...page, records: [{ ...run, id: '' }] },
      { ...page, records: [{ ...run, status: '' }] },
    ];
    inputs.forEach(value => expect(observeTaskExecutions('12', value).state).toBe('INVALID_PAGE'));
  });
});
