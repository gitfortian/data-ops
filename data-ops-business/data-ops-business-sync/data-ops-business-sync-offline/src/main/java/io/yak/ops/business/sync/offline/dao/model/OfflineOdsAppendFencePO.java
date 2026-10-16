package io.yak.ops.business.sync.offline.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Permanent, project-scoped first-write fence for an ODS APPEND destination. */
@Data
@TableName("yak_offline_ods_append_fence")
public class OfflineOdsAppendFencePO {
  @TableId(type = IdType.AUTO) private Long id;
  private Long projectId;
  private Long targetDatasourceId;
  private String targetTableKey;
  private Long jobDefinitionId;
  private Long modelId;
  private Integer versionNo;
  private Long targetObservationId;
  private String scopeSha256;
  private LocalDateTime createTime;
}
