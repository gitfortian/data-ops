package io.yak.ops.common.bean.po.sync.offline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 离线同步任务发布版本快照（append-only，W1-2 契约 C2）。 */
@Data
@TableName("yak_offline_job_revision")
public class OfflineJobRevisionPO {
  @TableId(type = IdType.AUTO)
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
