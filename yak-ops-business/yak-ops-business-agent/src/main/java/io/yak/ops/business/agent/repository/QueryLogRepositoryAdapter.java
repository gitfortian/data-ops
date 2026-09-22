package io.yak.ops.business.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.dao.mapper.AgentQueryLogMapper;
import io.yak.ops.business.agent.dao.mapper.AgentSessionMapper;
import io.yak.ops.business.agent.dao.model.AgentQueryLogPO;
import io.yak.ops.business.agent.dao.model.AgentSessionPO;
import io.yak.ops.business.agent.domain.QueryAuditItem;
import io.yak.ops.business.agent.domain.QueryEvidenceRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/** 查询证据留痕适配。留痕失败只记录告警，不反向影响已发生的查询事实。 */
@Slf4j
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class QueryLogRepositoryAdapter implements QueryLogRepository {

  private final AgentQueryLogMapper mapper;
  private final AgentSessionMapper sessionMapper;

  @Override
  public PageData<QueryAuditItem> pageAudit(
      long userId, String sessionId, Long datasetId, int pageNo, int pageSize) {
    // 先按归属人查出其全部会话ID，再对 query_log 做 in 过滤 + 分页（替代 XML 联表）
    List<String> sessionIds =
        sessionMapper
            .selectList(
                new LambdaQueryWrapper<AgentSessionPO>()
                    .select(AgentSessionPO::getSessionId)
                    .eq(AgentSessionPO::getUserId, userId))
            .stream()
            .map(AgentSessionPO::getSessionId)
            .toList();
    if (sessionIds.isEmpty()) {
      return PageData.of(List.of(), 0, pageNo, pageSize);
    }

    LambdaQueryWrapper<AgentQueryLogPO> wrapper =
        new LambdaQueryWrapper<AgentQueryLogPO>()
            .in(AgentQueryLogPO::getSessionId, sessionIds)
            .eq(sessionId != null && !sessionId.isBlank(),
                AgentQueryLogPO::getSessionId, sessionId)
            .eq(datasetId != null, AgentQueryLogPO::getDatasetId, datasetId)
            .orderByDesc(AgentQueryLogPO::getId);
    Page<AgentQueryLogPO> result = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    return PageData.of(
        result.getRecords().stream().map(QueryLogRepositoryAdapter::toAuditItem).toList(),
        result.getTotal(),
        pageNo,
        pageSize);
  }

  private static QueryAuditItem toAuditItem(AgentQueryLogPO po) {
    return new QueryAuditItem(
        po.getId(),
        po.getSessionId(),
        po.getDatasetId(),
        po.getQueryId(),
        po.getStatus(),
        po.getErrorMessage(),
        po.getReturnedRows(),
        po.getTruncated(),
        po.getElapsedMillis(),
        po.getCreateTime());
  }

  @Override
  public void record(QueryEvidenceRecord evidence) {
    try {
      AgentQueryLogPO po = new AgentQueryLogPO();
      po.setSessionId(evidence.sessionId());
      po.setDatasetId(evidence.datasetId());
      po.setQueryId(evidence.queryId());
      po.setRequestJson(evidence.requestJson());
      po.setStatus(evidence.status().name());
      po.setErrorMessage(evidence.errorMessage());
      po.setReturnedRows(evidence.returnedRows());
      po.setTruncated(evidence.truncated());
      po.setElapsedMillis(evidence.elapsedMillis());
      mapper.insert(po);
    } catch (Exception e) {
      log.warn(
          "failed to record agent query evidence: sessionId={}, datasetId={}",
          evidence.sessionId(),
          evidence.datasetId(),
          e);
    }
  }
}
