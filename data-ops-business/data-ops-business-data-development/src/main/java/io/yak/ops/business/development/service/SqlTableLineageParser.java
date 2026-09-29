package io.yak.ops.business.development.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

/**
 * Extracts table-level dependencies from SQL without coupling the generic lineage module to SQL.
 *
 * <p>V1 intentionally focuses on SELECT / INSERT / UPDATE / DELETE / CREATE TABLE / TRUNCATE.
 * CTEs, joins, subqueries and unions are handled by JSQLParser's table visitor.
 */
@Component
public class SqlTableLineageParser {

  public ParseResult parse(String sql) {
    if (sql == null || sql.isBlank()) {
      return new ParseResult(List.of(), List.of(), 0);
    }

    try {
      Statements parsed = CCJSqlParserUtil.parseStatements(sql);
      Map<String, TableRef> inputs = new LinkedHashMap<>();
      Map<String, TableRef> outputs = new LinkedHashMap<>();
      int statementCount = 0;

      for (Statement statement : parsed.getStatements()) {
        statementCount++;
        StatementTables tables = analyze(statement);
        tables.inputs().forEach(table -> inputs.putIfAbsent(table.canonicalName(), table));
        tables.outputs().forEach(table -> outputs.putIfAbsent(table.canonicalName(), table));
      }

      return new ParseResult(
          inputs.values().stream().sorted(Comparator.comparing(TableRef::canonicalName)).toList(),
          outputs.values().stream().sorted(Comparator.comparing(TableRef::canonicalName)).toList(),
          statementCount);
    } catch (JSQLParserException | RuntimeException exception) {
      if (exception instanceof SqlLineageParseException parseException) {
        throw parseException;
      }
      throw new SqlLineageParseException(
          "SQL 表级血缘解析失败：" + safeMessage(exception), exception);
    }
  }

  private StatementTables analyze(Statement statement) {
    TablesNamesFinder finder = new TablesNamesFinder();
    Set<String> discovered = finder.getTables(statement);
    LinkedHashSet<TableRef> inputs = new LinkedHashSet<>();
    for (String tableName : discovered) {
      TableRef ref = tableRef(tableName);
      if (ref != null) inputs.add(ref);
    }

    LinkedHashSet<TableRef> outputs = new LinkedHashSet<>();
    TableRef target = target(statement);
    if (target != null) {
      outputs.add(target);
      if (statement instanceof Insert || statement instanceof CreateTable || statement instanceof Truncate) {
        inputs.removeIf(input -> input.canonicalName().equals(target.canonicalName()));
      } else if (statement instanceof Update || statement instanceof Delete) {
        inputs.add(target);
      }
    }

    if (!(statement instanceof Select
        || statement instanceof Insert
        || statement instanceof Update
        || statement instanceof Delete
        || statement instanceof CreateTable
        || statement instanceof Truncate)) {
      // Unknown statements may still expose read tables through TablesNamesFinder. Keep those inputs
      // but do not guess a write target.
    }
    return new StatementTables(List.copyOf(inputs), List.copyOf(outputs));
  }

  private TableRef target(Statement statement) {
    Table table = null;
    if (statement instanceof Insert insert) {
      table = insert.getTable();
    } else if (statement instanceof Update update) {
      table = update.getTable();
    } else if (statement instanceof Delete delete) {
      table = delete.getTable();
    } else if (statement instanceof CreateTable createTable) {
      table = createTable.getTable();
    } else if (statement instanceof Truncate truncate) {
      table = truncate.getTable();
    }
    return table == null ? null : tableRef(table.getFullyQualifiedName());
  }

  private TableRef tableRef(String rawName) {
    if (rawName == null || rawName.isBlank()) return null;
    String cleaned = cleanQualifiedName(rawName);
    if (cleaned.isBlank()) return null;

    List<String> parts = new ArrayList<>();
    for (String part : cleaned.split("\\.")) {
      String normalized = stripIdentifierQuotes(part.trim());
      if (!normalized.isBlank()) parts.add(normalized);
    }
    if (parts.isEmpty()) return null;

    String tableName = parts.get(parts.size() - 1);
    String schemaName = parts.size() >= 2 ? parts.get(parts.size() - 2) : null;
    String databaseName = parts.size() >= 3 ? parts.get(parts.size() - 3) : null;
    String qualifiedName = String.join(".", parts);
    return new TableRef(
        qualifiedName.toLowerCase(Locale.ROOT),
        qualifiedName,
        databaseName,
        schemaName,
        tableName);
  }

  private String cleanQualifiedName(String value) {
    return value.trim();
  }

  private String stripIdentifierQuotes(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '`' && last == '`')
          || (first == '"' && last == '"')
          || (first == '[' && last == ']')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "unknown parser error" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  public record ParseResult(
      List<TableRef> inputs,
      List<TableRef> outputs,
      int statementCount) {
  }

  public record TableRef(
      String canonicalName,
      String qualifiedName,
      String databaseName,
      String schemaName,
      String tableName) {
  }

  private record StatementTables(List<TableRef> inputs, List<TableRef> outputs) {
  }

  public static final class SqlLineageParseException extends RuntimeException {
    SqlLineageParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
