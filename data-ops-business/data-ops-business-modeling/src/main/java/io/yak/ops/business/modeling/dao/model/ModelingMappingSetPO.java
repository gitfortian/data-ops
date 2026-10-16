package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** Project-owned mutable root; frozen revisions live in a different append-only table. */
@Data
@TableName("yak_modeling_mapping_set")
public class ModelingMappingSetPO {
  @TableId(type = IdType.AUTO) private Long id;
  private Long projectId;
  private Long physicalModelId;
  private Long logicalModelId;
  private Integer logicalVersionNo;
  private String logicalSnapshotSha;
  private String physicalStructureSha;
  private Long draftRevision;
  @ToString.Exclude private String mappingJson;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
