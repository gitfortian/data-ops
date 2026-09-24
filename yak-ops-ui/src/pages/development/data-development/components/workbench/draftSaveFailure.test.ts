import {
  classifyDraftSaveFailure,
  rebaseDraftSavePayload,
} from './draftSaveFailure';

describe('draft save failure semantics', () => {
  it('recognizes optimistic conflicts and preserves the backend detail', () => {
    expect(
      classifyDraftSaveFailure({
        response: { status: 409 },
        data: { message: '草稿已被其他会话更新' },
      }),
    ).toEqual({
      kind: 'conflict',
      status: 409,
      detail: '草稿已被其他会话更新',
    });
  });

  it('distinguishes permission and missing-resource failures', () => {
    expect(classifyDraftSaveFailure({ response: { status: 403 } }).kind).toBe(
      'permission-denied',
    );
    expect(classifyDraftSaveFailure({ response: { status: 404 } }).kind).toBe(
      'not-found',
    );
    expect(classifyDraftSaveFailure({ response: { status: 410 } }).kind).toBe(
      'not-found',
    );
  });

  it('does not mislabel transport failures as optimistic conflicts', () => {
    expect(classifyDraftSaveFailure(new Error('Network Error'))).toEqual({
      kind: 'network',
      detail: 'Network Error',
    });
  });

  it('keeps other HTTP failures generic while retaining status and detail', () => {
    expect(
      classifyDraftSaveFailure({
        response: { status: 500 },
        data: { message: 'database unavailable' },
      }),
    ).toEqual({
      kind: 'unknown',
      status: 500,
      detail: 'database unavailable',
    });
  });

  it('rebases only the optimistic revision and keeps editor content unchanged', () => {
    const payload = {
      taskType: 'SQL' as const,
      schemaVersion: 3,
      content: 'select customer_id from ods_customer',
      configJson: '{"dataSourceId":"12"}',
      baseRevision: 4,
    };

    expect(rebaseDraftSavePayload(payload, 7)).toEqual({
      ...payload,
      baseRevision: 7,
    });
    expect(payload.baseRevision).toBe(4);
    expect(payload.content).toBe('select customer_id from ods_customer');
    expect(payload.configJson).toBe('{"dataSourceId":"12"}');
  });
});
