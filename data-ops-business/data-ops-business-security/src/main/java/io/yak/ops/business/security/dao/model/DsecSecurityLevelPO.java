package io.yak.ops.business.security.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 安全等级持久化对象(分级字典)。 */
@Data
@TableName("yak_dsec_security_level")
public class DsecSecurityLevelPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String levelCode;
  private String levelName;
  private Integer rankNo;
  private Long stdSecurityId;
  private String description;
  private String status;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
