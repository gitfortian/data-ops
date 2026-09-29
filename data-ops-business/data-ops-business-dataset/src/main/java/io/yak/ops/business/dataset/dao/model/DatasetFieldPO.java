package io.yak.ops.business.dataset.dao.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** yak_dataset_field table row. The table uses the composite key (version_id, field_id). */
@Data
@TableName("yak_dataset_field")
public class DatasetFieldPO {

  private String fieldId;
  private Long versionId;
  private String physicalName;
  private String displayName;
  private String dataType;
  private Boolean nullable;
  private String description;
  private String defaultRole;
  private Integer sortOrder;
}
