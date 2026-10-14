import { render, screen } from '@testing-library/react';
import { HomeHeader } from './HomeHeader';

jest.mock('@umijs/max', () => ({
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));

test('keeps counts in the project overview instead of duplicating them in the header', () => {
  render(<HomeHeader />);
  expect(screen.getByText('Data Ops')).toBeInTheDocument();
  expect(screen.getByText('pages.home.header.tagline')).toBeInTheDocument();
  expect(screen.queryByText('pages.home.header.dataSources')).toBeNull();
  expect(screen.queryByText('pages.home.header.running')).toBeNull();
});
