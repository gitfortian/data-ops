package io.yak.ops.business.dataservice.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据服务 API 文档元数据。 */
@Data
@TableName("yak_ops_data_service_documentation")
public class DataServiceDocumentationPO {

  @TableId(value = "api_id", type = IdType.INPUT)
  private Long apiId;
  private String sqlHash;
  private String parameterSchemaJson;
  private String responseSchemaJson;
  private LocalDateTime updateTime;
}
