package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** Append-only snapshot. It does NOT authorize physical model publication. */
@Data
@TableName("yak_modeling_mapping_revision")
public class ModelingMappingRevisionPO {
  @TableId(type = IdType.AUTO) private Long id;
  private Long projectId;
  private Long mappingSetId;
  private Integer revisionNo;
  private Long draftRevision;
  private String logicalSnapshotSha;
  private String physicalStructureSha;
  private String contentSha;
  @ToString.Exclude private String frozenJson;
  private String frozenBy;
  private LocalDateTime frozenAt;
}
