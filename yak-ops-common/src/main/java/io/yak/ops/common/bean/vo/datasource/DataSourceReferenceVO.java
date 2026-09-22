package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单个下游模块对某数据源的引用统计。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceReferenceVO {

  private String moduleName;
  private long count;
}
