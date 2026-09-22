package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据服务调用日志持久化对象。 */
@Data
@TableName("yak_ops_data_service_call_log")
public class DataServiceCallLogPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private Long apiId;
  private String serviceName;
  private String servicePath;
  private String callerType;
  private Long apiKeyId;
  private String apiKeyName;
  private String apiKeyPrefix;
  private String paramsJson;
  private Boolean success;
  private Long durationMs;
  private Integer rowCount;
  private String errorMessage;
  private LocalDateTime createTime;
}
