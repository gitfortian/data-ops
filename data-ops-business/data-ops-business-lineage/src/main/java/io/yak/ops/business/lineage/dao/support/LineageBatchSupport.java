package io.yak.ops.business.lineage.dao.support;

/** Shared batch sizing utility used by DAO batching and focused performance tests. */
public final class LineageBatchSupport {

  private LineageBatchSupport() {
  }

  public static int batchExecutionCount(int itemCount, int batchSize) {
    if (itemCount <= 0) return 0;
    if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
    return (itemCount + batchSize - 1) / batchSize;
  }
}
