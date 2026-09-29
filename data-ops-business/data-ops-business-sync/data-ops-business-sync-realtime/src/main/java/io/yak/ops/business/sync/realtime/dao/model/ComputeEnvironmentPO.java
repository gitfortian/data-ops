package io.yak.ops.business.sync.realtime.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_compute_environment")
public class ComputeEnvironmentPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String name;
  private String engineType;
  private String deploymentMode;
  private String submitterType;
  private String configJson;
  private Boolean enabled;
  private Boolean isDefault;
  private Integer version;
  private String lastCheckStatus;
  private String lastCheckMessage;
  private LocalDateTime lastCheckTime;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
