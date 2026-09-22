import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

type SqlScanState =
  | 'code'
  | 'single-quote'
  | 'double-quote'
  | 'backtick'
  | 'bracket-identifier'
  | 'line-comment'
  | 'block-comment';

const MARKER_OWNER = 'yak-sql-structure';
const MAX_PARENTHESES_MARKERS = 20;
const DIAGNOSTIC_DELAY = 180;

const markerAtOffset = (
  model: monaco.editor.ITextModel,
  offset: number,
  message: string,
): monaco.editor.IMarkerData => {
  const valueLength = model.getValueLength();
  const safeStart = Math.max(0, Math.min(offset, Math.max(0, valueLength - 1)));
  const safeEnd = Math.max(safeStart + 1, Math.min(valueLength, safeStart + 1));
  const start = model.getPositionAt(safeStart);
  const end = model.getPositionAt(safeEnd);

  return {
    severity: monaco.MarkerSeverity.Warning,
    message,
    startLineNumber: start.lineNumber,
    startColumn: start.column,
    endLineNumber: end.lineNumber,
    endColumn:
      start.lineNumber === end.lineNumber && end.column <= start.column
        ? start.column + 1
        : end.column,
  };
};

const analyzeSqlStructure = (
  model: monaco.editor.ITextModel,
): monaco.editor.IMarkerData[] => {
  const sql = model.getValue();
  if (!sql) return [];

  const markers: monaco.editor.IMarkerData[] = [];
  const parentheses: number[] = [];
  let state: SqlScanState = 'code';
  let stateStart = -1;

  for (let index = 0; index < sql.length; index += 1) {
    const current = sql[index];
    const next = sql[index + 1];

    if (state === 'line-comment') {
      if (current === '\n') {
        state = 'code';
        stateStart = -1;
      }
      continue;
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        state = 'code';
        stateStart = -1;
        index += 1;
      }
      continue;
    }
    if (state === 'single-quote') {
      if (current === "'" && next === "'") {
        index += 1;
        continue;
      }
      if (current === "'") {
        state = 'code';
        stateStart = -1;
      }
      continue;
    }
    if (state === 'double-quote') {
      if (current === '"' && next === '"') {
        index += 1;
        continue;
      }
      if (current === '"') {
        state = 'code';
        stateStart = -1;
      }
      continue;
    }
    if (state === 'backtick') {
      if (current === '`' && next === '`') {
        index += 1;
        continue;
      }
      if (current === '`') {
        state = 'code';
        stateStart = -1;
      }
      continue;
    }
    if (state === 'bracket-identifier') {
      if (current === ']' && next === ']') {
        index += 1;
        continue;
      }
      if (current === ']') {
        state = 'code';
        stateStart = -1;
      }
      continue;
    }

    if (current === '-' && next === '-') {
      state = 'line-comment';
      stateStart = index;
      index += 1;
      continue;
    }
    if (current === '/' && next === '*') {
      state = 'block-comment';
      stateStart = index;
      index += 1;
      continue;
    }
    if (current === "'") {
      state = 'single-quote';
      stateStart = index;
      continue;
    }
    if (current === '"') {
      state = 'double-quote';
      stateStart = index;
      continue;
    }
    if (current === '`') {
      state = 'backtick';
      stateStart = index;
      continue;
    }
    if (current === '[') {
      state = 'bracket-identifier';
      stateStart = index;
      continue;
    }

    if (current === '(') {
      parentheses.push(index);
      continue;
    }
    if (current === ')') {
      if (parentheses.length) {
        parentheses.pop();
      } else {
        markers.push(markerAtOffset(model, index, '右括号没有匹配的左括号'));
      }
    }
  }

  if (state === 'block-comment' && stateStart >= 0) {
    markers.push(markerAtOffset(model, stateStart, '块注释未闭合'));
  } else if (state === 'single-quote' && stateStart >= 0) {
    markers.push(markerAtOffset(model, stateStart, '字符串未闭合'));
  } else if (
    (state === 'double-quote' ||
      state === 'backtick' ||
      state === 'bracket-identifier') &&
    stateStart >= 0
  ) {
    markers.push(markerAtOffset(model, stateStart, '标识符引号未闭合'));
  }

  parentheses
    .slice(-MAX_PARENTHESES_MARKERS)
    .forEach((offset) =>
      markers.push(markerAtOffset(model, offset, '左括号没有匹配的右括号')),
    );

  return markers;
};

export const bindSqlDiagnostics = (
  model: monaco.editor.ITextModel,
): monaco.IDisposable => {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let disposed = false;

  const run = () => {
    if (disposed) return;
    monaco.editor.setModelMarkers(model, MARKER_OWNER, analyzeSqlStructure(model));
  };

  const schedule = () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(run, DIAGNOSTIC_DELAY);
  };

  const contentDisposable = model.onDidChangeContent(schedule);
  run();

  return {
    dispose: () => {
      if (disposed) return;
      disposed = true;
      if (timer) clearTimeout(timer);
      contentDisposable.dispose();
      monaco.editor.setModelMarkers(model, MARKER_OWNER, []);
    },
  };
};
