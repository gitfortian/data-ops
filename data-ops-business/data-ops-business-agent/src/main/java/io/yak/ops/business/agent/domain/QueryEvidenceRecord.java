package io.yak.ops.business.agent.domain;

/** 查询证据留痕记录。每次 run_dataset_query 执行（成功或失败）都必须落一条；datasetId/queryId 承载数据集证据。 */
public record QueryEvidenceRecord(
    String sessionId,
    Long datasetId,
    String queryId,
    String requestJson,
    Status status,
    String errorMessage,
    Integer returnedRows,
    Boolean truncated,
    Long elapsedMillis) {

  public enum Status {
    SUCCESS,
    FAILED,
    REJECTED
  }
}
