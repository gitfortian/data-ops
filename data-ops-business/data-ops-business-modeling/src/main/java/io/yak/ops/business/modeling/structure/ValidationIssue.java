package io.yak.ops.business.modeling.structure;

/**
 * One structured finding of the dialect validation (ticket 07). ERROR issues
 * block saving; WARNING issues surface to the editor without blocking.
 */
public record ValidationIssue(Severity severity, Scope scope, String target, String message) {

  public enum Severity {
    ERROR,
    WARNING
  }

  public enum Scope {
    TABLE,
    COLUMN,
    PRIMARY_KEY,
    INDEX,
    PARTITION,
    PROPERTY
  }
}
