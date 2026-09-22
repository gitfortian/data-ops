package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_ops_data_service_consumer_api_grant")
public class DataServiceConsumerApiGrantPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long consumerId;
  private Long apiId;
  private LocalDateTime createTime;
}
