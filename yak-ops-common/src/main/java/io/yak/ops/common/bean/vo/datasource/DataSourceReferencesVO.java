package io.yak.ops.common.bean.vo.datasource;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 某数据源的跨模块引用清单。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceReferencesVO {

  private long total;
  private List<DataSourceReferenceVO> references;
}
