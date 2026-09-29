package io.yak.ops.business.agent.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.agent.domain.QueryAuditItem;
import io.yak.ops.business.agent.domain.QueryEvidenceRecord;

/** 查询证据留痕契约：每次 run_dataset_query 执行（成功或失败）恰好落一条。 */
public interface QueryLogRepository {

  void record(QueryEvidenceRecord evidence);

  /** 审计分页（按归属人限定，可选会话/数据集过滤）。 */
  PageData<QueryAuditItem> pageAudit(
      long userId, String sessionId, Long datasetId, int pageNo, int pageSize);
}
