package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_ops_data_service_consumer_ip_access_policy")
public class DataServiceConsumerIpAccessPolicyPO {
  @TableId(value = "consumer_id", type = IdType.INPUT)
  private Long consumerId;
  private String mode;
  private LocalDateTime updateTime;
}
