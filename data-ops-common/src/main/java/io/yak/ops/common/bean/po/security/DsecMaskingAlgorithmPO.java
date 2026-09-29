package io.yak.ops.common.bean.po.security;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 脱敏算法字典持久化对象。 */
@Data
@TableName("yak_dsec_masking_algorithm")
public class DsecMaskingAlgorithmPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String algoCode;
  private String algoName;
  private String params;
  private Integer builtin;
  private String description;
  private String status;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
