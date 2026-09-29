package io.yak.ops.business.datasource.dao.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.SqlExecutionAuditDao;
import io.yak.ops.business.datasource.dao.mapper.SqlExecutionAuditMapper;
import io.yak.ops.business.datasource.dao.mapper.SqlStatementExecutionAuditMapper;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditQuery;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditSummaryRow;
import io.yak.ops.business.datasource.dao.model.SqlStatementExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlStatementTypeCountRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus implementation of the project-scoped SQL execution audit DAO. */
@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class SqlExecutionAuditDaoImpl implements SqlExecutionAuditDao {

  private final SqlExecutionAuditMapper executionMapper;
  private final SqlStatementExecutionAuditMapper statementMapper;

  @Override
  public void insertExecution(SqlExecutionAuditPO execution) {
    if (execution == null || execution.getProjectId() == null || execution.getProjectId() <= 0L) {
      throw new IllegalArgumentException("SQL execution audit requires projectId");
    }
    executionMapper.insert(execution);
  }

  @Override
  public void insertStatements(List<SqlStatementExecutionAuditPO> statements) {
    if (statements == null || statements.isEmpty()) return;
    for (SqlStatementExecutionAuditPO statement : statements) statementMapper.insert(statement);
  }

  @Override
  public IPage<SqlExecutionAuditPO> selectPage(SqlExecutionAuditQuery query) {
    SqlExecutionAuditQuery condition = requireQuery(query);
    return executionMapper.selectAuditPage(
        Page.of(condition.getPageNo(), condition.getPageSize()), condition);
  }

  @Override
  public SqlExecutionAuditPO selectByExecutionId(Long projectId, String executionId) {
    if (executionId == null || executionId.isBlank()) return null;
    long scopedProjectId = requireProjectId(projectId);
    return executionMapper.selectOne(
        Wrappers.<SqlExecutionAuditPO>lambdaQuery()
            .eq(SqlExecutionAuditPO::getProjectId, scopedProjectId)
            .eq(SqlExecutionAuditPO::getExecutionId, executionId.trim())
            .last("LIMIT 1"));
  }

  @Override
  public List<SqlStatementExecutionAuditPO> selectStatements(Long projectId, String executionId) {
    if (executionId == null || executionId.isBlank()) return List.of();
    if (selectByExecutionId(projectId, executionId) == null) return List.of();
    return statementMapper.selectList(
        Wrappers.<SqlStatementExecutionAuditPO>lambdaQuery()
            .eq(SqlStatementExecutionAuditPO::getExecutionId, executionId.trim())
            .orderByAsc(SqlStatementExecutionAuditPO::getStatementIndex)
            .orderByAsc(SqlStatementExecutionAuditPO::getId));
  }

  @Override
  public SqlExecutionAuditSummaryRow selectSummary(SqlExecutionAuditQuery query) {
    SqlExecutionAuditSummaryRow row = executionMapper.selectAuditSummary(requireQuery(query));
    return row == null ? new SqlExecutionAuditSummaryRow() : row;
  }

  @Override
  public long selectP95DurationMs(SqlExecutionAuditQuery query) {
    Long value = executionMapper.selectP95DurationMs(requireQuery(query));
    return value == null ? 0L : Math.max(0L, value);
  }

  @Override
  public List<SqlStatementTypeCountRow> selectStatementTypeCounts(SqlExecutionAuditQuery query) {
    List<SqlStatementTypeCountRow> rows =
        executionMapper.selectStatementTypeCounts(requireQuery(query));
    return rows == null ? List.of() : List.copyOf(rows);
  }

  private static SqlExecutionAuditQuery requireQuery(SqlExecutionAuditQuery query) {
    if (query == null || query.getProjectId() == null || query.getProjectId() <= 0L) {
      throw new IllegalArgumentException("SQL execution audit query requires projectId");
    }
    return query;
  }

  private static long requireProjectId(Long projectId) {
    if (projectId == null || projectId <= 0L) {
      throw new IllegalArgumentException("projectId must be positive");
    }
    return projectId;
  }
}
