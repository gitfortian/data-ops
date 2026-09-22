package io.yak.ops.business.security.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 资产分级查询 SPI:下游(data-service/预览/建模)按对象自然键读等级与分类。 */
public interface SecurityClassificationQueryApi {

  /** 单个对象的分级视图;未定级返回 null。 */
  ClassificationView find(String objectKey);

  /** 批量分级视图,objectKey -> 视图;未定级的键不返回。 */
  Map<String, ClassificationView> findMany(Collection<String> objectKeys);

  /** 某列(库/表/列)命中的启用状态分级标签列表。 */
  List<ClassificationView> findByTable(String dbName, String tableName);
}
