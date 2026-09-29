package io.yak.ops.business.agent.repository;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.agent.dao.mapper.AgentReportMapper;
import io.yak.ops.business.agent.dao.model.AgentReportPO;
import io.yak.ops.business.agent.domain.ReportEntry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 报告持久化适配：全部读取隐含 is_deleted=0；归属人过滤在 SQL 条件层完成。 */
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class ReportRepositoryAdapter implements ReportRepository {

  private static final int NOT_DELETED = 0;

  private final AgentReportMapper mapper;

  @Override
  public PageData<ReportEntry> page(long userId, String keyword, int pageNo, int pageSize) {
    LambdaQueryWrapper<AgentReportPO> wrapper =
        new LambdaQueryWrapper<AgentReportPO>()
            .eq(AgentReportPO::getUserId, userId)
            .eq(AgentReportPO::getIsDeleted, NOT_DELETED)
            .like(keyword != null && !keyword.isBlank(), AgentReportPO::getTitle, keyword)
            .orderByDesc(AgentReportPO::getId);
    Page<AgentReportPO> result = mapper.selectPage(Page.of(pageNo, pageSize), wrapper);
    return PageData.of(
        result.getRecords().stream().map(ReportRepositoryAdapter::toEntry).toList(),
        result.getTotal(),
        pageNo,
        pageSize);
  }

  @Override
  public Optional<ReportEntry> findMeta(long userId, long reportId) {
    return Optional.ofNullable(
            mapper.selectOne(baseOwnerWrapper(userId).eq(AgentReportPO::getId, reportId)))
        .map(ReportRepositoryAdapter::toEntry);
  }

  @Override
  public Optional<String> content(long userId, long reportId) {
    return Optional.ofNullable(
            mapper.selectOne(baseOwnerWrapper(userId).eq(AgentReportPO::getId, reportId)))
        .map(AgentReportPO::getContent);
  }

  @Override
  public long insert(long userId, String sessionId, String title, String content) {
    AgentReportPO po = new AgentReportPO();
    po.setUserId(userId);
    po.setSessionId(sessionId);
    po.setTitle(title);
    po.setContent(content);
    po.setIsDeleted(NOT_DELETED);
    mapper.insert(po);
    return po.getId();
  }

  @Override
  public boolean softDelete(long userId, long reportId) {
    AgentReportPO patch = new AgentReportPO();
    patch.setIsDeleted(1);
    return mapper.update(patch, baseOwnerWrapper(userId).eq(AgentReportPO::getId, reportId)) > 0;
  }

  private LambdaQueryWrapper<AgentReportPO> baseOwnerWrapper(long userId) {
    return new LambdaQueryWrapper<AgentReportPO>()
        .eq(AgentReportPO::getUserId, userId)
        .eq(AgentReportPO::getIsDeleted, NOT_DELETED);
  }

  private static ReportEntry toEntry(AgentReportPO po) {
    return new ReportEntry(
        po.getId(), po.getSessionId(), po.getTitle(), po.getCreateTime(), po.getUpdateTime());
  }
}
