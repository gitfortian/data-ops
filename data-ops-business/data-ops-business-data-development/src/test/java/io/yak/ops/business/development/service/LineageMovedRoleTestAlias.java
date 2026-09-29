package io.yak.ops.business.development.service;

import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;

/** Test-only alias for the moved lineage transaction role. */
class DevelopmentLineageWriteTransaction
    extends io.yak.ops.business.development.lineage.DevelopmentLineageWriteTransaction {

  DevelopmentLineageWriteTransaction(
      DevelopmentTaskRevisionRepository revisionRepository, DevelopmentSqlLineageService lineage) {
    super(revisionRepository, lineage);
  }
}
