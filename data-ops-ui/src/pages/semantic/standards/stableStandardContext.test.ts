import {
  classifyStandardContextFailure,
  parseStandardId,
  withStandardContext,
} from './stableStandardContext';

describe('stable Standard context', () => {
  it('accepts only positive safe integer ids', () => {
    expect(parseStandardId('42')).toBe(42);
    expect(parseStandardId('0')).toBeNull();
    expect(parseStandardId('-1')).toBeNull();
    expect(parseStandardId('1.5')).toBeNull();
    expect(parseStandardId('abc')).toBeNull();
    expect(parseStandardId(null)).toBeNull();
  });

  it('preserves unrelated query context while setting and clearing standardId', () => {
    const original = new URLSearchParams('kind=CALIBER&status=ENABLED');
    const selected = withStandardContext(original, 17);
    expect(selected.get('standardId')).toBe('17');
    expect(selected.get('kind')).toBe('CALIBER');
    expect(selected.get('status')).toBe('ENABLED');

    const cleared = withStandardContext(selected, null);
    expect(cleared.has('standardId')).toBe(false);
    expect(cleared.get('kind')).toBe('CALIBER');
  });

  it('does not collapse forbidden, current-project empty, and unavailable reads', () => {
    expect(classifyStandardContextFailure({ response: { status: 403 } })).toBe('FORBIDDEN');
    expect(classifyStandardContextFailure({ response: { status: 404 } })).toBe('EMPTY');
    expect(classifyStandardContextFailure({ response: { status: 503 } })).toBe('UNAVAILABLE');
    expect(classifyStandardContextFailure(new Error('network'))).toBe('UNAVAILABLE');
  });
});
