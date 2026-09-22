import YakButton from '@/components/YakButton';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/esm/vs/basic-languages/sql/sql.contribution';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import { Modal } from 'antd';
import classNames from 'classnames';
import { Maximize2 } from 'lucide-react';
import type { CSSProperties } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { setupMonacoEnvironment } from './setupMonacoEnvironment';
import './index.less';

type SqlSchemaField = {
  name?: string;
  originFieldName?: string;
  type?: string;
  comment?: string;
};

type SqlTableOption = {
  value?: string | number;
  rawLabel?: string;
  description?: string;
};

interface SqlCodeEditorProps {
  value?: string;
  onChange: (value: string) => void;
  placeholder?: string;
  dbType?: string;
  schemaFields?: SqlSchemaField[];
  tableOptions?: SqlTableOption[];
  variables?: string[];
  minRows?: number;
  maxRows?: number;
  className?: string;
  showLineNumbers?: boolean;
  expandable?: boolean;
  fullscreenTitle?: string;
}

type SqlLexicalState =
  | 'code'
  | 'single-quote'
  | 'double-quote'
  | 'backtick'
  | 'bracket-identifier'
  | 'line-comment'
  | 'block-comment';

type SqlCompletionCandidate = {
  label: string;
  insertText?: string;
  kind: monaco.languages.CompletionItemKind;
  detail: string;
  documentation?: string;
  sortGroup: number;
};

type CompletionConfig = {
  schemaFields: SqlSchemaField[];
  tableOptions: SqlTableOption[];
  variables: string[];
};

const DEFAULT_MIN_ROWS = 5;
const DEFAULT_MAX_ROWS = 12;
const FULLSCREEN_MIN_ROWS = 18;
const FULLSCREEN_MAX_ROWS = 28;
const LINE_HEIGHT = 24;
const VERTICAL_PADDING = 20;
const SQL_COMPLETION_TOKEN_PATTERN = /(?:\$\{var:)?[A-Za-z_$][\w$:{.-]*$/;

const SQL_KEYWORDS = [
  'SELECT',
  'FROM',
  'WHERE',
  'AND',
  'OR',
  'INSERT',
  'INTO',
  'VALUES',
  'UPDATE',
  'SET',
  'DELETE',
  'JOIN',
  'LEFT JOIN',
  'RIGHT JOIN',
  'INNER JOIN',
  'FULL JOIN',
  'ON',
  'GROUP BY',
  'ORDER BY',
  'HAVING',
  'LIMIT',
  'OFFSET',
  'DISTINCT',
  'AS',
  'CASE',
  'WHEN',
  'THEN',
  'ELSE',
  'END',
  'CAST',
  'COUNT',
  'SUM',
  'AVG',
  'MIN',
  'MAX',
  'COALESCE',
  'DATE_FORMAT',
] as const;

const SQL_SNIPPETS: Array<Pick<SqlCompletionCandidate, 'label' | 'insertText' | 'detail'>> = [
  {
    label: 'SELECT * FROM',
    detail: '查询模板',
    insertText: 'SELECT *\nFROM ',
  },
  {
    label: 'SELECT FROM WHERE',
    detail: '过滤查询模板',
    insertText: 'SELECT \nFROM \nWHERE ',
  },
  {
    label: 'INSERT INTO SELECT',
    detail: '写入模板',
    insertText: 'INSERT INTO  ()\nSELECT \nFROM ',
  },
  {
    label: 'LEFT JOIN',
    detail: '关联查询',
    insertText: 'LEFT JOIN  ON ',
  },
  {
    label: 'GROUP BY',
    detail: '分组',
    insertText: 'GROUP BY ',
  },
  {
    label: 'ORDER BY',
    detail: '排序',
    insertText: 'ORDER BY ',
  },
  {
    label: 'LIMIT',
    detail: '限制行数',
    insertText: 'LIMIT ',
  },
  {
    label: '${var:today_start}',
    detail: '时间变量',
    insertText: '${var:today_start}',
  },
];

let themeRegistered = false;
let editorSequence = 0;

const ensureSqlCodeEditorTheme = () => {
  if (themeRegistered) return;
  themeRegistered = true;

  monaco.editor.defineTheme('yak-sql-code-editor', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: '2563EB', fontStyle: 'bold' },
      { token: 'comment', foreground: '94A3B8', fontStyle: 'italic' },
      { token: 'string', foreground: '0F766E' },
      { token: 'number', foreground: 'D97706' },
      { token: 'operator', foreground: '64748B' },
      { token: 'identifier', foreground: '334155' },
    ],
    colors: {
      'editor.background': '#FCFDFF',
      'editor.foreground': '#334155',
      'editorGutter.background': '#F8FAFC',
      'editorLineNumber.foreground': '#94A3B8',
      'editorLineNumber.activeForeground': '#64748B',
      'editor.lineHighlightBackground': '#F1F5F9B3',
      'editor.selectionBackground': '#4F6BFF29',
      'editor.inactiveSelectionBackground': '#4F6BFF1F',
      'editorCursor.foreground': '#0F172A',
      'editorIndentGuide.background1': '#EEF2F7',
      'editorIndentGuide.activeBackground1': '#CBD5E1',
      'editorSuggestWidget.background': '#FFFFFF',
      'editorSuggestWidget.border': '#D8DFFF',
      'editorSuggestWidget.foreground': '#334155',
      'editorSuggestWidget.selectedBackground': '#EEF2FF',
      'editorSuggestWidget.highlightForeground': '#315EFB',
      'editorWidget.border': '#D8DFFF',
      'editorWidget.background': '#FFFFFF',
      'scrollbarSlider.background': '#94A3B866',
      'scrollbarSlider.hoverBackground': '#94A3B399',
      'scrollbarSlider.activeBackground': '#64748B99',
    },
  });
};

const normalizeName = (value?: string | number) => String(value ?? '').trim();

const resolveDialectLabel = (dbType?: string) => {
  const normalized = String(dbType || '')
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_');

  if (normalized.includes('MARIADB')) return 'MariaDB';
  if (normalized.includes('MYSQL')) return 'MySQL';
  if (normalized.includes('POSTGRES') || normalized.includes('PGSQL')) return 'PostgreSQL';
  if (
    normalized.includes('SQLSERVER') ||
    normalized.includes('SQL_SERVER') ||
    normalized.includes('MSSQL')
  ) {
    return 'SQL Server';
  }
  if (normalized.includes('SQLITE')) return 'SQLite';
  if (normalized.includes('ORACLE')) return 'Oracle';
  return 'SQL';
};

const getTextBeforePosition = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) =>
  model.getValueInRange(new monaco.Range(1, 1, position.lineNumber, position.column));

const getLexicalState = (text: string): SqlLexicalState => {
  let state: SqlLexicalState = 'code';

  for (let index = 0; index < text.length; index += 1) {
    const current = text[index];
    const next = text[index + 1];

    if (state === 'line-comment') {
      if (current === '\n') state = 'code';
      continue;
    }

    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        state = 'code';
        index += 1;
      }
      continue;
    }

    if (state === 'single-quote') {
      if (current === '\\') {
        index += 1;
        continue;
      }
      if (current === "'" && next === "'") {
        index += 1;
        continue;
      }
      if (current === "'") state = 'code';
      continue;
    }

    if (state === 'double-quote') {
      if (current === '\\') {
        index += 1;
        continue;
      }
      if (current === '"' && next === '"') {
        index += 1;
        continue;
      }
      if (current === '"') state = 'code';
      continue;
    }

    if (state === 'backtick') {
      if (current === '\\') {
        index += 1;
        continue;
      }
      if (current === '`' && next === '`') {
        index += 1;
        continue;
      }
      if (current === '`') state = 'code';
      continue;
    }

    if (state === 'bracket-identifier') {
      if (current === ']' && next === ']') {
        index += 1;
        continue;
      }
      if (current === ']') state = 'code';
      continue;
    }

    if (current === '-' && next === '-') {
      state = 'line-comment';
      index += 1;
      continue;
    }
    if (current === '#') {
      state = 'line-comment';
      continue;
    }
    if (current === '/' && next === '*') {
      state = 'block-comment';
      index += 1;
      continue;
    }
    if (current === "'") {
      state = 'single-quote';
      continue;
    }
    if (current === '"') {
      state = 'double-quote';
      continue;
    }
    if (current === '`') {
      state = 'backtick';
      continue;
    }
    if (current === '[') state = 'bracket-identifier';
  }

  return state;
};

const buildCompletionCandidates = ({
  schemaFields,
  tableOptions,
  variables,
}: CompletionConfig): SqlCompletionCandidate[] => {
  const keywords = SQL_KEYWORDS.map<SqlCompletionCandidate>((label) => ({
    label,
    kind: monaco.languages.CompletionItemKind.Keyword,
    detail: 'SQL 关键字',
    sortGroup: 1,
  }));

  const snippets = SQL_SNIPPETS.map<SqlCompletionCandidate>((snippet) => ({
    ...snippet,
    kind:
      snippet.detail === '时间变量'
        ? monaco.languages.CompletionItemKind.Variable
        : monaco.languages.CompletionItemKind.Snippet,
    sortGroup: snippet.detail === '时间变量' ? 5 : 2,
  }));

  const tables = tableOptions
    .map<SqlCompletionCandidate | null>((option) => {
      const tableName = normalizeName(option.rawLabel || option.value);
      if (!tableName) return null;
      return {
        label: tableName,
        kind: monaco.languages.CompletionItemKind.Struct,
        detail: '数据表',
        documentation: option.description || undefined,
        sortGroup: 3,
      };
    })
    .filter((candidate): candidate is SqlCompletionCandidate => Boolean(candidate));

  const fields = schemaFields
    .map<SqlCompletionCandidate | null>((field) => {
      const fieldName = normalizeName(field.originFieldName || field.name);
      if (!fieldName) return null;
      return {
        label: fieldName,
        kind: monaco.languages.CompletionItemKind.Field,
        detail: field.type || '字段',
        documentation: field.comment || undefined,
        sortGroup: 4,
      };
    })
    .filter((candidate): candidate is SqlCompletionCandidate => Boolean(candidate));

  const variableNames = Array.from(new Set(variables.map(normalizeName).filter(Boolean)));
  const variableCandidates = variableNames.map<SqlCompletionCandidate>((name) => ({
    label: `\${var:${name}}`,
    insertText: `\${var:${name}}`,
    kind: monaco.languages.CompletionItemKind.Variable,
    detail: '时间变量',
    sortGroup: 5,
  }));

  return [...keywords, ...snippets, ...tables, ...fields, ...variableCandidates];
};

const getCompletionContext = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const lineText = model.getLineContent(position.lineNumber).slice(0, position.column - 1);
  const token = lineText.match(SQL_COMPLETION_TOKEN_PATTERN)?.[0] || '';
  const startColumn = Math.max(1, position.column - token.length);

  return {
    token,
    range: new monaco.Range(
      position.lineNumber,
      startColumn,
      position.lineNumber,
      position.column,
    ),
  };
};

export default function SqlCodeEditor({
  value = '',
  onChange,
  placeholder = '请输入 SQL',
  dbType,
  schemaFields = [],
  tableOptions = [],
  variables = [],
  minRows = DEFAULT_MIN_ROWS,
  maxRows = DEFAULT_MAX_ROWS,
  className,
  showLineNumbers = true,
  expandable = true,
  fullscreenTitle = '编辑 SQL',
}: SqlCodeEditorProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor>();
  const modelRef = useRef<monaco.editor.ITextModel>();
  const onChangeRef = useRef(onChange);
  const completionConfigRef = useRef<CompletionConfig>({
    schemaFields,
    tableOptions,
    variables,
  });
  const applyingExternalValueRef = useRef(false);
  const minHeightRef = useRef(0);
  const maxHeightRef = useRef(0);
  const resizeToContentRef = useRef<() => void>();
  const [editorEmpty, setEditorEmpty] = useState(!value);
  const [fullscreenOpen, setFullscreenOpen] = useState(false);
  const [fullscreenValue, setFullscreenValue] = useState(value || '');

  const minHeight = Math.max(minRows, 1) * LINE_HEIGHT + VERTICAL_PADDING;
  const maxHeight = Math.max(maxRows, minRows) * LINE_HEIGHT + VERTICAL_PADDING;

  minHeightRef.current = minHeight;
  maxHeightRef.current = maxHeight;

  const editorStyle = useMemo(
    () =>
      ({
        '--sql-editor-min-height': `${minHeight}px`,
        '--sql-editor-max-height': `${maxHeight}px`,
        '--sql-editor-placeholder-left': showLineNumbers ? '56px' : '12px',
      }) as CSSProperties,
    [minHeight, maxHeight, showLineNumbers],
  );

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    completionConfigRef.current = { schemaFields, tableOptions, variables };
  }, [schemaFields, tableOptions, variables]);

  useEffect(() => {
    if (!fullscreenOpen) setFullscreenValue(value || '');
  }, [fullscreenOpen, value]);

  useEffect(() => {
    if (!containerRef.current) return undefined;

    setupMonacoEnvironment();
    ensureSqlCodeEditorTheme();

    const uri = monaco.Uri.parse(
      `inmemory://yak-ops/sql-code-editor/${++editorSequence}.sql`,
    );
    monaco.editor.getModel(uri)?.dispose();

    const model = monaco.editor.createModel(value || '', 'sql', uri);
    const editor = monaco.editor.create(containerRef.current, {
      model,
      theme: 'yak-sql-code-editor',
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 13,
      lineHeight: LINE_HEIGHT,
      fontFamily:
        "'JetBrains Mono', 'Fira Code', 'SFMono-Regular', Menlo, Monaco, Consolas, 'Liberation Mono', monospace",
      tabSize: 2,
      insertSpaces: true,
      lineNumbers: showLineNumbers ? 'on' : 'off',
      lineNumbersMinChars: 3,
      glyphMargin: false,
      folding: true,
      showFoldingControls: 'mouseover',
      scrollBeyondLastLine: false,
      smoothScrolling: true,
      wordWrap: 'on',
      wrappingIndent: 'same',
      renderLineHighlight: 'all',
      renderWhitespace: 'selection',
      cursorBlinking: 'smooth',
      cursorSmoothCaretAnimation: 'on',
      overviewRulerLanes: 0,
      hideCursorInOverviewRuler: true,
      fixedOverflowWidgets: true,
      padding: { top: 10, bottom: 10 },
      quickSuggestions: {
        other: true,
        comments: false,
        strings: false,
      },
      suggestOnTriggerCharacters: true,
      acceptSuggestionOnEnter: 'on',
      tabCompletion: 'on',
      wordBasedSuggestions: 'off',
      parameterHints: { enabled: false },
      bracketPairColorization: { enabled: true },
      guides: { bracketPairs: true, indentation: true },
      ariaLabel: `${resolveDialectLabel(dbType)} editor`,
      suggest: {
        showWords: false,
        showKeywords: true,
        showSnippets: true,
        showFields: true,
        showStructs: true,
        showVariables: true,
        snippetsPreventQuickSuggestions: false,
      },
    });

    editorRef.current = editor;
    modelRef.current = model;

    const resizeToContent = () => {
      const container = containerRef.current;
      if (!container) return;

      const contentHeight = editor.getContentHeight();
      const nextHeight = Math.min(
        maxHeightRef.current,
        Math.max(minHeightRef.current, contentHeight),
      );
      const nextHeightPx = `${nextHeight}px`;
      if (container.style.height === nextHeightPx) return;
      container.style.height = nextHeightPx;
      editor.layout();
    };
    resizeToContentRef.current = resizeToContent;

    const completionProvider = monaco.languages.registerCompletionItemProvider('sql', {
      triggerCharacters: ['.', '$', '{'],
      provideCompletionItems: (completionModel, position) => {
        if (completionModel.uri.toString() !== model.uri.toString()) {
          return { suggestions: [] };
        }

        if (getLexicalState(getTextBeforePosition(completionModel, position)) !== 'code') {
          return { suggestions: [] };
        }

        const { token, range } = getCompletionContext(completionModel, position);
        const normalizedToken = token.toLowerCase();
        const candidates = buildCompletionCandidates(completionConfigRef.current);
        const matchedCandidates = normalizedToken
          ? candidates.filter((candidate) => {
              const label = candidate.label.toLowerCase();
              return (
                label.startsWith(normalizedToken) ||
                label.split(/\s+/).some((part) => part.startsWith(normalizedToken))
              );
            })
          : candidates;

        return {
          suggestions: matchedCandidates.map<monaco.languages.CompletionItem>((candidate) => ({
            label: candidate.label,
            kind: candidate.kind,
            insertText: candidate.insertText || candidate.label,
            detail: candidate.detail,
            documentation: candidate.documentation,
            filterText: candidate.label,
            sortText: `${candidate.sortGroup}-${candidate.label}`,
            range,
          })),
        };
      },
    });

    const contentDisposable = model.onDidChangeContent(() => {
      const nextValue = model.getValue();
      setEditorEmpty(nextValue.length === 0);
      resizeToContent();
      if (!applyingExternalValueRef.current) onChangeRef.current(nextValue);
    });
    const sizeDisposable = editor.onDidContentSizeChange(resizeToContent);
    const animationFrame = window.requestAnimationFrame(resizeToContent);
    return () => {
      window.cancelAnimationFrame(animationFrame);
      resizeToContentRef.current = undefined;
      sizeDisposable.dispose();
      contentDisposable.dispose();
      completionProvider.dispose();
      editor.dispose();
      model.dispose();
      editorRef.current = undefined;
      modelRef.current = undefined;
    };
  }, []);

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor) return;
    editor.updateOptions({
      lineNumbers: showLineNumbers ? 'on' : 'off',
      ariaLabel: `${resolveDialectLabel(dbType)} editor`,
    });
  }, [dbType, showLineNumbers]);

  useEffect(() => {
    resizeToContentRef.current?.();
  }, [minHeight, maxHeight]);

  useEffect(() => {
    const model = modelRef.current;
    if (!model) return;

    const nextValue = value || '';
    if (model.getValue() === nextValue) {
      setEditorEmpty(nextValue.length === 0);
      return;
    }

    applyingExternalValueRef.current = true;
    model.pushEditOperations(
      [],
      [{ range: model.getFullModelRange(), text: nextValue }],
      () => null,
    );
    applyingExternalValueRef.current = false;
    setEditorEmpty(nextValue.length === 0);
    resizeToContentRef.current?.();
  }, [value]);

  const handleOpenFullscreen = () => {
    setFullscreenValue(value || '');
    setFullscreenOpen(true);
  };

  const handleCancelFullscreen = () => {
    setFullscreenValue(value || '');
    setFullscreenOpen(false);
  };

  const handleApplyFullscreen = () => {
    onChangeRef.current(fullscreenValue);
    setFullscreenOpen(false);
  };

  return (
    <>
      <div
        className={classNames(
          'sql-code-editor',
          {
            'sql-code-editor--expandable': expandable,
          },
          className,
        )}
        style={editorStyle}
      >
        <div ref={containerRef} className="sql-code-editor__monaco" />
        {editorEmpty ? (
          <div className="sql-code-editor__placeholder">{placeholder}</div>
        ) : null}
        {expandable ? (
          <YakButton
            type="text"
            size="small"
            className="sql-code-editor__expand-btn !text-[#667085]"
            icon={<Maximize2 size={14} />}
            aria-label="展开 SQL 编辑器"
            title="展开编辑"
            onClick={handleOpenFullscreen}
          />
        ) : null}
      </div>

      {expandable ? (
        <Modal
          open={fullscreenOpen}
          title={fullscreenTitle}
          centered
          width="min(920px, calc(100vw - 48px))"
          className="sql-code-editor-modal"
          destroyOnClose
          maskClosable={false}
          onCancel={handleCancelFullscreen}
          footer={
            <div className="sql-code-editor-modal__footer">
              <YakButton
                type="text"
                className="!text-[#667085]"
                onClick={handleCancelFullscreen}
              >
                取消
              </YakButton>
              <YakButton type="primary" onClick={handleApplyFullscreen}>
                应用
              </YakButton>
            </div>
          }
        >
          <div className="sql-code-editor-modal__body">
            <SqlCodeEditor
              value={fullscreenValue}
              onChange={setFullscreenValue}
              placeholder={placeholder}
              dbType={dbType}
              schemaFields={schemaFields}
              tableOptions={tableOptions}
              variables={variables}
              minRows={FULLSCREEN_MIN_ROWS}
              maxRows={FULLSCREEN_MAX_ROWS}
              className="sql-code-editor--fullscreen"
              showLineNumbers={showLineNumbers}
              expandable={false}
            />
          </div>
        </Modal>
      ) : null}
    </>
  );
}