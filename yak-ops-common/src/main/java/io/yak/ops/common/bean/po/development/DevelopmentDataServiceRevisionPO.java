package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** Immutable published Data Service Node revision. */
@Data
@TableName("yak_dev_data_service_revision")
public class DevelopmentDataServiceRevisionPO {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long nodeId;
  private Integer revisionNo;
  private Long sourceDraftRevision;
  private String definitionJson;
  private String checksum;
  private Instant createTime;
}
