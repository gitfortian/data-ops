import { queryString } from './query-string';

describe('Data Development queryString', () => {
  it('skips only absent values and the literal empty string', () => {
    expect(queryString({ absent: undefined, nul: null, blank: '', zero: 0, off: false, space: ' ' }))
      .toBe('?zero=0&off=false&space=+');
  });

  it('keeps nonempty values in input order and URL-encodes them', () => {
    expect(queryString({ search: 'a&b', page: 2, city: '新加坡' }))
      .toBe('?search=a%26b&page=2&city=%E6%96%B0%E5%8A%A0%E5%9D%A1');
  });

  it('skips the literal empty string but preserves objects that stringify to empty', () => {
    const custom = { toString: () => '' };
    // The original helper checks the raw value before String(value).
    // An object is not === '', so URLSearchParams retains its empty value.
    expect(queryString({ custom })).toBe('?custom=');
    expect(queryString({ custom, page: 1 })).toBe('?custom=&page=1');
    expect(queryString({ blank: '' })).toBe('');
    expect(queryString({})).toBe('');
  });
});
