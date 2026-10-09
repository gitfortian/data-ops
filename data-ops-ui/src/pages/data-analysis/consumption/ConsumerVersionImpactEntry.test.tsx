import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import type { DataProductView } from '@/services/consumption';
import ConsumerVersionImpactEntry from './ConsumerVersionImpactEntry';

let mockProjectId = 42;
let mockRoute = 'DATASET%3A101';
let mockPermissions = ['data-asset:read', 'agent:chat:run'];
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() }, useParams: () => ({ productKey: mockRoute }) }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ usePermissionAccess: () => ({ can: (code: string) => mockPermissions.includes(code) }) }));
const product = { projectId: 42, productKey: { productType: 'DATASET', sourceIdentity: '101' } } as DataProductView;
beforeEach(() => { mockProjectId = 42; mockRoute = 'DATASET%3A101'; mockPermissions = ['data-asset:read', 'agent:chat:run']; jest.clearAllMocks(); });

it('opens a selected exact-version task only on explicit click', () => {
  render(<ConsumerVersionImpactEntry product={product} version="9001" blocked={false} />);
  expect(history.push).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('AI 消费影响说明'));
  expect(history.push).toHaveBeenCalledWith('/ai-agent?purpose=CONSUMER_VERSION_IMPACT&consumerProductType=DATASET&consumerProductIdentity=101&consumerVersionIdentity=9001');
});
it('hides stale product, project, loading, invalid version and revoked permission entries', () => {
  const view = render(<ConsumerVersionImpactEntry product={product} version="9001" blocked={true} />);
  expect(screen.queryByText('AI 消费影响说明')).not.toBeInTheDocument();
  for (const change of [() => { mockProjectId = 43; }, () => { mockProjectId = 42; mockRoute = 'DATASET:102'; },
    () => { mockRoute = 'DATASET:101'; mockPermissions = ['agent:chat:run']; },
    () => { mockPermissions = ['data-asset:read']; }]) {
    change(); view.rerender(<ConsumerVersionImpactEntry product={product} version="9001" blocked={false} />);
    expect(screen.queryByText('AI 消费影响说明')).not.toBeInTheDocument();
  }
  mockPermissions = ['data-asset:read', 'agent:chat:run'];
  view.rerender(<ConsumerVersionImpactEntry product={product} version="../1" blocked={false} />);
  expect(screen.queryByText('AI 消费影响说明')).not.toBeInTheDocument();
});
