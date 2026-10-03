import { render, screen, waitFor } from '@testing-library/react';
import { validateDevelopmentTaskPublish } from '@/services/data-development';
import { hydrateEditorSession } from '../../editors/session/editorSessionStore';
import type { DevelopmentNode } from '../../types';
import AuthoringStatusBar from './AuthoringStatusBar';

jest.mock('@/services/data-development', () => ({ validateDevelopmentTaskPublish: jest.fn().mockResolvedValue({ valid: false, draftRevision: 1, issues: [] }) }));
jest.mock('../../service', () => ({ listDevelopmentTaskRevisions: jest.fn().mockResolvedValue({ code: 200, data: [] }) }));
const mockIntl = { formatMessage: ({ id, defaultMessage }: { id: string; defaultMessage?: string }) => defaultMessage || id };
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
