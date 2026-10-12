package io.yak.ops.business.metadata.autoingest;

import io.yak.ops.business.metadata.dao.mapper.AutoIngestEventMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Called inside Metadata's existing transactional table-row upsert.
 * A visible event therefore proves that a source table was committed.
 * Unsuccessful or dry-run collection has no new delivery event.
 */
@Component
public class AutoIngestOutbox {
  private final AutoIngestEventMapper mapper;

  public AutoIngestOutbox(AutoIngestEventMapper mapper) {
    this.mapper = mapper;
  }

  public void enqueueCommittedTables(Long projectId, Long runId,
                                      Map<String, Long> committedIds) {
    if (projectId == null || projectId <= 0 || runId == null || runId <= 0) {
      throw new IllegalArgumentException("自动纳管缺少已保存项目或采集 Run 身份");
    }
    LocalDateTime now = LocalDateTime.now();
    for (Long id : committedIds.values()) {
      if (id == null || id <= 0) {
        throw new IllegalStateException("已提交表实体 ID 无法回读，禁止伪造纳管事件");
      }
      if (mapper.enqueue(projectId, runId, id, now) != 1) {
        throw new IllegalStateException("可靠自动纳管事件入库失败，元数据批次需回滚");
      }
    }
  }
}
