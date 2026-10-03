package io.yak.ops.business.security.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据分类持久化对象(分类树)。 */
@Data
@TableName("yak_dsec_data_category")
public class DsecDataCategoryPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String categoryCode;
  private String categoryName;
  private String parentCode;
  private Integer sortOrder;
  private String description;
  private String status;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
