package io.yak.ops.business.development.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of SQL validation for a single SQL statement.
 *
 * <p>Each statement is validated independently. When validation fails,
 * structured error information is provided so the user can fix the SQL.
 */
public class SqlValidationResult {

  /** 0-based index of the statement within the original multi-statement SQL script. */
  private int statementIndex;

  /** Whether the statement passed all validation checks. */
  private boolean valid;

  /** The original SQL statement text (abbreviated if too long). */
  private String sql;

  /** List of validation errors (empty when valid). */
  private List<ValidationError> errors = new ArrayList<>();

  public SqlValidationResult() {}

  public SqlValidationResult(int statementIndex, String sql) {
    this.statementIndex = statementIndex;
    this.sql = sql;
  }

  public int getStatementIndex() {
    return statementIndex;
  }

  public void setStatementIndex(int statementIndex) {
    this.statementIndex = statementIndex;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public String getSql() {
    return sql;
  }

  public void setSql(String sql) {
    this.sql = sql;
  }

  public List<ValidationError> getErrors() {
    return errors;
  }

  public void setErrors(List<ValidationError> errors) {
    this.errors = errors != null ? errors : new ArrayList<>();
  }

  public void addError(ValidationError error) {
    this.errors.add(error);
    this.valid = false;
  }

  // ---- Inner class: Validation Error ----

  /**
   * A single validation error with structured information.
   */
  public static class ValidationError {

    /** Error severity. */
    public enum Severity {
      /** The SQL cannot be executed; must be fixed. */
      ERROR,
      /** The SQL may work but has potential issues. */
      WARNING
    }

    /** Error type for programmatic handling. */
    public enum Type {
      /** SQL syntax error (parse failure). */
      SYNTAX_ERROR,
      /** Referenced table does not exist in the schema/datasource. */
      TABLE_NOT_FOUND,
      /** Referenced column does not exist in the table. */
      COLUMN_NOT_FOUND,
      /** Column reference is ambiguous (exists in multiple tables). */
      AMBIGUOUS_COLUMN,
      /** Data type mismatch or incompatible types. */
      TYPE_MISMATCH,
      /** Unsupported SQL syntax for Calcite's parser. */
      UNSUPPORTED_SYNTAX,
      /** Other validation error. */
      OTHER
    }

    private Severity severity;
    private Type type;
    /** Human-readable error message. */
    private String message;
    /** 1-based line number in the statement, if available. */
    private Integer line;
    /** 1-based column number in the statement, if available. */
    private Integer column;
    /** The object that caused the error (e.g. table name, column name), if available. */
    private String object;

    public ValidationError() {}

    public ValidationError(Severity severity, Type type, String message) {
      this.severity = severity;
      this.type = type;
      this.message = message;
    }

    public Severity getSeverity() {
      return severity;
    }

    public void setSeverity(Severity severity) {
      this.severity = severity;
    }

    public Type getType() {
      return type;
    }

    public void setType(Type type) {
      this.type = type;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public Integer getLine() {
      return line;
    }

    public void setLine(Integer line) {
      this.line = line;
    }

    public Integer getColumn() {
      return column;
    }

    public void setColumn(Integer column) {
      this.column = column;
    }

    public String getObject() {
      return object;
    }

    public void setObject(String object) {
      this.object = object;
    }
  }
}
