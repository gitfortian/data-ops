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
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Extends the baseline SQL column lineage parser with transparent CTE and FROM-subquery
 * propagation.
 *
 * <p>CTEs and derived tables are query-local virtual relations only. Their output columns keep
 * physical source-column origins in memory and are flattened before {@link ColumnMapping}s are
 * returned, so no fake CTE/subquery COLUMN assets are persisted.
 */
@Component
@Primary
public class DerivedAwareSqlColumnLineageParser extends SqlColumnLineageParser {

  private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
      "AVG", "COUNT", "GROUP_CONCAT", "MAX", "MIN", "SUM",
      "STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VAR_POP", "VAR_SAMP", "VARIANCE");

  @Override
  public ParseResult parse(String sql) {
    return parse(sql, SchemaProvider.none());
  }

  @Override
  public ParseResult parse(String sql, SchemaProvider schemaProvider) {
    if (sql == null || sql.isBlank()) {
      return super.parse(sql, schemaProvider);
    }

    SchemaProvider provider = schemaProvider == null ? SchemaProvider.none() : schemaProvider;
    try {
      Statements statements = CCJSqlParserUtil.parseStatements(sql);
      Map<String, ColumnMapping> merged = new LinkedHashMap<>();
      int statementIndex = 0;
      int candidateOutputCount = 0;
      int unresolvedReferenceCount = 0;

      for (Statement statement : statements.getStatements()) {
        statementIndex++;
        if (supportsDerivedPropagation(statement)) {
          StatementResult derived =
              analyzeDerivedStatement(statement, statementIndex, provider);
          if (derived.handled()) {
            for (ColumnMapping mapping : derived.mappings()) {
              merged.putIfAbsent(mappingKey(mapping), mapping);
            }
            candidateOutputCount += derived.candidateOutputCount();
            unresolvedReferenceCount += derived.unresolvedReferenceCount();
            continue;
          }
        }

        ParseResult fallback = super.parse(statement.toString(), provider);
        for (ColumnMapping mapping : fallback.mappings()) {
          ColumnMapping reindexed = reindex(mapping, statementIndex);
          merged.putIfAbsent(mappingKey(reindexed), reindexed);
        }
        candidateOutputCount += fallback.candidateOutputCount();
        unresolvedReferenceCount += fallback.unresolvedReferenceCount();
      }

      return new ParseResult(
          List.copyOf(merged.values()),
          statementIndex,
          candidateOutputCount,
          unresolvedReferenceCount);
    } catch (JSQLParserException | RuntimeException exception) {
      // Derived propagation is an enrichment layer. Any unexpected resolver issue must not reduce
      // the baseline parser's already-working coverage.
      return super.parse(sql, provider);
    }
  }

  private boolean supportsDerivedPropagation(Statement statement) {
    if (statement instanceof Insert insert) {
      return hasWithItems(insert.getWithItemsList()) || selectContainsDerived(insert.getSelect());
    }
    if (statement instanceof CreateTable createTable) {
      return selectContainsDerived(createTable.getSelect());
    }
    return false;
  }

  private boolean selectContainsDerived(Select select) {
    if (select == null) return false;
    if (hasWithItems(select.getWithItemsList())) return true;
    if (select instanceof ParenthesedSelect) return true;
    if (select instanceof PlainSelect plainSelect) {
      if (fromItemIsDerived(plainSelect.getFromItem())) return true;
      if (plainSelect.getJoins() != null) {
        for (Join join : plainSelect.getJoins()) {
          if (join != null && fromItemIsDerived(join.getRightItem())) return true;
        }
      }
      return false;
    }
    if (select instanceof SetOperationList setOperationList
        && setOperationList.getSelects() != null) {
      for (Select child : setOperationList.getSelects()) {
        if (selectContainsDerived(child)) return true;
      }
    }
    return false;
  }

  private boolean fromItemIsDerived(FromItem fromItem) {
    return fromItem instanceof ParenthesedSelect;
  }

  private boolean hasWithItems(List<WithItem> items) {
    return items != null && !items.isEmpty();
  }

  private StatementResult analyzeDerivedStatement(
      Statement statement,
      int statementIndex,
      SchemaProvider schemaProvider) {
    ResolveStats stats = new ResolveStats();
    if (statement instanceof Insert insert) {
      return analyzeInsert(insert, statementIndex, schemaProvider, stats);
    }
    if (statement instanceof CreateTable createTable) {
      return analyzeCreateTable(createTable, statementIndex, schemaProvider, stats);
    }
    return StatementResult.unhandled();
  }

  private StatementResult analyzeInsert(
      Insert insert,
      int statementIndex,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    SqlTableLineageParser.TableRef target = tableRef(insert.getTable());
    Select select = insert.getSelect();
    if (target == null || select == null) return StatementResult.unhandled();

    Map<String, VirtualRelation> ctes =
        resolveWithItems(insert.getWithItemsList(), Map.of(), schemaProvider, stats);
    VirtualRelation relation = resolveSelect(select, ctes, schemaProvider, stats);

    List<String> targetColumns = new ArrayList<>();
    if (insert.getColumns() != null) {
      for (Column column : insert.getColumns()) {
        String name = normalizeIdentifier(column.getColumnName());
        if (name != null) targetColumns.add(name);
      }
    }
    if (targetColumns.isEmpty()) {
      targetColumns = schemaColumnNames(schemaProvider, target);
    }
    if (targetColumns.isEmpty()) {
      return new StatementResult(
          true,
          List.of(),
          relation.columns().size(),
          Math.max(1, stats.unresolved + relation.columns().size()));
    }

    return emitMappings(
        relation,
        target,
        targetColumns,
        statementIndex,
        stats,
        "INSERT");
  }

  private StatementResult analyzeCreateTable(
      CreateTable createTable,
      int statementIndex,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    SqlTableLineageParser.TableRef target = tableRef(createTable.getTable());
    Select select = createTable.getSelect();
    if (target == null || select == null) return StatementResult.unhandled();

    VirtualRelation relation = resolveSelect(select, Map.of(), schemaProvider, stats);
    List<String> targetColumns = new ArrayList<>();
    if (createTable.getColumns() != null) {
      for (String column : createTable.getColumns()) {
        String name = normalizeIdentifier(column);
        if (name != null) targetColumns.add(name);
      }
    }
    if (targetColumns.isEmpty()) {
      for (VirtualColumn column : relation.columns()) {
        targetColumns.add(column.name());
      }
    }

    return emitMappings(
        relation,
        target,
        targetColumns,
        statementIndex,
        stats,
        "CTAS");
  }

  private StatementResult emitMappings(
      VirtualRelation relation,
      SqlTableLineageParser.TableRef target,
      List<String> targetColumns,
      int statementIndex,
      ResolveStats stats,
      String operation) {
    Map<String, ColumnMapping> mappings = new LinkedHashMap<>();
    int candidates = relation.columns().size();
    int unresolved = stats.unresolved;

    for (int i = 0; i < relation.columns().size(); i++) {
      VirtualColumn output = relation.columns().get(i);
      String targetColumn =
          i < targetColumns.size() ? normalizeIdentifier(targetColumns.get(i)) : null;
      if (targetColumn == null) {
        unresolved++;
        continue;
      }

      if (output.origins().isEmpty()) {
        // Constant projections legitimately have no upstream physical column.
        if (!output.constant()) unresolved++;
        continue;
      }

      int sourceOrdinal = 0;
      for (Origin origin : output.origins()) {
        sourceOrdinal++;
        String evidence = evidence(operation, output, origin);
        ColumnMapping mapping = new ColumnMapping(
            origin.table(),
            origin.columnName(),
            target,
            targetColumn,
            origin.kind(),
            evidence,
            statementIndex,
            i + 1,
            sourceOrdinal);
        mappings.putIfAbsent(mappingKey(mapping), mapping);
      }
    }

    if (targetColumns.size() > relation.columns().size()) {
      unresolved += targetColumns.size() - relation.columns().size();
    }

    return new StatementResult(
        true,
        List.copyOf(mappings.values()),
        candidates,
        unresolved);
  }

  private String evidence(String operation, VirtualColumn output, Origin origin) {
    String path = origin.expression();
    if (path == null || path.isBlank()) {
      path = origin.table().qualifiedName() + "." + origin.columnName();
    }
    if (output.expression() != null
        && !output.expression().isBlank()
        && !path.endsWith(output.expression())) {
      path = path + " -> " + output.expression();
    }
    return operation + ": " + path;
  }

  private VirtualRelation resolveSelect(
      Select select,
      Map<String, VirtualRelation> inheritedCtes,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    if (select == null) return VirtualRelation.empty();

    Map<String, VirtualRelation> ctes =
        resolveWithItems(select.getWithItemsList(), inheritedCtes, schemaProvider, stats);

    if (select instanceof ParenthesedSelect parenthesedSelect) {
      return resolveSelect(parenthesedSelect.getSelect(), ctes, schemaProvider, stats);
    }

    if (select instanceof PlainSelect plainSelect) {
      return resolvePlainSelect(plainSelect, ctes, schemaProvider, stats);
    }

    if (select instanceof SetOperationList setOperationList) {
      return resolveSetOperation(setOperationList, ctes, schemaProvider, stats);
    }

    stats.unresolved++;
    return VirtualRelation.empty();
  }

  private Map<String, VirtualRelation> resolveWithItems(
      List<WithItem> withItems,
      Map<String, VirtualRelation> inherited,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    Map<String, VirtualRelation> resolved = new LinkedHashMap<>(inherited);
    if (withItems == null) return resolved;

    for (WithItem withItem : withItems) {
      if (withItem == null
          || withItem.getAlias() == null
          || withItem.getAlias().getName() == null) {
        stats.unresolved++;
        continue;
      }

      String name = withItem.getAlias().getName().toLowerCase(Locale.ROOT);
      if (withItem.isRecursive()) {
        // Recursive CTEs need fixpoint semantics. Keep the declared name as an empty virtual
        // relation so outer references cannot accidentally leak out as fake physical tables.
        stats.unresolved++;
        resolved.put(name, VirtualRelation.empty());
        continue;
      }

      VirtualRelation relation =
          resolveSelect(withItem.getSelect(), resolved, schemaProvider, stats);
      List<String> explicitNames = cteColumnAliases(withItem);
      if (!explicitNames.isEmpty()) {
        relation = relation.rename(explicitNames, stats);
      }
      resolved.put(name, relation);
    }
    return resolved;
  }

  private List<String> cteColumnAliases(WithItem withItem) {
    List<String> names = new ArrayList<>();
    if (withItem.getWithItemList() == null) return names;
    for (SelectItem<?> item : withItem.getWithItemList()) {
      if (item == null || item.getExpression() == null) {
        names.add(null);
      } else if (item.getAlias() != null && item.getAlias().getName() != null) {
        names.add(normalizeIdentifier(item.getAlias().getName()));
      } else if (item.getExpression() instanceof Column column) {
        names.add(normalizeIdentifier(column.getColumnName()));
      } else {
        names.add(normalizeIdentifier(item.getExpression().toString()));
      }
    }
    return names;
  }

  private VirtualRelation resolveSetOperation(
      SetOperationList setOperationList,
      Map<String, VirtualRelation> ctes,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    if (setOperationList.getSelects() == null || setOperationList.getSelects().isEmpty()) {
      return VirtualRelation.empty();
    }

    List<VirtualRelation> branches = new ArrayList<>();
    for (Select child : setOperationList.getSelects()) {
      branches.add(resolveSelect(child, ctes, schemaProvider, stats));
    }

    int width = branches.get(0).columns().size();
    List<VirtualColumn> outputs = new ArrayList<>();
    for (int ordinal = 0; ordinal < width; ordinal++) {
      VirtualColumn first = branches.get(0).columns().get(ordinal);
      Map<String, Origin> origins = new LinkedHashMap<>();
      boolean constant = true;

      for (VirtualRelation branch : branches) {
        if (branch.columns().size() <= ordinal) {
          stats.unresolved++;
          continue;
        }
        VirtualColumn column = branch.columns().get(ordinal);
        constant &= column.constant();
        mergeOrigins(origins, column.origins());
      }
      outputs.add(new VirtualColumn(
          first.name(),
          List.copyOf(origins.values()),
          first.expression(),
          constant));
    }

    for (VirtualRelation branch : branches) {
      if (branch.columns().size() != width) stats.unresolved++;
    }
    return new VirtualRelation(List.copyOf(outputs));
  }

  private VirtualRelation resolvePlainSelect(
      PlainSelect plainSelect,
      Map<String, VirtualRelation> ctes,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    Scope scope = buildScope(plainSelect, ctes, schemaProvider, stats);
    List<VirtualColumn> outputs = new ArrayList<>();
    if (plainSelect.getSelectItems() == null) return new VirtualRelation(outputs);

    for (SelectItem<?> item : plainSelect.getSelectItems()) {
      if (item == null || item.getExpression() == null) {
        stats.unresolved++;
        outputs.add(new VirtualColumn(null, List.of(), null, false));
        continue;
      }

      Expression expression = item.getExpression();
      if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
        List<VirtualColumn> expanded = expandStar(expression, scope, schemaProvider);
        if (expanded.isEmpty()) {
          stats.unresolved++;
          outputs.add(new VirtualColumn(null, List.of(), expression.toString(), false));
        } else {
          outputs.addAll(expanded);
        }
        continue;
      }

      ExpressionResult resolved = resolveExpression(expression, scope, schemaProvider);
      stats.unresolved += resolved.unresolved();
      String outputName = outputName(item);
      outputs.add(new VirtualColumn(
          outputName,
          resolved.origins(),
          expression.toString(),
          resolved.constant()));
    }

    return new VirtualRelation(List.copyOf(outputs));
  }

  private Scope buildScope(
      PlainSelect select,
      Map<String, VirtualRelation> ctes,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    Scope scope = new Scope();
    addFromItem(scope, select.getFromItem(), ctes, schemaProvider, stats);
    if (select.getJoins() != null) {
      for (Join join : select.getJoins()) {
        if (join != null) {
          addFromItem(scope, join.getRightItem(), ctes, schemaProvider, stats);
        }
      }
    }
    return scope;
  }

  private void addFromItem(
      Scope scope,
      FromItem fromItem,
      Map<String, VirtualRelation> ctes,
      SchemaProvider schemaProvider,
      ResolveStats stats) {
    if (fromItem instanceof Table table) {
      String rawName = table.getFullyQualifiedName();
      String simpleName = table.getName();
      String alias =
          table.getAlias() == null || table.getAlias().getName() == null
              ? null
              : table.getAlias().getName();

      if (rawName != null && !rawName.contains(".") && simpleName != null) {
        VirtualRelation cte = ctes.get(simpleName.toLowerCase(Locale.ROOT));
        if (cte != null) {
          scope.addVirtual(simpleName, alias, cte);
          return;
        }
      }

      SqlTableLineageParser.TableRef physical = tableRef(table);
      if (physical != null) scope.addPhysical(physical, alias);
      return;
    }

    if (fromItem instanceof ParenthesedSelect parenthesedSelect) {
      VirtualRelation relation =
          resolveSelect(parenthesedSelect.getSelect(), ctes, schemaProvider, stats);
      String alias =
          parenthesedSelect.getAlias() == null
                  || parenthesedSelect.getAlias().getName() == null
              ? null
              : parenthesedSelect.getAlias().getName();
      scope.addVirtual(null, alias, relation);
    }
  }

  private List<VirtualColumn> expandStar(
      Expression expression,
      Scope scope,
      SchemaProvider schemaProvider) {
    if (expression instanceof AllColumns allColumns
        && ((allColumns.getExceptColumns() != null && !allColumns.getExceptColumns().isEmpty())
            || (allColumns.getReplaceExpressions() != null
                && !allColumns.getReplaceExpressions().isEmpty()))) {
      return List.of();
    }

    if (expression instanceof AllTableColumns allTableColumns) {
      Table qualifier = allTableColumns.getTable();
      String rawQualifier = qualifier == null ? null : qualifier.getFullyQualifiedName();
      Binding binding = scope.resolveBinding(rawQualifier);
      return binding == null ? List.of() : binding.columns(schemaProvider);
    }

    List<VirtualColumn> expanded = new ArrayList<>();
    for (Binding binding : scope.orderedBindings()) {
      expanded.addAll(binding.columns(schemaProvider));
    }
    return expanded;
  }

  private ExpressionResult resolveExpression(
      Expression expression,
      Scope scope,
      SchemaProvider schemaProvider) {
    Map<String, Origin> origins = new LinkedHashMap<>();
    int[] unresolved = {0};
    boolean[] aggregate = {false};
    boolean[] sawColumn = {false};
    boolean[] sawSubquery = {false};

    expression.accept(new ExpressionVisitorAdapter() {
      @Override
      public void visit(Column column) {
        sawColumn[0] = true;
        ColumnResolution resolution = scope.resolveColumn(column, schemaProvider);
        if (resolution.origins().isEmpty()) {
          unresolved[0]++;
          return;
        }
        mergeOrigins(origins, resolution.origins());
      }

      @Override
      public void visit(Function function) {
        String name = function.getName();
        if (name != null && AGGREGATE_FUNCTIONS.contains(name.toUpperCase(Locale.ROOT))) {
          aggregate[0] = true;
        }
        super.visit(function);
      }

      @Override
      public void visit(ParenthesedSelect parenthesedSelect) {
        // Correlated/scalar subqueries need expression-scope semantics. Do not accidentally resolve
        // their columns against the outer SELECT scope.
        sawSubquery[0] = true;
      }
    });

    MappingKind localKind =
        expression instanceof Column
            ? MappingKind.IDENTITY
            : aggregate[0] ? MappingKind.AGGREGATION : MappingKind.TRANSFORMATION;

    List<Origin> adjusted = new ArrayList<>();
    for (Origin origin : origins.values()) {
      MappingKind kind = strongest(origin.kind(), localKind);
      String expr = composeExpression(origin.expression(), expression.toString());
      adjusted.add(new Origin(origin.table(), origin.columnName(), kind, expr));
    }

    if (sawSubquery[0]) unresolved[0]++;
    boolean constant = !sawColumn[0] && !sawSubquery[0];
    return new ExpressionResult(List.copyOf(adjusted), unresolved[0], constant);
  }

  private String outputName(SelectItem<?> item) {
    if (item.getAlias() != null && item.getAlias().getName() != null) {
      return normalizeIdentifier(item.getAlias().getName());
    }
    if (item.getExpression() instanceof Column column) {
      return normalizeIdentifier(column.getColumnName());
    }
    return null;
  }

  private List<String> schemaColumnNames(
      SchemaProvider schemaProvider,
      SqlTableLineageParser.TableRef table) {
    List<String> names = new ArrayList<>();
    for (SchemaColumn column : safeColumns(schemaProvider, table)) {
      String name = normalizeIdentifier(column.name());
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
          .filter(column -> column != null && normalizeIdentifier(column.name()) != null)
          .sorted(Comparator.comparingInt(
              column -> column.ordinalPosition() == null
                  ? Integer.MAX_VALUE
                  : column.ordinalPosition()))
          .toList();
    } catch (RuntimeException ignored) {
      return List.of();
    }
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

  private MappingKind strongest(MappingKind left, MappingKind right) {
    return staticStrongest(left, right);
  }

  private String composeExpression(String upstream, String current) {
    if (upstream == null || upstream.isBlank()) return current;
    if (current == null || current.isBlank()) return upstream;
    if (upstream.equals(current) || upstream.endsWith(" -> " + current)) return upstream;
    return upstream + " -> " + current;
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

  private ColumnMapping reindex(ColumnMapping mapping, int statementIndex) {
    return new ColumnMapping(
        mapping.sourceTable(),
        mapping.sourceColumnName(),
        mapping.targetTable(),
        mapping.targetColumnName(),
        mapping.mappingKind(),
        mapping.expression(),
        statementIndex,
        mapping.outputOrdinal(),
        mapping.sourceOrdinal());
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

  private static void mergeOrigins(Map<String, Origin> target, List<Origin> origins) {
    for (Origin origin : origins) {
      String key = origin.table().canonicalName()
          + "."
          + origin.columnName().toLowerCase(Locale.ROOT);
      Origin existing = target.get(key);
      if (existing == null) {
        target.put(key, origin);
      } else {
        MappingKind kind = staticStrongest(existing.kind(), origin.kind());
        String expression =
            existing.expression() == null || existing.expression().isBlank()
                ? origin.expression()
                : existing.expression();
        target.put(key, new Origin(existing.table(), existing.columnName(), kind, expression));
      }
    }
  }

  private static MappingKind staticStrongest(MappingKind left, MappingKind right) {
    if (left == MappingKind.AGGREGATION || right == MappingKind.AGGREGATION) {
      return MappingKind.AGGREGATION;
    }
    if (left == MappingKind.TRANSFORMATION || right == MappingKind.TRANSFORMATION) {
      return MappingKind.TRANSFORMATION;
    }
    return MappingKind.IDENTITY;
  }

  private record StatementResult(
      boolean handled,
      List<ColumnMapping> mappings,
      int candidateOutputCount,
      int unresolvedReferenceCount) {
    static StatementResult unhandled() {
      return new StatementResult(false, List.of(), 0, 0);
    }
  }

  private record Origin(
      SqlTableLineageParser.TableRef table,
      String columnName,
      MappingKind kind,
      String expression) {
  }

  private record VirtualColumn(
      String name,
      List<Origin> origins,
      String expression,
      boolean constant) {
  }

  private record VirtualRelation(List<VirtualColumn> columns) {
    static VirtualRelation empty() {
      return new VirtualRelation(List.of());
    }

    VirtualRelation rename(List<String> names, ResolveStats stats) {
      List<VirtualColumn> renamed = new ArrayList<>();
      for (int i = 0; i < columns.size(); i++) {
        String name = i < names.size() ? normalizeIdentifier(names.get(i)) : null;
        if (name == null) {
          stats.unresolved++;
          name = columns.get(i).name();
        }
        VirtualColumn column = columns.get(i);
        renamed.add(new VirtualColumn(name, column.origins(), column.expression(), column.constant()));
      }
      if (names.size() != columns.size()) stats.unresolved++;
      return new VirtualRelation(List.copyOf(renamed));
    }
  }

  private record ExpressionResult(
      List<Origin> origins,
      int unresolved,
      boolean constant) {
  }

  private record ColumnResolution(List<Origin> origins) {
  }

  private static final class ResolveStats {
    private int unresolved;
  }

  private static final class Binding {
    private final SqlTableLineageParser.TableRef physical;
    private final VirtualRelation virtual;

    private Binding(
        SqlTableLineageParser.TableRef physical,
        VirtualRelation virtual) {
      this.physical = physical;
      this.virtual = virtual;
    }

    static Binding physical(SqlTableLineageParser.TableRef table) {
      return new Binding(table, null);
    }

    static Binding virtual(VirtualRelation relation) {
      return new Binding(null, relation);
    }

    List<VirtualColumn> columns(SchemaProvider schemaProvider) {
      if (virtual != null) return virtual.columns();
      if (physical == null) return List.of();

      List<VirtualColumn> columns = new ArrayList<>();
      List<SchemaColumn> schemaColumns;
      try {
        schemaColumns = schemaProvider == null ? List.of() : schemaProvider.columns(physical);
      } catch (RuntimeException ignored) {
        schemaColumns = List.of();
      }
      if (schemaColumns == null) schemaColumns = List.of();
      schemaColumns = schemaColumns.stream()
          .filter(column -> column != null && normalizeIdentifier(column.name()) != null)
          .sorted(Comparator.comparingInt(
              column -> column.ordinalPosition() == null
                  ? Integer.MAX_VALUE
                  : column.ordinalPosition()))
          .toList();

      for (SchemaColumn column : schemaColumns) {
        String name = normalizeIdentifier(column.name());
        Origin origin = new Origin(
            physical,
            name,
            MappingKind.IDENTITY,
            physical.qualifiedName() + "." + name);
        columns.add(new VirtualColumn(name, List.of(origin), origin.expression(), false));
      }
      return List.copyOf(columns);
    }

    ColumnResolution resolveColumn(String columnName, SchemaProvider schemaProvider) {
      if (columnName == null) return new ColumnResolution(List.of());
      if (virtual != null) {
        VirtualColumn matched = null;
        for (VirtualColumn column : virtual.columns()) {
          if (column.name() != null && column.name().equalsIgnoreCase(columnName)) {
            if (matched != null) return new ColumnResolution(List.of());
            matched = column;
          }
        }
        return matched == null
            ? new ColumnResolution(List.of())
            : new ColumnResolution(matched.origins());
      }

      if (physical != null) {
        Origin origin = new Origin(
            physical,
            columnName,
            MappingKind.IDENTITY,
            physical.qualifiedName() + "." + columnName);
        return new ColumnResolution(List.of(origin));
      }
      return new ColumnResolution(List.of());
    }

    boolean hasColumn(String columnName, SchemaProvider schemaProvider) {
      if (virtual != null) {
        return virtual.columns().stream()
            .filter(column -> column.name() != null)
            .anyMatch(column -> column.name().equalsIgnoreCase(columnName));
      }
      if (physical == null) return false;
      List<VirtualColumn> columns = columns(schemaProvider);
      return columns.stream()
          .filter(column -> column.name() != null)
          .anyMatch(column -> column.name().equalsIgnoreCase(columnName));
    }
  }

  private static final class Scope {
    private final List<Binding> ordered = new ArrayList<>();
    private final Map<String, Binding> qualifiers = new LinkedHashMap<>();
    private final Set<String> ambiguousQualifiers = new LinkedHashSet<>();

    void addPhysical(SqlTableLineageParser.TableRef table, String alias) {
      Binding binding = Binding.physical(table);
      ordered.add(binding);
      addQualifier(table.qualifiedName(), binding);
      addQualifier(table.tableName(), binding);
      if (table.schemaName() != null) {
        addQualifier(table.schemaName() + "." + table.tableName(), binding);
      }
      addQualifier(alias, binding);
    }

    void addVirtual(String relationName, String alias, VirtualRelation relation) {
      Binding binding = Binding.virtual(relation);
      ordered.add(binding);
      addQualifier(relationName, binding);
      addQualifier(alias, binding);
    }

    Binding resolveBinding(String qualifier) {
      if (qualifier == null || qualifier.isBlank()) return null;
      String key = qualifier.trim().toLowerCase(Locale.ROOT);
      if (ambiguousQualifiers.contains(key)) return null;
      return qualifiers.get(key);
    }

    List<Binding> orderedBindings() {
      return List.copyOf(ordered);
    }

    ColumnResolution resolveColumn(Column column, SchemaProvider schemaProvider) {
      String columnName = normalizeIdentifier(column.getColumnName());
      if (columnName == null) return new ColumnResolution(List.of());

      Table qualifier = column.getTable();
      String rawQualifier = qualifier == null ? null : qualifier.getFullyQualifiedName();
      if (rawQualifier != null && !rawQualifier.isBlank()) {
        Binding binding = resolveBinding(rawQualifier);
        return binding == null
            ? new ColumnResolution(List.of())
            : binding.resolveColumn(columnName, schemaProvider);
      }

      if (ordered.size() == 1) {
        return ordered.get(0).resolveColumn(columnName, schemaProvider);
      }

      Binding resolved = null;
      for (Binding binding : ordered) {
        if (!binding.hasColumn(columnName, schemaProvider)) continue;
        if (resolved != null && resolved != binding) {
          return new ColumnResolution(List.of());
        }
        resolved = binding;
      }
      return resolved == null
          ? new ColumnResolution(List.of())
          : resolved.resolveColumn(columnName, schemaProvider);
    }

    private void addQualifier(String qualifier, Binding binding) {
      if (qualifier == null || qualifier.isBlank()) return;
      String key = qualifier.trim().toLowerCase(Locale.ROOT);
      Binding existing = qualifiers.get(key);
      if (existing != null && existing != binding) {
        ambiguousQualifiers.add(key);
        qualifiers.remove(key);
      } else if (!ambiguousQualifiers.contains(key)) {
        qualifiers.putIfAbsent(key, binding);
      }
    }
  }
}
