import type { DevelopmentSqlDialect } from '@/services/data-development';

import { resolveSqlEditorProfile } from '../profiles/sqlEditorProfiles';

const protectSqlSegments = (sql: string) => {
  const segments: string[] = [];
  let output = '';
  let index = 0;

  const pushSegment = (value: string) => {
    const token = `__YAK_SQL_SEGMENT_${segments.length}__`;
    segments.push(value);
    output += token;
  };

  while (index < sql.length) {
    const current = sql[index];
    const next = sql[index + 1];

    if (current === "'" || current === '"' || current === '`') {
      const quote = current;
      let end = index + 1;
      while (end < sql.length) {
        if (sql[end] === quote && sql[end + 1] === quote) {
          end += 2;
          continue;
        }
        if (sql[end] === quote) {
          end += 1;
          break;
        }
        end += 1;
      }
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    if (current === '[') {
      let end = index + 1;
      while (end < sql.length) {
        if (sql[end] === ']' && sql[end + 1] === ']') {
          end += 2;
          continue;
        }
        if (sql[end] === ']') {
          end += 1;
          break;
        }
        end += 1;
      }
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    if (current === '-' && next === '-') {
      let end = index + 2;
      while (end < sql.length && sql[end] !== '\n') end += 1;
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    if (current === '/' && next === '*') {
      let end = index + 2;
      while (
        end < sql.length &&
        !(sql[end] === '*' && sql[end + 1] === '/')
      ) {
        end += 1;
      }
      end = Math.min(sql.length, end + 2);
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    output += current;
    index += 1;
  }

  return { output, segments };
};

const restoreSqlSegments = (sql: string, segments: string[]) =>
  segments.reduce(
    (value, segment, index) =>
      value.replaceAll(`__YAK_SQL_SEGMENT_${index}__`, segment),
    sql,
  );

const BASE_CLAUSES = [
  'LEFT OUTER JOIN',
  'RIGHT OUTER JOIN',
  'FULL OUTER JOIN',
  'LEFT JOIN',
  'RIGHT JOIN',
  'FULL JOIN',
  'INNER JOIN',
  'CROSS JOIN',
  'GROUP BY',
  'ORDER BY',
  'UNION ALL',
  'UNION',
  'FROM',
  'WHERE',
  'HAVING',
  'JOIN',
  'SET',
  'VALUES',
  'LIMIT',
  'OFFSET',
] as const;

const escapeRegExp = (value: string) =>
  value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const clausePattern = (value: string) =>
  value
    .trim()
    .split(/\s+/)
    .map(escapeRegExp)
    .join('\\s+');

const buildClausePattern = (dialect?: DevelopmentSqlDialect | string) => {
  const profile = resolveSqlEditorProfile(dialect);
  const clauses = [...BASE_CLAUSES, ...profile.formatterClauses].sort(
    (left, right) => right.length - left.length,
  );
  return clauses.map(clausePattern).join('|');
};

export const formatSqlText = (
  sql: string,
  dialect?: DevelopmentSqlDialect | string,
) => {
  if (!sql.trim()) return sql;

  const normalized = sql.replace(/\r\n?/g, '\n');
  const { output, segments } = protectSqlSegments(normalized);
  const pattern = buildClausePattern(dialect);

  let formatted = output
    .replace(/[ \t]+/g, ' ')
    .replace(/ *\n */g, '\n')
    .replace(new RegExp(`\\s+(${pattern})\\s+`, 'gi'), '\n$1 ')
    .replace(/\s+(AND|OR)\s+/gi, '\n  $1 ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();

  formatted = restoreSqlSegments(formatted, segments);
  return formatted;
};
