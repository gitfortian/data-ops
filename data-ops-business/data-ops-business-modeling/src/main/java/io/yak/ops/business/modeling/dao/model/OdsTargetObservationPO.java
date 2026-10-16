package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Append-only evidence: current target catalog columns observed at a known time. */
@Data
@TableName("yak_modeling_ods_target_observation")
public class OdsTargetObservationPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private Long modelId;
  private Long modelVersionId;
  private Integer versionNo;
  private Long targetDatasourceId;
  private String targetDatabase;
  private String targetSchema;
  private String targetTable;
  private String catalogSha256;
  private String frozenChecksum;
  private Integer observedColumnCount;
  private String observedBy;
  private LocalDateTime observedAt;
}
