import { classifyWorkspaceLoadFailure } from './workspaceState';

describe('data-development workspace load semantics', () => {
  it('classifies forbidden catalogue reads separately from empty data', () => {
    expect(
      classifyWorkspaceLoadFailure({
        response: { status: 403 },
        data: { message: 'permission denied' },
      }),
    ).toEqual({
      kind: 'permission-denied',
      status: 403,
      detail: 'permission denied',
    });
  });

  it('keeps transport and server failures unavailable instead of empty', () => {
    expect(classifyWorkspaceLoadFailure(new Error('Network Error'))).toEqual({
      kind: 'unavailable',
      detail: 'Network Error',
    });

    expect(
      classifyWorkspaceLoadFailure({
        response: { status: 503 },
        data: { message: 'catalogue unavailable' },
      }),
    ).toEqual({
      kind: 'unavailable',
      status: 503,
      detail: 'catalogue unavailable',
    });
  });

  it('also recognizes numeric business 403 codes when HTTP status is absent', () => {
    expect(
      classifyWorkspaceLoadFailure({
        code: 403,
        message: 'forbidden',
      }),
    ).toEqual({
      kind: 'permission-denied',
      status: 403,
      detail: 'forbidden',
    });
  });
});
