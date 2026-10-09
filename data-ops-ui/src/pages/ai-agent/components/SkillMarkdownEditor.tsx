import {
  BoldOutlined,
  CodeOutlined,
  ItalicOutlined,
  LinkOutlined,
  SearchOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { Alert, Button, Input, Segmented, Space, Spin, Tooltip, Typography } from 'antd';
import React from 'react';
import type { MarkdownFormat } from '../skill-markdown';
import type { MarkdownSourceHandle } from './SkillMarkdownSourceEditor';
import SkillMarkdownContent from './SkillMarkdownContent';

// Defer Monaco initialization until the editor opens; use the project's CommonJS-compatible loader.
const loadSourceEditor = () =>
  React.lazy(async () => {
    const source: typeof import('./SkillMarkdownSourceEditor') = require('./SkillMarkdownSourceEditor');
    return { default: source.default };
  });
const formats: { kind: MarkdownFormat; label: string; icon?: React.ReactNode }[] = [
  { kind: 'heading', label: '标题' },
  { kind: 'bold', label: '加粗', icon: <BoldOutlined /> },
  { kind: 'italic', label: '斜体', icon: <ItalicOutlined /> },
  { kind: 'list', label: '列表', icon: <UnorderedListOutlined /> },
  { kind: 'quote', label: '引用' },
  { kind: 'code', label: '代码块', icon: <CodeOutlined /> },
  { kind: 'link', label: '链接', icon: <LinkOutlined /> },
];

class EditorBoundary extends React.Component<
  { children: React.ReactNode; fallback: React.ReactNode; onFailure: () => void },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch() {
    this.props.onFailure();
  }
  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}

interface Props {
  value?: string;
  onChange?: (value: string) => void;
  id?: string;
  disabled?: boolean;
}

const SkillMarkdownEditor: React.FC<Props> = ({ value = '', onChange, id, disabled = false }) => {
  const [mode, setMode] = React.useState('split');
  const [fallback, setFallback] = React.useState(false);
  const [SourceEditor, setSourceEditor] = React.useState(loadSourceEditor);
  const [attempt, setAttempt] = React.useState(0);
  const editorRef = React.useRef<MarkdownSourceHandle>(null);
  const change = (next: string) => onChange?.(next);
  return (
    <div id={id} className="skill-md-editor">
      <div className="skill-md-toolbar">
        <Segmented
          aria-label="Markdown 编辑方式"
          value={mode}
          onChange={(next) => setMode(String(next))}
          options={[
            { label: '编辑', value: 'edit' },
            { label: '分屏', value: 'split' },
            { label: '预览', value: 'preview' },
          ]}
        />
        <Typography.Text type="secondary">
          {value.length} 字符 · {value.split(/\r\n|\r|\n/).length} 行
        </Typography.Text>
      </div>
      <Space wrap size={4} className="skill-md-format-toolbar">
        {formats.map((item) => (
          <Tooltip key={item.kind} title={item.label}>
            <Button
              size="small"
              aria-label={`插入${item.label}`}
              icon={item.icon}
              disabled={disabled || mode === 'preview' || fallback}
              onClick={() => editorRef.current?.format(item.kind)}
            >
              {item.icon ? null : item.label}
            </Button>
          </Tooltip>
        ))}
        <Button
          size="small"
          icon={<SearchOutlined />}
          disabled={mode === 'preview' || fallback}
          onClick={() => editorRef.current?.find()}
        >
          查找
        </Button>
      </Space>
      <div className={`skill-md-edit-panes skill-md-mode-${mode}`}>
        <div className="skill-md-edit-source" aria-label="Markdown 编辑区" hidden={mode === 'preview'}>
          <EditorBoundary
            key={attempt}
            onFailure={() => setFallback(true)}
            fallback={
              <>
                <Alert
                  type="warning"
                  showIcon
                  message="Markdown 编辑器加载失败，可继续编辑源码。"
                  action={
                    <Button
                      onClick={() => {
                        setFallback(false);
                        setSourceEditor(loadSourceEditor());
                        setAttempt((previous) => previous + 1);
                      }}
                    >
                      重试加载
                    </Button>
                  }
                />
                <Input.TextArea
                  aria-label="技能 Markdown 源码"
                  value={value}
                  disabled={disabled}
                  onChange={(event) => change(event.target.value)}
                  className="skill-md-fallback"
                />
              </>
            }
          >
            <React.Suspense
              fallback={
                <div className="skill-md-loading">
                  <Spin tip="正在加载 Markdown 编辑器" />
                </div>
              }
            >
              <SourceEditor ref={editorRef} value={value} onChange={change} disabled={disabled} />
            </React.Suspense>
          </EditorBoundary>
        </div>
        <div className="skill-md-edit-preview" aria-label="Markdown 预览区" hidden={mode === 'edit'}>
          <SkillMarkdownContent source={value} outline={false} />
        </div>
      </div>
    </div>
  );
};

export default SkillMarkdownEditor;
