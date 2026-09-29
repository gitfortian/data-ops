package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_ops_data_service_consumer_ip_access_rule")
public class DataServiceConsumerIpAccessRulePO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long consumerId;
  private String ruleType;
  private String networkCidr;
  private String description;
  private Boolean enabled;
  private LocalDateTime expiresAt;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
