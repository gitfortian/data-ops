package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** Mutable Data Service Node authoring draft. */
@Data
@TableName("yak_dev_data_service_draft")
public class DevelopmentDataServiceDraftPO {

  @TableId(type = IdType.INPUT)
  private Long nodeId;

  private String definitionJson;
  private Long draftRevision;
  private Instant createTime;
  private Instant updateTime;
}
