import { act, render, screen, waitFor } from '@testing-library/react';
import {
  listDevelopmentTaskRevisions,
  validateDevelopmentTaskPublish,
} from '@/services/data-development';
import { hydrateEditorSession } from '../../editors/session/editorSessionStore';
import type { DevelopmentNode } from '../../types';
import AuthoringStatusBar from './AuthoringStatusBar';

jest.mock('@/services/data-development', () => ({
  listDevelopmentTaskRevisions: jest.fn().mockResolvedValue([]),
  validateDevelopmentTaskPublish: jest.fn().mockResolvedValue({
    valid: false, draftRevision: 1, issues: [],
  }),
}));
const mockIntl = {
  formatMessage: (
    { id, defaultMessage }: { id: string; defaultMessage?: string },
    values?: { revision?: number },
  ) => values?.revision !== undefined ? `${id}:${values.revision}` : defaultMessage || id,
};
jest.mock('@umijs/max', () => ({ useAccess: () => ({ hasPermission: () => true }), useIntl: () => mockIntl }));

test('空 SQL 草稿使用中性提示，填写内容后仍执行发布校验', async () => {
  const node: DevelopmentNode = { id: 'empty-sql', name: 'SQL 任务', type: 'SQL', configured: false, pendingPublish: true };
  hydrateEditorSession(node.id, 'SQL', 1, '', '{}', 1);
  const { rerender } = render(<AuthoringStatusBar node={node} />);
  expect(await screen.findByText('先填写任务内容')).toBeInTheDocument();
  expect(validateDevelopmentTaskPublish).not.toHaveBeenCalled();
  expect(screen.queryByText('pages.dataDevelopment.authoring.pendingPublish')).not.toBeInTheDocument();
  expect(screen.queryByText('pages.dataDevelopment.authoring.saved')).not.toBeInTheDocument();
  hydrateEditorSession(node.id, 'SQL', 1, 'SELECT 1', '{}', 1);
  rerender(<AuthoringStatusBar node={node} />);
  await waitFor(() => expect(validateDevelopmentTaskPublish).toHaveBeenCalledWith(node.id, 1));
});

describe('AuthoringStatusBar modern revision read contract', () => {
  const list = jest.mocked(listDevelopmentTaskRevisions);
  const readiness = jest.mocked(validateDevelopmentTaskPublish);
  const createNode = (id: string): DevelopmentNode => ({
    id,
    name: 'SQL Task',
    type: 'SQL',
    configured: true,
    pendingPublish: false,
  });

  beforeEach(() => {
    list.mockReset();
    readiness.mockReset();
    list.mockResolvedValue([]);
    readiness.mockResolvedValue({ valid: false, draftRevision: 1, issues: [] } as never);
  });

  it('renders the newest revision directly from modern data-only rows', async () => {
    list.mockResolvedValue([{
      id: 'revision-5', nodeId: 'status-node-1', revisionNo: 5,
      sourceDraftRevision: 5, checksum: 'xyz',
    }]);
    render(<AuthoringStatusBar node={createNode('status-node-1')} />);
    expect(await screen.findByText('pages.dataDevelopment.authoring.publishedRevision:5'))
      .toBeInTheDocument();
    expect(list).toHaveBeenCalledWith('status-node-1');
  });

  it('keeps successful empty history separate from a failed history query', async () => {
    render(<AuthoringStatusBar node={createNode('status-node-2')} />);
    expect(await screen.findByText('pages.dataDevelopment.authoring.notPublished'))
      .toBeInTheDocument();
  });

  it('shows unknown published state on failed revision lookup rather than not published', async () => {
    list.mockRejectedValue(new Error('cannot read revisions'));
    render(<AuthoringStatusBar node={createNode('status-node-3')} />);
    expect(await screen.findByText('pages.dataDevelopment.authoring.publishedUnknown'))
      .toBeInTheDocument();
    expect(screen.queryByText('pages.dataDevelopment.authoring.notPublished'))
      .not.toBeInTheDocument();
  });

  it('ignores an older revision result after switching nodes', async () => {
    let complete!: (rows: Awaited<ReturnType<typeof listDevelopmentTaskRevisions>>) => void;
    const pending = new Promise<Awaited<ReturnType<typeof listDevelopmentTaskRevisions>>>(
      resolve => { complete = resolve; },
    );
    list.mockImplementation(id => id === 'status-node-old'
      ? pending
      : Promise.resolve([{
          id: 'revision-8', nodeId: id, revisionNo: 8,
          sourceDraftRevision: 8, checksum: 'xyz',
        }]));
    const { rerender } = render(
      <AuthoringStatusBar node={createNode('status-node-old')} />,
    );
    rerender(<AuthoringStatusBar node={createNode('status-node-new')} />);
    expect(await screen.findByText('pages.dataDevelopment.authoring.publishedRevision:8'))
      .toBeInTheDocument();
    await act(async () => {
      complete([{
        id: 'revision-1', nodeId: 'status-node-old', revisionNo: 1,
        sourceDraftRevision: 1, checksum: 'old',
      }]);
      await pending;
    });
    expect(screen.getByText('pages.dataDevelopment.authoring.publishedRevision:8'))
      .toBeInTheDocument();
    expect(screen.queryByText('pages.dataDevelopment.authoring.publishedRevision:1'))
      .not.toBeInTheDocument();
  });
});
