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

  it('keeps the original literal-empty-string semantics, distinct from HTTP shared helper', () => {
    const custom = { toString: () => '' };
    expect(queryString({ custom })).toBe('');
    expect(queryString({ custom, page: 1 })).toBe('?custom=&page=1');
    expect(queryString({})).toBe('');
  });
});
