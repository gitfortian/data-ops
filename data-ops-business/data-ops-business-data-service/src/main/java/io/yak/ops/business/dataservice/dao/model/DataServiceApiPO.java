package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据服务 API 持久化对象。 */
@Data
@TableName("yak_ops_data_service_api")
public class DataServiceApiPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String name;
  private String path;
  private Long dataSourceId;
  private String sqlText;
  private Integer maxRows;
  private Integer timeoutSeconds;
  private Boolean paginationEnabled;
  private Boolean enabled;
  private String authMode;
  private Boolean cacheEnabled;
  private Integer cacheTtlSeconds;
  private Integer cacheMaxEntries;
  private Boolean circuitBreakerEnabled;
  private Integer circuitFailureThreshold;
  private Integer circuitRecoverySeconds;
  private Long runtimeGeneration;
  private String description;
  private String sourceType;
  private String sourceRef;
  private Long sourceRevisionId;
  private Integer sourceRevisionNo;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
