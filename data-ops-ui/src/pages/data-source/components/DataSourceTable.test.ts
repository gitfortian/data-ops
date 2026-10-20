import { publicJdbcEndpoint } from './DataSourceTable';
describe('data source JDBC address display never leaks credentials or arbitrary URL parameters', () => {
  it.each([
    ['jdbc:mysql://database.internal:3306/orders', 'database.internal:3306'],
    ['jdbc:postgresql://db-01:5432/dwh', 'db-01:5432'],
    ['jdbc:mysql://user:secret@db:3306/orders', '—'],
    ['jdbc:mysql://db:3306/orders?user=alice&password=secret', '—'],
    ['jdbc:mysql://db:3306/orders;password=secret', '—'],
    ['jdbc:oracle:thin:@host:1521:sid', '—'],
    ['http://host:1234', '—'],
    ['jdbc:mysql://db:99999/orders', '—'],
  ])('accepts only a safe host/port summary of %s', (url, expected) => {
    expect(publicJdbcEndpoint(url)).toBe(expected);
  });
});
