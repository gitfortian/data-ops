import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/esm/vs/basic-languages/markdown/markdown.contribution';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import React from 'react';
import { setupMonacoEnvironment } from '@/components/SqlCodeEditor/setupMonacoEnvironment';
import { markdownInsertion } from '../skill-markdown';
import type { MarkdownFormat } from '../skill-markdown';

export interface MarkdownSourceHandle {
  format: (kind: MarkdownFormat) => void;
  find: () => void;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}

/** Local, disposable Markdown model. Mode changes retain this editor and its undo history. */
const SkillMarkdownSourceEditor = React.forwardRef<MarkdownSourceHandle, Props>(
  ({ value, onChange, disabled }, ref) => {
    const containerRef = React.useRef<HTMLDivElement>(null);
    const editorRef = React.useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
    const changeRef = React.useRef(onChange);
    const disabledRef = React.useRef(disabled);
    const syncing = React.useRef(false);
    changeRef.current = onChange;
    disabledRef.current = disabled;

    const format = (kind: MarkdownFormat) => {
      const editor = editorRef.current;
      const selection = editor?.getSelection();
      const model = editor?.getModel();
      if (!editor || !selection || !model || disabledRef.current) return;
      const range = ['heading', 'list', 'quote'].includes(kind)
        ? new monaco.Range(
            selection.startLineNumber,
            1,
            selection.endLineNumber,
            model.getLineMaxColumn(selection.endLineNumber),
          )
        : selection;
      const text = markdownInsertion(kind, model.getValueInRange(range));
      editor.pushUndoStop();
      editor.executeEdits('skill-markdown-format', [{ range, text, forceMoveMarkers: true }]);
      editor.pushUndoStop();
      editor.focus();
    };
    React.useImperativeHandle(ref, () => ({
      format,
      find: () => {
        void editorRef.current?.getAction('actions.find')?.run();
      },
    }));

    React.useEffect(() => {
      if (!containerRef.current) return;
      setupMonacoEnvironment();
      const model = monaco.editor.createModel(value, 'markdown');
      // Monaco normalizes EOL internally. Preserve untouched input exactly, including mixed line endings.
      const editor = monaco.editor.create(containerRef.current, {
        model,
        automaticLayout: true,
        readOnly: disabled,
        domReadOnly: disabled,
        ariaLabel: '技能 Markdown 源码',
        wordWrap: 'on',
        fontSize: 14,
        lineHeight: 24,
        minimap: { enabled: false },
        lineNumbers: 'on',
        lineNumbersMinChars: 3,
        folding: true,
        scrollBeyondLastLine: false,
        padding: { top: 12, bottom: 12 },
        tabSize: 2,
        insertSpaces: true,
        renderWhitespace: 'selection',
      });
      editorRef.current = editor;
      const changes = model.onDidChangeContent(() => {
        if (!syncing.current && !disabledRef.current) changeRef.current(model.getValue());
      });
      const bold = editor.addAction({
        id: 'skill-markdown-bold',
        label: '加粗',
        keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyB],
        run: () => format('bold'),
      });
      const italic = editor.addAction({
        id: 'skill-markdown-italic',
        label: '斜体',
        keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyI],
        run: () => format('italic'),
      });
      return () => {
        changes.dispose();
        bold.dispose();
        italic.dispose();
        editor.dispose();
        model.dispose();
        editorRef.current = null;
      };
    }, []);

    React.useEffect(() => {
      const model = editorRef.current?.getModel();
      if (!model || model.getValue() === value || model.getValue() === value.replace(/\r\n|\r/g, '\n')) return;
      syncing.current = true;
      try {
        model.setValue(value);
      } finally {
        syncing.current = false;
      }
    }, [value]);
    React.useEffect(() => {
      editorRef.current?.updateOptions({ readOnly: disabled, domReadOnly: disabled });
    }, [disabled]);

    return <div className="skill-md-monaco" ref={containerRef} />;
  },
);

export default SkillMarkdownSourceEditor;
