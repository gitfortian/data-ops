import { buildSavePayload, normalizeEditDetail } from './model';

/** Link-Up owns worker dispatch; legacy UI placement fields must not become another scheduler truth. */
test.each(['AUTO', 'MANUAL'])('ignores legacy %s worker placement while preserving the job definition', mode => {
  const editor = normalizeEditDetail({
    id: '1001', basic: { jobName: '历史任务', jobDesc: '升级兼容', mode: 'GUIDE_SINGLE' },
    workflow: { nodes: [], edges: [] },
    worker: { mode, nodeId: 'legacy-worker', requiredLabels: { region: 'south' } },
  }, '1001');
  const payload = buildSavePayload(editor);
  expect(editor.basic.jobName).toBe('历史任务');
  expect(payload.basic.jobDesc).toBe('升级兼容');
  expect(editor).not.toHaveProperty('worker');
  expect(payload).not.toHaveProperty('worker');
});
