package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 离线同步任务发布版本快照；持久化只允许追加，不含 ORM 契约。 */
@Data
public class OfflineJobRevision {
  private Long id;
  private Long projectId;
  private Long jobDefinitionId;
  private Integer versionNo;
  @ToString.Exclude private String definitionJson;
  @ToString.Exclude private String jobSpecJson;
  private String configDigest;
  private String checksum;
  private Integer sourceVersion;
  private String createdBy;
  private LocalDateTime createTime;
}
