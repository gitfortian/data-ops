package io.yak.ops.business.development.dataservice;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;

/** SQL validation and named-parameter compilation owned by Data Development authoring. */
public class DevelopmentDataServiceSqlCompiler {

  public CompiledSql compile(String sql, Map<String, ?> parameters) {
    validateSelectOnly(sql);
    Map<String, ?> values = parameters == null ? Map.of() : parameters;
    for (String name : parameterNames(sql)) {
      if (!values.containsKey(name)) {
        throw new IllegalArgumentException("缺少请求参数：" + name);
      }
    }
    try {
      MapSqlParameterSource source = new MapSqlParameterSource(values);
      ParsedSql parsed = NamedParameterUtils.parseSqlStatement(sql);
      String jdbcSql = NamedParameterUtils.substituteNamedParameters(parsed, source);
      Object[] boundValues = NamedParameterUtils.buildValueArray(parsed, source, null);
      return new CompiledSql(
          jdbcSql,
          boundValues == null ? List.of() : Arrays.asList(boundValues));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("SQL 参数绑定失败：" + exception.getMessage(), exception);
    }
  }

  /** Discovers :name parameters while ignoring quoted content, comments and PostgreSQL :: casts. */
  public List<String> parameterNames(String sql) {
    validateSelectOnly(sql);
    Set<String> names = new LinkedHashSet<>();
    boolean singleQuote = false;
    boolean doubleQuote = false;
    boolean backtick = false;
    boolean lineComment = false;
    boolean blockComment = false;

    for (int index = 0; index < sql.length(); index++) {
      char current = sql.charAt(index);
      char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

      if (lineComment) {
        if (current == '\n' || current == '\r') lineComment = false;
        continue;
      }
      if (blockComment) {
        if (current == '*' && next == '/') {
          blockComment = false;
          index++;
        }
        continue;
      }
      if (!singleQuote && !doubleQuote && !backtick) {
        if (current == '-' && next == '-') {
          lineComment = true;
          index++;
          continue;
        }
        if (current == '/' && next == '*') {
          blockComment = true;
          index++;
          continue;
        }
      }
      if (!doubleQuote && !backtick && current == '\'') {
        if (singleQuote && next == '\'') {
          index++;
          continue;
        }
        singleQuote = !singleQuote;
        continue;
      }
      if (!singleQuote && !backtick && current == '"') {
        doubleQuote = !doubleQuote;
        continue;
      }
      if (!singleQuote && !doubleQuote && current == '`') {
        backtick = !backtick;
        continue;
      }
      if (singleQuote || doubleQuote || backtick || current != ':') continue;
      if (next == ':' || (index > 0 && sql.charAt(index - 1) == ':')) continue;
      if (!(Character.isLetter(next) || next == '_')) continue;

      int end = index + 2;
      while (end < sql.length()) {
        char candidate = sql.charAt(end);
        if (!(Character.isLetterOrDigit(candidate) || candidate == '_')) break;
        end++;
      }
      names.add(sql.substring(index + 1, end));
      index = end - 1;
    }
    return List.copyOf(names);
  }

  public void validateSelectOnly(String sql) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }
    try {
      Statements statements = CCJSqlParserUtil.parseStatements(sql);
      if (statements.getStatements().size() != 1
          || !(statements.getStatements().getFirst() instanceof Select)) {
        throw new IllegalArgumentException("Data Service Node 第一阶段仅支持单条 SELECT 查询");
      }
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL 解析失败：" + exception.getMessage(), exception);
    }
  }

  public record CompiledSql(String sql, List<Object> parameters) {}
}
