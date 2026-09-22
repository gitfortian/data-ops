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
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import org.springframework.stereotype.Component;

/**
 * Extracts conservative column-to-column lineage from SQL ASTs.
 *
 * <p>The parser never guesses. Without schema metadata it preserves the V1 conservative behavior.
 * When a {@link SchemaProvider} is supplied it can expand stars, disambiguate unqualified columns
 * across joins and align INSERT projections with physical target-column order.
 */
@Component
public class SqlColumnLineageParser {

  private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
      "AVG", "COUNT", "GROUP_CONCAT", "MAX", "MIN", "SUM",
      "STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VAR_POP", "VAR_SAMP", "VARIANCE");

  public ParseResult parse(String sql) {
    return parse(sql, SchemaProvider.none());
  }

  public ParseResult parse(String sql, SchemaProvider schemaProvider) {
    if (sql == null || sql.isBlank()) {
      return new ParseResult(List.of(), 0, 0, 0);
    }

    SchemaProvider provider = schemaProvider == null ? SchemaProvider.none() : schemaProvider;
    try {
      Statements parsed = CCJSqlParserUtil.parseStatements(sql);
      Map<String, ColumnMapping> mappings = new LinkedHashMap<>();
      MutableStats stats = new MutableStats();
      int statementIndex = 0;

      for (Statement statement : parsed.getStatements()) {
        statementIndex++;
        analyze(statement, statementIndex, mappings, stats, provider);
      }

      return new ParseResult(
          List.copyOf(mappings.values()),
          statementIndex,
          stats.candidateOutputCount,
          stats.unresolvedReferenceCount);
    } catch (JSQLParserException | RuntimeException exception) {
      if (exception instanceof SqlColumnLineageParseException parseException) {
        throw parseException;
      }
      throw new SqlColumnLineageParseException(
          "SQL 字段级血缘解析失败：" + safeMessage(exception), exception);
    }
  }

  private void analyze(
      Statement statement,
      int statementIndex,
      Map<String, ColumnMapping> mappings,
      MutableStats stats,
      SchemaProvider schemaProvider) {
    if (statement instanceof Insert insert) {
      analyzeInsert(insert, statementIndex, mappings, stats, schemaProvider);
    } else if (statement instanceof CreateTable createTable) {
      analyzeCreateTable(createTable, statementIndex, mappings, stats, schemaProvider);
    } else if (statement instanceof Update update) {
      analyzeUpdate(update, statementIndex, mappings, stats, schemaProvider);
    }
  }

  private void analyzeInsert(
      Insert insert,
      int statementIndex,
      Map<String, ColumnMapping> mappings,
      MutableStats stats,
      SchemaProvider schemaProvider) {
    SqlTableLineageParser.TableRef target = tableRef(insert.getTable());
    Select select = insert.getSelect();
    if (target == null || select == null) return;

    List<String> targetColumns = new ArrayList<>();
    if (insert.getColumns() != null) {
      for (Column column : insert.getColumns()) {
        targetColumns.add(normalizeColumnName(column.getColumnName()));
      }
    }

    if (targetColumns.isEmpty()) {
      targetColumns = schemaColumnNames(schemaProvider, target);
    }
    if (targetColumns.isEmpty()) {
      stats.unresolvedReferenceCount++;
      return;
    }

    analyzeSelect(
        select,
        target,
        targetColumns,
        statementIndex,
        mappings,
        stats,
        cteNames(insert.getWithItemsList()),
        schemaProvider);
  }

  private void analyzeCreateTable(
      CreateTable createTable,
      int statementIndex,
      Map<String, ColumnMapping> mappings,
      MutableStats stats,
      SchemaProvider schemaProvider) {
    SqlTableLineageParser.TableRef target = tableRef(createTable.getTable());
    Select select = createTable.getSelect();
    if (target == null || select == null) return;

    List<String> targetColumns = new ArrayList<>();
    if (createTable.getColumns() != null) {
      for (String column : createTable.getColumns()) {
        targetColumns.add(normalizeColumnName(column));
      }
    }
    if (targetColumns.isEmpty()) {
      targetColumns = inferSelectOutputNames(select, schemaProvider, Set.of());
    }

    analyzeSelect(
        select,
        target,
        targetColumns,
        statementIndex,
        mappings,
        stats,
        Set.of(),
        schemaProvider);
  }

  private void analyzeUpdate(
      Update update,
      int statementIndex,
      Map<String, ColumnMapping> mappings,
      MutableStats stats,
      SchemaProvider schemaProvider) {
    SqlTableLineageParser.TableRef target = tableRef(update.getTable());
    if (target == null || update.getUpdateSets() == null) return;

    Set<String> cteNames = cteNames(update.getWithItemsList());
    Scope scope = new Scope();
    addFromItem(scope, update.getTable(), cteNames);
    addFromItem(scope, update.getFromItem(), cteNames);
    addJoins(scope, update.getStartJoins(), cteNames);
    addJoins(scope, update.getJoins(), cteNames);

    int outputOrdinal = 0;
    for (UpdateSet updateSet : update.getUpdateSets()) {
      int count = Math.min(updateSet.getColumns().size(), updateSet.getValues().size());
      for (int i = 0; i < count; i++) {
        outputOrdinal++;
        stats.candidateOutputCount++;

        String targetColumn = normalizeColumnName(updateSet.getColumn(i).getColumnName());
        Expression expression = updateSet.getValue(i);
        if (targetColumn == null || expression == null) {
          stats.unresolvedReferenceCount++;
          continue;
        }

        collectMappings(
            expression,
            target,
            targetColumn,
            scope,
            statementIndex,
            outputOrdinal,
            mappings,
            stats,
            schemaProvider);
      }
      if (updateSet.getColumns().size() != updateSet.getValues().size()) {
        stats.unresolvedReferenceCount +=
            Math.abs(updateSet.getColumns().size() - updateSet.getValues().size());
      }
    }
  }

  private Set<String> cteNames(List<WithItem> withItems) {
    if (withItems == null || withItems.isEmpty()) return Set.of();
    Set<String> names = new LinkedHashSet<>();
    for (WithItem withItem : withItems) {
      if (withItem != null
          && withItem.getAlias() != null
          && withItem.getAlias().getName() != null) {
        names.add(withItem.getAlias().getName().toLowerCase(Locale.ROOT));
      }
    }
    return names;
  }

  private void analyzeSelect(
      Select select,
      SqlTableLineageParser.TableRef target,
      List<String> targetColumns,
      int statementIndex,
      Map<String, ColumnMapping> mappings,
      MutableStats stats,
      Set<String> inheritedCteNames,
      SchemaProvider schemaProvider) {
    if (select == null) return;

    Set<String> cteNames = new LinkedHashSet<>(inheritedCteNames);
    if (select.getWithItemsList() != null) {
      for (WithItem withItem : select.getWithItemsList()) {
        if (withItem.getAlias() != null && withItem.getAlias().getName() != null) {
          cteNames.add(withItem.getAlias().getName().toLowerCase(Locale.ROOT));
        }
      }
    }

    if (select instanceof PlainSelect plainSelect) {
      analyzePlainSelect(
          plainSelect,
          target,
          targetColumns,
          statementIndex,
          mappings,
          stats,
          cteNames,
          schemaProvider);
      return;
    }

    if (select instanceof SetOperationList setOperationList) {
      if (setOperationList.getSelects() == null) return;
      for (Select child : setOperationList.getSelects()) {
        analyzeSelect(
            child,
            target,
            targetColumns,
            statementIndex,
            mappings,
            stats,
            cteNames,
            schemaProvider);
      }
      return;
    }

    stats.unresolvedReferenceCount++;
  }

  private void analyzePlainSelect(
      PlainSelect select,
      SqlTableLineageParser.TableRef target,
      List<String> targetColumns,
      int statementIndex,
      Map<String, ColumnMapping> mappings,
      MutableStats stats,
      Set<String> cteNames,
      SchemaProvider schemaProvider) {
    if (select.getSelectItems() == null) return;

    Scope scope = buildScope(select, cteNames);
    int outputOrdinal = 0;
    for (SelectItem<?> item : select.getSelectItems()) {
      Expression expression = item == null ? null : item.getExpression();

      if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
        List<SourceColumnRef> expanded = expandStar(expression, scope, schemaProvider);
        if (expanded.isEmpty()) {
          outputOrdinal++;
          stats.candidateOutputCount++;
          stats.unresolvedReferenceCount++;
          continue;
        }

        for (SourceColumnRef source : expanded) {
          outputOrdinal++;
          stats.candidateOutputCount++;
          String targetColumn = targetColumn(targetColumns, outputOrdinal);
          if (targetColumn == null) {
            stats.unresolvedReferenceCount++;
            continue;
          }
          putMapping(
              new ColumnMapping(
                  source.table(),
                  source.columnName(),
                  target,
                  targetColumn,
                  MappingKind.IDENTITY,
                  expression.toString(),
                  statementIndex,
                  outputOrdinal,
                  1),
              mappings);
        }
        continue;
      }

      outputOrdinal++;
      stats.candidateOutputCount++;
      String targetColumn = targetColumn(targetColumns, outputOrdinal);
      if (targetColumn == null || expression == null) {
        stats.unresolvedReferenceCount++;
        continue;
      }

      collectMappings(
          expression,
          target,
          targetColumn,
          scope,
          statementIndex,
          outputOrdinal,
          mappings,
          stats,
          schemaProvider);
    }

    if (targetColumns.size() > outputOrdinal) {
      stats.unresolvedReferenceCount += targetColumns.size() - outputOrdinal;
    }
  }

  private String targetColumn(List<String> targetColumns, int outputOrdinal) {
    int index = outputOrdinal - 1;
    return index >= 0 && index < targetColumns.size()
        ? normalizeColumnName(targetColumns.get(index))
        : null;
  }

  private List<SourceColumnRef> expandStar(
      Expression expression,
      Scope scope,
      SchemaProvider schemaProvider) {
    if (expression instanceof AllColumns allColumns
        && ((allColumns.getExceptColumns() != null && !allColumns.getExceptColumns().isEmpty())
            || (allColumns.getReplaceExpressions() != null
                && !allColumns.getReplaceExpressions().isEmpty()))) {
      return List.of();
    }

    List<SqlTableLineageParser.TableRef> tables = new ArrayList<>();

    if (expression instanceof AllTableColumns allTableColumns) {
      Table qualifier = allTableColumns.getTable();
      String rawQualifier = qualifier == null ? null : qualifier.getFullyQualifiedName();
      SqlTableLineageParser.TableRef table = scope.resolve(rawQualifier);
      if (table != null) tables.add(table);
    } else {
      tables.addAll(scope.orderedTables());
    }

    List<SourceColumnRef> expanded = new ArrayList<>();
    for (SqlTableLineageParser.TableRef table : tables) {
      for (SchemaColumn column : safeColumns(schemaProvider, table)) {
        String columnName = normalizeColumnName(column.name());
        if (columnName != null) {
          expanded.add(new SourceColumnRef(table, columnName));
        }
      }
    }
    return expanded;
  }

  private void collectMappings(
      Expression expression,
      SqlTableLineageParser.TableRef target,
      String targetColumn,
      Scope scope,
      int statementIndex,
      int outputOrdinal,
      Map<String, ColumnMapping> mappings,
      MutableStats stats,
      SchemaProvider schemaProvider) {
    SourceCollection collected = collectSources(expression, scope, schemaProvider);
    stats.unresolvedReferenceCount += collected.unresolvedCount();

    MappingKind kind =
        expression instanceof Column
            ? MappingKind.IDENTITY
            : collected.aggregate()
                ? MappingKind.AGGREGATION
                : MappingKind.TRANSFORMATION;

    int sourceOrdinal = 0;
    for (SourceColumnRef source : collected.sources()) {
      sourceOrdinal++;
      putMapping(
          new ColumnMapping(
              source.table(),
              source.columnName(),
              target,
              targetColumn,
              kind,
              expression.toString(),
              statementIndex,
              outputOrdinal,
              sourceOrdinal),
          mappings);
    }
  }

  private SourceCollection collectSources(
      Expression expression,
      Scope scope,
      SchemaProvider schemaProvider) {
    Map<String, SourceColumnRef> sources = new LinkedHashMap<>();
    int[] unresolved = new int[] {0};
    boolean[] aggregate = new boolean[] {false};

    expression.accept(new ExpressionVisitorAdapter() {
      @Override
      public void visit(Column column) {
        String columnName = normalizeColumnName(column.getColumnName());
        SqlTableLineageParser.TableRef table =
            resolveTable(column, scope, schemaProvider);
        if (columnName == null || table == null) {
          unresolved[0]++;
          return;
        }
        SourceColumnRef source = new SourceColumnRef(table, columnName);
        sources.putIfAbsent(
            table.canonicalName() + "." + columnName.toLowerCase(Locale.ROOT),
            source);
      }

      @Override
      public void visit(Function function) {
        String name = function.getName();
        if (name != null && AGGREGATE_FUNCTIONS.contains(name.toUpperCase(Locale.ROOT))) {
          aggregate[0] = true;
        }
        super.visit(function);
      }
    });

    return new SourceCollection(List.copyOf(sources.values()), unresolved[0], aggregate[0]);
  }

  private Scope buildScope(PlainSelect select, Set<String> cteNames) {
    Scope scope = new Scope();
    addFromItem(scope, select.getFromItem(), cteNames);
    addJoins(scope, select.getJoins(), cteNames);
    return scope;
  }

  private void addJoins(Scope scope, List<Join> joins, Set<String> cteNames) {
    if (joins == null) return;
    for (Join join : joins) {
      if (join != null) addFromItem(scope, join.getRightItem(), cteNames);
    }
  }

  private void addFromItem(Scope scope, FromItem fromItem, Set<String> cteNames) {
    if (!(fromItem instanceof Table table)) return;

    String rawName = table.getFullyQualifiedName();
    String simpleName = table.getName();
    if (rawName != null
        && !rawName.contains(".")
        && simpleName != null
        && cteNames.contains(simpleName.toLowerCase(Locale.ROOT))) {
      return;
    }

    SqlTableLineageParser.TableRef ref = tableRef(table);
    if (ref == null) return;

    String alias =
        table.getAlias() == null || table.getAlias().getName() == null
            ? null
            : table.getAlias().getName();
    scope.add(ref, alias);
  }

  private SqlTableLineageParser.TableRef resolveTable(
      Column column,
      Scope scope,
      SchemaProvider schemaProvider) {
    Table qualifier = column.getTable();
    String rawQualifier = qualifier == null ? null : qualifier.getFullyQualifiedName();
    if (rawQualifier != null && !rawQualifier.isBlank()) {
      return scope.resolve(rawQualifier);
    }

    SqlTableLineageParser.TableRef only = scope.onlyTable();
    if (only != null) return only;

    String columnName = normalizeColumnName(column.getColumnName());
    if (columnName == null) return null;

    SqlTableLineageParser.TableRef resolved = null;
    for (SqlTableLineageParser.TableRef table : scope.orderedTables()) {
      if (!hasColumn(schemaProvider, table, columnName)) continue;
      if (resolved != null
          && !resolved.canonicalName().equals(table.canonicalName())) {
        return null;
      }
      resolved = table;
    }
    return resolved;
  }

  private boolean hasColumn(
      SchemaProvider schemaProvider,
      SqlTableLineageParser.TableRef table,
      String columnName) {
    for (SchemaColumn column : safeColumns(schemaProvider, table)) {
      if (column.name() != null && column.name().equalsIgnoreCase(columnName)) {
        return true;
      }
    }
    return false;
  }

  private SqlTableLineageParser.TableRef tableRef(Table table) {
    if (table == null) return null;
    String rawName = table.getFullyQualifiedName();
    if (rawName == null || rawName.isBlank()) return null;

    List<String> parts = new ArrayList<>();
    for (String part : rawName.trim().split("\\.")) {
      String normalized = normalizeIdentifier(part);
      if (normalized != null) parts.add(normalized);
    }
    if (parts.isEmpty()) return null;

    String tableName = parts.get(parts.size() - 1);
    String schemaName = parts.size() >= 2 ? parts.get(parts.size() - 2) : null;
    String databaseName = parts.size() >= 3 ? parts.get(parts.size() - 3) : null;
    String qualifiedName = String.join(".", parts);
    return new SqlTableLineageParser.TableRef(
        qualifiedName.toLowerCase(Locale.ROOT),
        qualifiedName,
        databaseName,
        schemaName,
        tableName);
  }

  private List<String> inferSelectOutputNames(
      Select select,
      SchemaProvider schemaProvider,
      Set<String> inheritedCteNames) {
    if (select instanceof PlainSelect plainSelect) {
      Set<String> cteNames = new LinkedHashSet<>(inheritedCteNames);
      if (select.getWithItemsList() != null) {
        for (WithItem withItem : select.getWithItemsList()) {
          if (withItem.getAlias() != null && withItem.getAlias().getName() != null) {
            cteNames.add(withItem.getAlias().getName().toLowerCase(Locale.ROOT));
          }
        }
      }

      Scope scope = buildScope(plainSelect, cteNames);
      List<String> names = new ArrayList<>();
      if (plainSelect.getSelectItems() == null) return names;
      for (SelectItem<?> item : plainSelect.getSelectItems()) {
        if (item == null || item.getExpression() == null) {
          names.add(null);
        } else if (item.getAlias() != null && item.getAlias().getName() != null) {
          names.add(normalizeColumnName(item.getAlias().getName()));
        } else if (item.getExpression() instanceof Column column) {
          names.add(normalizeColumnName(column.getColumnName()));
        } else if (item.getExpression() instanceof AllColumns
            || item.getExpression() instanceof AllTableColumns) {
          List<SourceColumnRef> expanded =
              expandStar(item.getExpression(), scope, schemaProvider);
          if (expanded.isEmpty()) {
            names.add(null);
          } else {
            for (SourceColumnRef source : expanded) {
              names.add(source.columnName());
            }
          }
        } else {
          names.add(null);
        }
      }
      return names;
    }

    if (select instanceof SetOperationList setOperationList
        && setOperationList.getSelects() != null
        && !setOperationList.getSelects().isEmpty()) {
      return inferSelectOutputNames(
          setOperationList.getSelect(0), schemaProvider, inheritedCteNames);
    }
    return List.of();
  }

  private List<String> schemaColumnNames(
      SchemaProvider schemaProvider,
      SqlTableLineageParser.TableRef table) {
    List<String> names = new ArrayList<>();
    for (SchemaColumn column : safeColumns(schemaProvider, table)) {
      String name = normalizeColumnName(column.name());
      if (name != null) names.add(name);
    }
    return names;
  }

  private List<SchemaColumn> safeColumns(
      SchemaProvider schemaProvider,
      SqlTableLineageParser.TableRef table) {
    if (schemaProvider == null || table == null) return List.of();
    try {
      List<SchemaColumn> columns = schemaProvider.columns(table);
      if (columns == null || columns.isEmpty()) return List.of();
      return columns.stream()
          .filter(column -> column != null && normalizeColumnName(column.name()) != null)
          .sorted(Comparator.comparingInt(
              column -> column.ordinalPosition() == null
                  ? Integer.MAX_VALUE
                  : column.ordinalPosition()))
          .toList();
    } catch (RuntimeException ignored) {
      return List.of();
    }
  }

  private static void putMapping(
      ColumnMapping mapping,
      Map<String, ColumnMapping> mappings) {
    mappings.putIfAbsent(mappingKey(mapping), mapping);
  }

  private static String mappingKey(ColumnMapping mapping) {
    return mapping.sourceTable().canonicalName()
        + "|"
        + mapping.sourceColumnName().toLowerCase(Locale.ROOT)
        + "|"
        + mapping.targetTable().canonicalName()
        + "|"
        + mapping.targetColumnName().toLowerCase(Locale.ROOT)
        + "|"
        + mapping.statementIndex()
        + "|"
        + mapping.outputOrdinal()
        + "|"
        + mapping.expression();
  }

  private static String normalizeColumnName(String value) {
    return normalizeIdentifier(value);
  }

  private static String normalizeIdentifier(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isEmpty()) return null;
    if (normalized.length() >= 2) {
      char first = normalized.charAt(0);
      char last = normalized.charAt(normalized.length() - 1);
      if ((first == '`' && last == '`')
          || (first == '"' && last == '"')
          || (first == '[' && last == ']')) {
        normalized = normalized.substring(1, normalized.length() - 1);
      }
    }
    return normalized.isBlank() ? null : normalized;
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "unknown parser error" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  public enum MappingKind {
    IDENTITY,
    TRANSFORMATION,
    AGGREGATION
  }

  public record ParseResult(
      List<ColumnMapping> mappings,
      int statementCount,
      int candidateOutputCount,
      int unresolvedReferenceCount) {
  }

  public record ColumnMapping(
      SqlTableLineageParser.TableRef sourceTable,
      String sourceColumnName,
      SqlTableLineageParser.TableRef targetTable,
      String targetColumnName,
      MappingKind mappingKind,
      String expression,
      int statementIndex,
      int outputOrdinal,
      int sourceOrdinal) {
  }

  public record SchemaColumn(String name, Integer ordinalPosition) {
  }

  @FunctionalInterface
  public interface SchemaProvider {
    List<SchemaColumn> columns(SqlTableLineageParser.TableRef table);

    static SchemaProvider none() {
      return table -> List.of();
    }
  }

  private record SourceColumnRef(
      SqlTableLineageParser.TableRef table,
      String columnName) {
  }

  private record SourceCollection(
      List<SourceColumnRef> sources,
      int unresolvedCount,
      boolean aggregate) {
  }

  private static final class MutableStats {
    private int candidateOutputCount;
    private int unresolvedReferenceCount;
  }

  private static final class Scope {
    private final Map<String, SqlTableLineageParser.TableRef> tables = new LinkedHashMap<>();
    private final Map<String, SqlTableLineageParser.TableRef> qualifiers = new LinkedHashMap<>();
    private final Set<String> ambiguousQualifiers = new LinkedHashSet<>();

    void add(SqlTableLineageParser.TableRef table, String alias) {
      tables.putIfAbsent(table.canonicalName(), table);
      addQualifier(table.qualifiedName(), table);
      addQualifier(table.tableName(), table);
      if (table.schemaName() != null) {
        addQualifier(table.schemaName() + "." + table.tableName(), table);
      }
      addQualifier(alias, table);
    }

    SqlTableLineageParser.TableRef resolve(String qualifier) {
      if (qualifier == null || qualifier.isBlank()) return null;
      String key = qualifier.trim().toLowerCase(Locale.ROOT);
      if (ambiguousQualifiers.contains(key)) return null;
      return qualifiers.get(key);
    }

    SqlTableLineageParser.TableRef onlyTable() {
      return tables.size() == 1 ? tables.values().iterator().next() : null;
    }

    List<SqlTableLineageParser.TableRef> orderedTables() {
      return List.copyOf(tables.values());
    }

    private void addQualifier(String qualifier, SqlTableLineageParser.TableRef table) {
      if (qualifier == null || qualifier.isBlank()) return;
      String key = qualifier.trim().toLowerCase(Locale.ROOT);
      SqlTableLineageParser.TableRef existing = qualifiers.get(key);
      if (existing != null && !existing.canonicalName().equals(table.canonicalName())) {
        ambiguousQualifiers.add(key);
        qualifiers.remove(key);
      } else if (!ambiguousQualifiers.contains(key)) {
        qualifiers.putIfAbsent(key, table);
      }
    }
  }

  public static final class SqlColumnLineageParseException extends RuntimeException {
    SqlColumnLineageParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
