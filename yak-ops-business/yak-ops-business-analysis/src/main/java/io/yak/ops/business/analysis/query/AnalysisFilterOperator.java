package io.yak.ops.business.analysis.query;

/** Semantic filter operators; SQL-specific syntax remains a Dataset runtime concern. */
public enum AnalysisFilterOperator {
  EQ,
  NE,
  GT,
  GTE,
  LT,
  LTE,
  CONTAINS
}
