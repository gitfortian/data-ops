package io.yak.ops.business.development.service;

import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only alias for the moved lineage transaction role. */
class DevelopmentLineageWriteTransaction
    extends io.yak.ops.business.development.lineage.DevelopmentLineageWriteTransaction {

  DevelopmentLineageWriteTransaction(JdbcTemplate jdbc, DevelopmentSqlLineageService lineage) {
    super(jdbc, lineage);
  }
}
