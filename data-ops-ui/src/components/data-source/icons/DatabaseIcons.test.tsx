import { render } from '@testing-library/react';

import DatabaseIcons from './DatabaseIcons';

const newlySupportedTypes = [
  'GOLDENDB',
  'GBASE8C',
  'GBASE8A',
  'GBASE8S',
  'HANA',
  'OCEANBASE',
  'YASHAN_DB',
  'HIGHGO',
  'IRIS',
  'XUGU',
  'DUCKDB',
];

describe('DatabaseIcons', () => {
  it.each(newlySupportedTypes)(
    'uses the database brand icon for %s instead of the generic fallback',
    (dbType) => {
      const { container } = render(<DatabaseIcons dbType={dbType} />);

      expect(container.querySelector('svg')).not.toBeNull();
      expect(container.querySelector('.anticon-database')).toBeNull();
    },
  );

  it('keeps the generic database icon for unknown types', () => {
    const { container } = render(<DatabaseIcons dbType="UNKNOWN" />);

    expect(container.querySelector('.anticon-database')).not.toBeNull();
  });

  it('keeps GoldenDB within the requested icon dimensions', () => {
    const { container } = render(
      <DatabaseIcons dbType="GOLDENDB" width="18px" height="19px" />,
    );
    const svg = container.querySelector('svg');

    expect(svg?.getAttribute('width')).toBe('18px');
    expect(svg?.getAttribute('height')).toBe('19px');
  });
});
