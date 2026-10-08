import { queryString } from './query-string';

describe('Data Development query parameters', () => {
  it('omits undefined, null, and the empty string without losing falsy values', () => {
    expect(queryString({
      undefinedValue: undefined,
      nullValue: null,
      empty: '',
      zero: 0,
      disabled: false,
      whitespace: ' ',
    })).toBe('?zero=0&disabled=false&whitespace=+');
  });

  it('encodes names and values in insertion order', () => {
    expect(queryString({ 'table name': 'a&b=c', search: '中文/空 格' }))
      .toBe('?table+name=a%26b%3Dc&search=%E4%B8%AD%E6%96%87%2F%E7%A9%BA+%E6%A0%BC');
  });

  it('retains objects stringifying to empty, matching the original strict check', () => {
    expect(queryString({ objectValue: { toString: () => '' } }))
      .toBe('?objectValue=');
    expect(queryString({})).toBe('');
    expect(queryString({ keyword: null })).toBe('');
  });
});
