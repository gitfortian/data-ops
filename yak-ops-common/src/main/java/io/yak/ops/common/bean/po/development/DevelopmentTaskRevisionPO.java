package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** Immutable published data-development task revision. */
@Data
@TableName("yak_dev_task_revision")
public class DevelopmentTaskRevisionPO {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long nodeId;
  private Integer revisionNo;
  private Long sourceDraftRevision;
  private String taskType;
  private Integer schemaVersion;
  private String content;
  private String configJson;
  private String checksum;
  private Instant createTime;
}
