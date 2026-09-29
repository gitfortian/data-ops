import { render, screen } from '@testing-library/react';
import RouteAccessBoundary from '.';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useLocation: () => ({ pathname: '/sync/batch-link-up' }),
  useModel: () => ({ initialState: { currentUser: { permissionCodes: [] } } }),
}));

describe('RouteAccessBoundary', () => {
  it('renders 403 instead of a denied direct URL', () => {
    render(
      <RouteAccessBoundary>
        <div>secret page</div>
      </RouteAccessBoundary>,
    );
    expect(screen.getByText('403')).toBeTruthy();
    expect(screen.queryByText('secret page')).toBeNull();
  });

  it('requires both the action permission and the stable menu grant', () => {
    const route = {
      id: 'batch-link-up',
      menuCode: 'batch-link-up' as const,
      mode: 'one' as const,
      permission: 'task:batch:read',
      path: '/sync/batch-link-up',
      title: '离线同步',
      component: './integration/batch-link-up',
    };

    const { rerender } = render(
      <RouteAccessBoundary
        route={route}
        permissionCodes={['task:batch:read']}
        menuCodes={[]}
      >
        <div>granted page</div>
      </RouteAccessBoundary>,
    );
    expect(screen.getByText('403')).toBeTruthy();

    rerender(
      <RouteAccessBoundary
        route={route}
        permissionCodes={['task:batch:read']}
        menuCodes={['batch-link-up']}
      >
        <div>granted page</div>
      </RouteAccessBoundary>,
    );
    expect(screen.getByText('granted page')).toBeTruthy();
  });
});
