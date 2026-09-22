import {
  buildMessageQuery,
  MESSAGE_COUNT_CHANGED_EVENT,
  notifyMessageCountChanged,
  safeMessageActionPath,
} from './messages';

describe('message center contract', () => {
  it('serializes project scope and epoch time filters', () => {
    const params = new URLSearchParams(
      buildMessageQuery({
        pageNum: 2,
        pageSize: 20,
        status: 'UNREAD',
        type: 'TASK',
        projectId: 7,
        startTime: 1788163200000,
        endTime: 1788249599999,
      }),
    );

    expect(params.get('pageNum')).toBe('2');
    expect(params.get('pageSize')).toBe('20');
    expect(params.get('status')).toBe('UNREAD');
    expect(params.get('type')).toBe('TASK');
    expect(params.get('projectId')).toBe('7');
    expect(params.get('startTime')).toBe('1788163200000');
    expect(params.get('endTime')).toBe('1788249599999');
  });

  it('omits optional filters that are not supplied', () => {
    const params = new URLSearchParams(
      buildMessageQuery({ pageNum: 1, pageSize: 3 }),
    );

    expect(params.has('projectId')).toBe(false);
    expect(params.has('status')).toBe(false);
    expect(params.has('startTime')).toBe(false);
  });

  it('only accepts internal action paths', () => {
    expect(safeMessageActionPath('/data-quality/overview')).toBe(
      '/data-quality/overview',
    );
    expect(safeMessageActionPath('https://example.com')).toBeUndefined();
    expect(safeMessageActionPath('//example.com/path')).toBeUndefined();
  });

  it('emits a shared unread-count refresh event', () => {
    const listener = jest.fn();
    window.addEventListener(MESSAGE_COUNT_CHANGED_EVENT, listener);

    notifyMessageCountChanged();

    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(MESSAGE_COUNT_CHANGED_EVENT, listener);
  });
});
