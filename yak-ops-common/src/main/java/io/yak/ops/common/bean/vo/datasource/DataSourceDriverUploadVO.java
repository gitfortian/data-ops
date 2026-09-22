package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JDBC 驱动包上传结果(字段名与前端 DriverUploadResult 对齐)。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceDriverUploadVO {

  private String dbType;
  private String fileName;
  private String path;
}
