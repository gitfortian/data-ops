import { queryString } from './query-string';

describe('queryString', () => {
  it('omits only null, undefined and empty string representations', () => {
    expect(queryString({
      unset: undefined,
      absent: null,
      empty: '',
      zero: 0,
      off: false,
      on: true,
      whitespace: ' ',
    })).toBe('?zero=0&off=false&on=true&whitespace=+');
  });

  it('encodes query names and values while retaining input order', () => {
    expect(queryString({ 'field name': 'a&b=c', keyword: '中文/空 格' }))
      .toBe('?field+name=a%26b%3Dc&keyword=%E4%B8%AD%E6%96%87%2F%E7%A9%BA+%E6%A0%BC');
  });

  it('returns an empty suffix for empty options and does not modify the input', () => {
    const params = { keyword: '', page: 0 };
    expect(queryString({})).toBe('');
    expect(queryString({ keyword: null })).toBe('');
    expect(queryString(params)).toBe('?page=0');
    expect(params).toEqual({ keyword: '', page: 0 });
  });
});
