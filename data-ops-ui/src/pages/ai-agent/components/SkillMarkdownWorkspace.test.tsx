import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import SkillEditorModal from './SkillEditorModal';
import SkillDocumentViewer from './SkillDocumentViewer';
import SkillMarkdownEditor from './SkillMarkdownEditor';

jest.mock('./SkillMarkdownSourceEditor', () => {
  const React = jest.requireActual<typeof import('react')>('react');
  return {
    __esModule: true,
    default: React.forwardRef(
      (props: { value: string; onChange: (value: string) => void; disabled: boolean }, _ref) => {
        if (props.value === 'EDITOR_LOAD_FAILURE') throw new Error('test load failure');
        return (
          <textarea
            aria-label="技能 Markdown 源码"
            value={props.value}
            disabled={props.disabled}
            onChange={(event) => props.onChange(event.target.value)}
          />
        );
      },
    ),
  };
});

const source =
  '---\r\nname: skill-demo\r\n---\r\n\r\n# 长文说明\r\n\r\n## 检查步骤\r\n\r\n- 保留  空格\r\n\r\n```json\r\n{"a":1}\r\n```\r\n';
const template = {
  skillId: 'skill-demo',
  name: '技能演示',
  description: '核对正文',
  metadata: { scenario: 'TEST' },
  content: source,
};

it('编辑/预览/分屏/全屏不提交、重置或转换 Markdown，明确保存才回传原文', async () => {
  const submit = jest.fn();
  render(
    <SkillEditorModal open editing={null} template={template} saving={false} onCancel={jest.fn()} onSubmit={submit} />,
  );
  await screen.findByRole('textbox', { name: '技能 Markdown 源码' });
  const preview = screen.getByLabelText('Markdown 预览区');
  expect(within(preview).getByRole('heading', { name: '长文说明' })).toBeTruthy();
  fireEvent.click(screen.getByText('文档头信息（YAML）'));
  expect(within(preview).getByText('name: skill-demo')).toBeTruthy();
  fireEvent.click(screen.getByText('预览', { exact: true }));
  fireEvent.click(screen.getByRole('button', { name: '全屏编辑' }));
  fireEvent.click(screen.getByText('编辑', { exact: true }));
  fireEvent.click(screen.getByRole('button', { name: '退出全屏' }));
  fireEvent.click(screen.getByText('分屏', { exact: true }));
  expect(submit).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(submit).toHaveBeenCalledWith(template));
});

it('编辑即时更新预览，编辑请求仍携带原 version 并保留正文空格', async () => {
  const submit = jest.fn();
  render(
    <SkillEditorModal
      open
      editing={{ ...template, enabled: true, version: 9, createTime: '', updateTime: '' }}
      saving={false}
      onCancel={jest.fn()}
      onSubmit={submit}
    />,
  );
  fireEvent.change(await screen.findByRole('textbox', { name: '技能 Markdown 源码' }), {
    target: { value: '# 新版\n\n正文  两个空格\n' },
  });
  expect(screen.getByRole('heading', { name: '新版' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() =>
    expect(submit).toHaveBeenCalledWith({ ...template, content: '# 新版\n\n正文  两个空格\n', expectedVersion: 9 }),
  );
});

it('阅读器源码与原文完全一致，没有写入入口；目录仅定位自己的预览', () => {
  const scroll = jest.fn();
  const original = HTMLElement.prototype.scrollTo;
  HTMLElement.prototype.scrollTo = scroll;
  try {
    render(<SkillDocumentViewer source={source} />);
    expect(screen.queryByRole('textbox')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '检查步骤' }));
    expect(scroll.mock.instances[0]).toBe(screen.getByLabelText('正文阅读区'));
    fireEvent.click(screen.getByText('源码', { exact: true }));
    expect(screen.getByLabelText('Markdown 原文').textContent).toBe(source);
    fireEvent.click(screen.getByText('阅读', { exact: true }));
    expect(screen.getByRole('heading', { name: '长文说明' })).toBeTruthy();
  } finally {
    HTMLElement.prototype.scrollTo = original;
  }
});

it('编辑器失败时提供源码编辑及重试，保留草稿并仍可预览', async () => {
  const consoleError = jest.spyOn(console, 'error').mockImplementation(() => undefined);
  const onChange = jest.fn();
  try {
    const { rerender } = render(<SkillMarkdownEditor value="EDITOR_LOAD_FAILURE" onChange={onChange} />);
    await screen.findByText('Markdown 编辑器加载失败，可继续编辑源码。');
    fireEvent.change(screen.getByRole('textbox', { name: '技能 Markdown 源码' }), {
      target: { value: '# 已保留草稿' },
    });
    expect(onChange).toHaveBeenCalledWith('# 已保留草稿');
    rerender(<SkillMarkdownEditor value="# 已保留草稿" onChange={onChange} />);
    expect(screen.getByRole('heading', { name: '已保留草稿' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '重试加载' }));
    await waitFor(() => expect(screen.queryByText('Markdown 编辑器加载失败，可继续编辑源码。')).toBeNull());
    expect(await screen.findByRole('textbox', { name: '技能 Markdown 源码' })).toHaveProperty('value', '# 已保留草稿');
  } finally {
    consoleError.mockRestore();
  }
});
