package io.yak.ops.business.dataset.dao.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** yak_dataset_draft_field table row. Editor field overrides before version commit. */
@Data
@TableName("yak_dataset_draft_field")
public class DatasetDraftFieldPO {

  private String fieldId;
  private Long datasetId;
  private String physicalName;
  private String displayName;
  private String dataType;
  private Boolean nullable;
  private String description;
  private String defaultRole;
  private Integer sortOrder;
}
