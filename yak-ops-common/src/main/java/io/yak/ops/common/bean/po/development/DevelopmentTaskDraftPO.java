package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** Mutable data-development task draft. */
@Data
@TableName("yak_dev_task_draft")
public class DevelopmentTaskDraftPO {

  @TableId(type = IdType.INPUT)
  private Long nodeId;

  private String taskType;
  private Integer schemaVersion;
  private String content;
  private String configJson;
  private Long draftRevision;
  private Instant createTime;
  private Instant updateTime;
}
