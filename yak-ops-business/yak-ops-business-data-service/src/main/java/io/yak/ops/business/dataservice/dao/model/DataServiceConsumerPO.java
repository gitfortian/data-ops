package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_ops_data_service_consumer")
public class DataServiceConsumerPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String name;
  private String description;
  private String accessScope;
  private Boolean enabled;
  private Integer defaultRateLimitPerMinute;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
