package io.yak.ops.common.bean.po.security;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 资产分级标签持久化对象。 */
@Data
@TableName("yak_dsec_classification")
public class DsecClassificationPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String objectType;
  private String objectKey;
  private Long datasourceId;
  private String dbName;
  private String tableName;
  private String columnName;
  private String objectName;
  private Long levelId;
  private Long categoryId;
  private String source;
  private Integer confidence;
  private Long discoveryRuleId;
  private String status;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
