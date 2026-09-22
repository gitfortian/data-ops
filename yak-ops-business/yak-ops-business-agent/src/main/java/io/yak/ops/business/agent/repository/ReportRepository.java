package io.yak.ops.business.agent.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.agent.domain.ReportEntry;
import java.util.Optional;

/** 报告持久化契约：所有读取都隐含 is_deleted=0 与归属人过滤。 */
public interface ReportRepository {

  PageData<ReportEntry> page(long userId, String keyword, int pageNo, int pageSize);

  Optional<ReportEntry> findMeta(long userId, long reportId);

  Optional<String> content(long userId, long reportId);

  long insert(long userId, String sessionId, String title, String content);

  boolean softDelete(long userId, long reportId);
}
