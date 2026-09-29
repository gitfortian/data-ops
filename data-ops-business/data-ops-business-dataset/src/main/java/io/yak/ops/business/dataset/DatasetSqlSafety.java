package io.yak.ops.business.dataset;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative guard for SQL used as a QUERY_REVISION Dataset source. */
public final class DatasetSqlSafety {

  private static final int MAX_SQL_LENGTH = 500_000;
  private static final Pattern FIRST_KEYWORD = Pattern.compile("(?i)\\b([a-z]+)\\b");
  private static final Pattern FORBIDDEN = Pattern.compile(
      "(?i)\\b(INSERT|UPDATE|DELETE|MERGE|ALTER|DROP|CREATE|TRUNCATE|REPLACE|CALL|EXEC|EXECUTE|"
          + "GRANT|REVOKE|LOAD|COPY|LOCK|UNLOCK|SET|USE|INTO)\\b");
  private static final Pattern FOR_UPDATE = Pattern.compile("(?i)\\bFOR\\s+UPDATE\\b");

  private DatasetSqlSafety() {
  }

    public static String requireReadOnlyQuery(String sql) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("Dataset 来源 SQL 不能为空");
    }
    String normalized = sql.trim();
    if (normalized.length() > MAX_SQL_LENGTH) {
      throw new IllegalArgumentException("Dataset 来源 SQL 过长");
    }

    String visible = visibleSql(normalized);
    int last = lastNonWhitespace(visible);
    if (last >= 0 && visible.charAt(last) == ';') {
      visible = visible.substring(0, last);
      normalized = normalized.substring(0, Math.min(last, normalized.length())).trim();
    }
    if (visible.indexOf(';') >= 0) {
      throw new IllegalArgumentException("Dataset 仅允许单条只读查询，不能包含多语句");
    }

    Matcher first = FIRST_KEYWORD.matcher(visible);
    if (!first.find()) throw new IllegalArgumentException("Dataset 来源 SQL 无法识别查询语句");
    String keyword = first.group(1).toUpperCase(Locale.ROOT);
    if (!"SELECT".equals(keyword) && !"WITH".equals(keyword)) {
      throw new IllegalArgumentException("Dataset 仅允许 SELECT / WITH 查询");
    }
    if (FORBIDDEN.matcher(visible).find() || FOR_UPDATE.matcher(visible).find()) {
      throw new IllegalArgumentException("Dataset 来源 SQL 必须是无副作用的只读查询");
    }
    return normalized;
  }

  private static int lastNonWhitespace(String value) {
    for (int i = value.length() - 1; i >= 0; i--) {
      if (!Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  /** Replaces comments and quoted content with spaces so validation only sees executable tokens. */
  private static String visibleSql(String sql) {
    StringBuilder out = new StringBuilder(sql.length());
    State state = State.NORMAL;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
      switch (state) {
        case NORMAL -> {
          if (c == '\'' ) {
            state = State.SINGLE_QUOTE;
            out.append(' ');
          } else if (c == '"') {
            state = State.DOUBLE_QUOTE;
            out.append(' ');
          } else if (c == '`') {
            state = State.BACKTICK;
            out.append(' ');
          } else if (c == '-' && next == '-') {
            state = State.LINE_COMMENT;
            out.append("  ");
            i++;
          } else if (c == '#') {
            state = State.LINE_COMMENT;
            out.append(' ');
          } else if (c == '/' && next == '*') {
            state = State.BLOCK_COMMENT;
            out.append("  ");
            i++;
          } else {
            out.append(c);
          }
        }
        case SINGLE_QUOTE -> {
          out.append(' ');
          if (c == '\\' && next != '\0') {
            out.append(' ');
            i++;
          } else if (c == '\'' && next == '\'') {
            out.append(' ');
            i++;
          } else if (c == '\'') {
            state = State.NORMAL;
          }
        }
        case DOUBLE_QUOTE -> {
          out.append(' ');
          if (c == '\\' && next != '\0') {
            out.append(' ');
            i++;
          } else if (c == '"' && next == '"') {
            out.append(' ');
            i++;
          } else if (c == '"') {
            state = State.NORMAL;
          }
        }
        case BACKTICK -> {
          out.append(' ');
          if (c == '`' && next == '`') {
            out.append(' ');
            i++;
          } else if (c == '`') {
            state = State.NORMAL;
          }
        }
        case LINE_COMMENT -> {
          if (c == '\n' || c == '\r') {
            state = State.NORMAL;
            out.append(c);
          } else {
            out.append(' ');
          }
        }
        case BLOCK_COMMENT -> {
          out.append(' ');
          if (c == '*' && next == '/') {
            out.append(' ');
            i++;
            state = State.NORMAL;
          }
        }
      }
    }
    if (state == State.SINGLE_QUOTE || state == State.DOUBLE_QUOTE
        || state == State.BACKTICK || state == State.BLOCK_COMMENT) {
      throw new IllegalArgumentException("Dataset 来源 SQL 包含未闭合的字符串、标识符或注释");
    }
    return out.toString();
  }

  private enum State {
    NORMAL,
    SINGLE_QUOTE,
    DOUBLE_QUOTE,
    BACKTICK,
    LINE_COMMENT,
    BLOCK_COMMENT
  }
}
