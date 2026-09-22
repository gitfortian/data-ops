package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据服务 API Key 持久化对象。明文 Key 永不落库。 */
@Data
@TableName("yak_ops_data_service_api_key")
public class DataServiceApiKeyPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long apiId;
  private Long consumerId;
  private String name;
  private String keyPrefix;
  private String keyHash;
  private Boolean enabled;
  private Integer rateLimitPerMinute;
  private LocalDateTime expiresAt;
  private LocalDateTime lastUsedAt;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
