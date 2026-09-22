package io.yak.ops.business.development.domain;

import java.util.ArrayList;
import java.util.List;

/** Result of SQL lineage analysis for a single SQL statement. */
public class SqlLineageResult {

  /** 0-based index of the statement within the original multi-statement SQL script. */
  private int statementIndex;
  private List<TableNode> tables = new ArrayList<>();
  private List<TableEdge> tableEdges = new ArrayList<>();
  private List<ColumnEdge> columnEdges = new ArrayList<>();
  private List<String> warnings = new ArrayList<>();

  public SqlLineageResult() {}

  public SqlLineageResult(int statementIndex) {
    this.statementIndex = statementIndex;
  }

  public int getStatementIndex() {
    return statementIndex;
  }

  public void setStatementIndex(int statementIndex) {
    this.statementIndex = statementIndex;
  }

  public List<TableNode> getTables() {
    return tables;
  }

  public void setTables(List<TableNode> tables) {
    this.tables = tables != null ? tables : new ArrayList<>();
  }

  public List<TableEdge> getTableEdges() {
    return tableEdges;
  }

  public void setTableEdges(List<TableEdge> tableEdges) {
    this.tableEdges = tableEdges != null ? tableEdges : new ArrayList<>();
  }

  public List<ColumnEdge> getColumnEdges() {
    return columnEdges;
  }

  public void setColumnEdges(List<ColumnEdge> columnEdges) {
    this.columnEdges = columnEdges != null ? columnEdges : new ArrayList<>();
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings != null ? warnings : new ArrayList<>();
  }

  public void addWarning(String warning) {
    this.warnings.add(warning);
  }

  /** A table that appears in the SQL as either a source or a target. */
  public static class TableNode {
    private String name;
    private String type; // SOURCE or TARGET

    public TableNode() {}

    public TableNode(String name, String type) {
      this.name = name;
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }

  /** A directed edge from a source table to a target table. */
  public static class TableEdge {
    private String source;
    private String target;

    public TableEdge() {}

    public TableEdge(String source, String target) {
      this.source = source;
      this.target = target;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }

    public String getTarget() {
      return target;
    }

    public void setTarget(String target) {
      this.target = target;
    }
  }

  /** Column-level lineage from a source column to a target column. */
  public static class ColumnEdge {
    private String sourceTable;
    private String sourceColumn;
    private String targetTable;
    private String targetColumn;

    public ColumnEdge() {}

    public ColumnEdge(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
      this.sourceTable = sourceTable;
      this.sourceColumn = sourceColumn;
      this.targetTable = targetTable;
      this.targetColumn = targetColumn;
    }

    public String getSourceTable() {
      return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
      this.sourceTable = sourceTable;
    }

    public String getSourceColumn() {
      return sourceColumn;
    }

    public void setSourceColumn(String sourceColumn) {
      this.sourceColumn = sourceColumn;
    }

    public String getTargetTable() {
      return targetTable;
    }

    public void setTargetTable(String targetTable) {
      this.targetTable = targetTable;
    }

    public String getTargetColumn() {
      return targetColumn;
    }

    public void setTargetColumn(String targetColumn) {
      this.targetColumn = targetColumn;
    }
  }
}
