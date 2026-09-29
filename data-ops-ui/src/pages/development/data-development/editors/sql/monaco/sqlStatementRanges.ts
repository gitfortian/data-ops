export interface SqlStatementRange {
  startOffset: number;
  endOffset: number;
  startLine: number;
  endLine: number;
  sql: string;
}

type SqlScanMode =
  | 'normal'
  | 'single-quote'
  | 'double-quote'
  | 'backtick'
  | 'bracket'
  | 'line-comment'
  | 'block-comment';

const buildLineStarts = (sql: string) => {
  const starts = [0];
  for (let index = 0; index < sql.length; index += 1) {
    if (sql[index] === '\n') starts.push(index + 1);
  }
  return starts;
};

const lineNumberAtOffset = (lineStarts: number[], offset: number) => {
  let low = 0;
  let high = lineStarts.length - 1;
  while (low <= high) {
    const middle = Math.floor((low + high) / 2);
    if (lineStarts[middle] <= offset) {
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }
  return Math.max(1, high + 1);
};

const trimSegment = (sql: string, start: number, end: number) => {
  let trimmedStart = start;
  let trimmedEnd = end;
  while (trimmedStart < trimmedEnd && /\s/.test(sql[trimmedStart])) {
    trimmedStart += 1;
  }
  while (trimmedEnd > trimmedStart && /\s/.test(sql[trimmedEnd - 1])) {
    trimmedEnd -= 1;
  }
  return { start: trimmedStart, end: trimmedEnd };
};

/**
 * Split editor SQL into executable statement ranges without treating semicolons
 * inside strings, quoted identifiers, or comments as statement delimiters.
 */
export const getSqlStatementRanges = (sql: string): SqlStatementRange[] => {
  if (!sql.trim()) return [];

  const segmentEnds: number[] = [];
  let mode: SqlScanMode = 'normal';
  let index = 0;

  while (index < sql.length) {
    const current = sql[index];
    const next = sql[index + 1];

    if (mode === 'line-comment') {
      if (current === '\n') mode = 'normal';
      index += 1;
      continue;
    }

    if (mode === 'block-comment') {
      if (current === '*' && next === '/') {
        mode = 'normal';
        index += 2;
      } else {
        index += 1;
      }
      continue;
    }

    if (mode === 'bracket') {
      if (current === ']' && next === ']') {
        index += 2;
      } else if (current === ']') {
        mode = 'normal';
        index += 1;
      } else {
        index += 1;
      }
      continue;
    }

    if (
      mode === 'single-quote' ||
      mode === 'double-quote' ||
      mode === 'backtick'
    ) {
      const quote =
        mode === 'single-quote' ? "'" : mode === 'double-quote' ? '"' : '`';
      if (current === '\\' && next !== undefined) {
        index += 2;
      } else if (current === quote && next === quote) {
        index += 2;
      } else if (current === quote) {
        mode = 'normal';
        index += 1;
      } else {
        index += 1;
      }
      continue;
    }

    if (current === '-' && next === '-') {
      mode = 'line-comment';
      index += 2;
      continue;
    }
    if (current === '/' && next === '*') {
      mode = 'block-comment';
      index += 2;
      continue;
    }
    if (current === "'") {
      mode = 'single-quote';
      index += 1;
      continue;
    }
    if (current === '"') {
      mode = 'double-quote';
      index += 1;
      continue;
    }
    if (current === '`') {
      mode = 'backtick';
      index += 1;
      continue;
    }
    if (current === '[') {
      mode = 'bracket';
      index += 1;
      continue;
    }
    if (current === ';') segmentEnds.push(index + 1);
    index += 1;
  }

  if (!segmentEnds.length || segmentEnds[segmentEnds.length - 1] < sql.length) {
    segmentEnds.push(sql.length);
  }

  const lineStarts = buildLineStarts(sql);
  const ranges: SqlStatementRange[] = [];
  let segmentStart = 0;

  segmentEnds.forEach((segmentEnd) => {
    const trimmed = trimSegment(sql, segmentStart, segmentEnd);
    segmentStart = segmentEnd;
    if (trimmed.start >= trimmed.end) return;

    const endOffsetForLine = Math.max(trimmed.start, trimmed.end - 1);
    ranges.push({
      startOffset: trimmed.start,
      endOffset: trimmed.end,
      startLine: lineNumberAtOffset(lineStarts, trimmed.start),
      endLine: lineNumberAtOffset(lineStarts, endOffsetForLine),
      sql: sql.slice(trimmed.start, trimmed.end),
    });
  });

  return ranges;
};
