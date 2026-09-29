import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/esm/vs/basic-languages/python/python.contribution';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import { useEffect, useRef } from 'react';

import type { DevelopmentEditorViewState } from '../sql/session/types';
import {
  YAK_EDITOR_THEMES,
  editorOptionsFromSettings,
  getYakEditorSettings,
  subscribeYakEditorSettings,
} from '../sql/editorSettings';
import { setupMonacoEnvironment } from '../sql/monaco/setupMonacoEnvironment';

export interface PythonEditorPosition {
  lineNumber: number;
  column: number;
  selectionLength: number;
}

interface PythonMonacoEditorProps {
  id: string;
  value: string;
  initialViewState?: DevelopmentEditorViewState;
  onChange: (value: string) => void;
  onRunScript?: (script: string) => void;
  running?: boolean;
  readOnly?: boolean;
  onPositionChange?: (position: PythonEditorPosition) => void;
  onViewStateChange?: (viewState: DevelopmentEditorViewState) => void;
}

let themesRegistered = false;

const ensureYakThemes = () => {
  if (themesRegistered) return;
  themesRegistered = true;
  YAK_EDITOR_THEMES.forEach((theme) => monaco.editor.defineTheme(theme.name, theme.data));
};

const PythonMonacoEditor = ({
  id,
  value,
  initialViewState,
  onChange,
  onRunScript,
  running,
  readOnly = false,
  onPositionChange,
  onViewStateChange,
}: PythonMonacoEditorProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor>();
  const modelRef = useRef<monaco.editor.ITextModel>();
  const onChangeRef = useRef(onChange);
  const onRunScriptRef = useRef(onRunScript);
  const runningRef = useRef(running);
  const onPositionChangeRef = useRef(onPositionChange);
  const onViewStateChangeRef = useRef(onViewStateChange);

  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);
  useEffect(() => { onRunScriptRef.current = onRunScript; }, [onRunScript]);
  useEffect(() => { runningRef.current = running; }, [running]);
  useEffect(() => { onPositionChangeRef.current = onPositionChange; }, [onPositionChange]);
  useEffect(() => { onViewStateChangeRef.current = onViewStateChange; }, [onViewStateChange]);

  useEffect(() => {
    if (!containerRef.current) return undefined;

    setupMonacoEnvironment();
    ensureYakThemes();

    const uri = monaco.Uri.parse(`inmemory://yak-ops/data-development/python/${encodeURIComponent(id)}.py`);
    monaco.editor.getModel(uri)?.dispose();

    const model = monaco.editor.createModel(value, 'python', uri);
    const initialSettings = getYakEditorSettings();
    const editor = monaco.editor.create(containerRef.current, {
      model,
      ...editorOptionsFromSettings(initialSettings),
      readOnly,
      domReadOnly: readOnly,
      automaticLayout: true,
      tabSize: 4,
      insertSpaces: true,
      scrollBeyondLastLine: false,
      smoothScrolling: true,
      cursorSmoothCaretAnimation: 'on',
      cursorBlinking: 'smooth',
      showFoldingControls: 'always',
      lineNumbersMinChars: 3,
      overviewRulerLanes: 0,
      hideCursorInOverviewRuler: true,
      fixedOverflowWidgets: true,
      padding: { top: 12, bottom: 12 },
      quickSuggestions: { other: true, comments: false, strings: false },
      suggestOnTriggerCharacters: true,
      acceptSuggestionOnEnter: 'on',
      tabCompletion: 'on',
      suggestSelection: 'recentlyUsedByPrefix',
      parameterHints: { enabled: true },
      hover: { enabled: 'on', delay: 300, sticky: true },
      suggest: {
        showWords: true,
        showKeywords: true,
        showFunctions: true,
        showSnippets: true,
        snippetsPreventQuickSuggestions: false,
      },
      wordBasedSuggestions: 'currentDocument',
      bracketPairColorization: { enabled: true },
      guides: { bracketPairs: true, indentation: true },
    });

    editorRef.current = editor;
    modelRef.current = model;

    const unsubscribeSettings = subscribeYakEditorSettings((settings) => {
      monaco.editor.setTheme(settings.theme);
      editor.updateOptions(editorOptionsFromSettings(settings));
    });

    if (initialViewState) {
      if (initialViewState.selection) editor.setSelection(initialViewState.selection);
      else editor.setPosition({ lineNumber: initialViewState.lineNumber, column: initialViewState.column });
      editor.setScrollTop(initialViewState.scrollTop);
      editor.setScrollLeft(initialViewState.scrollLeft);
    }

    const emitEditorState = () => {
      const position = editor.getPosition();
      const selection = editor.getSelection();
      if (!position) return;
      const selectionLength = selection ? model.getValueInRange(selection).length : 0;
      onPositionChangeRef.current?.({ lineNumber: position.lineNumber, column: position.column, selectionLength });
      onViewStateChangeRef.current?.({
        lineNumber: position.lineNumber,
        column: position.column,
        selection: selection ? {
          startLineNumber: selection.startLineNumber,
          startColumn: selection.startColumn,
          endLineNumber: selection.endLineNumber,
          endColumn: selection.endColumn,
        } : undefined,
        scrollTop: editor.getScrollTop(),
        scrollLeft: editor.getScrollLeft(),
      });
    };

    const contentDisposable = model.onDidChangeContent(() => {
      if (!readOnly) onChangeRef.current(model.getValue());
    });

    // Ctrl/Cmd + Shift + Enter to run the full script
    const runBinding = editor.addAction({
      id: 'yak-python-run-script',
      label: 'Run Python Script',
      keybindings: [
        monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter,
      ],
      run: () => {
        if (!runningRef.current && onRunScriptRef.current) {
          onRunScriptRef.current(model.getValue());
        }
      },
    });

    const cursorDisposable = editor.onDidChangeCursorPosition(emitEditorState);
    const selectionDisposable = editor.onDidChangeCursorSelection(emitEditorState);
    const scrollDisposable = editor.onDidScrollChange(emitEditorState);

    emitEditorState();

    return () => {
      emitEditorState();
      unsubscribeSettings();
      contentDisposable.dispose();
      runBinding.dispose();
      cursorDisposable.dispose();
      selectionDisposable.dispose();
      scrollDisposable.dispose();
      editor.dispose();
      model.dispose();
      editorRef.current = undefined;
      modelRef.current = undefined;
    };
  }, [id, readOnly]);

  useEffect(() => {
    const model = modelRef.current;
    if (!model || model.getValue() === value) return;
    model.pushEditOperations([], [{ range: model.getFullModelRange(), text: value }], () => null);
  }, [value]);

  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
};

export default PythonMonacoEditor;
