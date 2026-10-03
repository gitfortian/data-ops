package io.yak.ops.business.security.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据访问流水持久化对象(追加式,无 update)。 */
@Data
@TableName("yak_dsec_access_log")
public class DsecAccessLogPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private LocalDateTime accessTime;
  private String actor;
  private String resourceType;
  private String resourceKey;
  private String resourceName;
  private String action;
  private String levelCode;
  private String decision;
  private Integer masked;
  private String algoCode;
  private String source;
  private LocalDateTime createTime;
}
