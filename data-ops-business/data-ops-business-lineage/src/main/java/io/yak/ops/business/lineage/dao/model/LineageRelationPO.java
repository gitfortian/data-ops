package io.yak.ops.business.lineage.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.sql.Timestamp;
import lombok.Data;

/** Database row for yak_metadata_relation. */
@Data
@TableName("yak_metadata_relation")
public class LineageRelationPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private Long sourceAssetId;
  private Long targetAssetId;
  private String relationType;
  private String sourceType;
  private String sourceId;
  private String expression;
  private BigDecimal confidence;
  private String version;
  private Timestamp observedAt;
  private String properties;
  private Timestamp createTime;
  private Timestamp updateTime;
}
