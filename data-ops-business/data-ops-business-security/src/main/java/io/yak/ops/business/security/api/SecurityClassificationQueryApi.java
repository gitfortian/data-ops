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

  /** 某物理表当前启用的分级；按完整项目内来源与物理坐标匹配。 */
  List<ClassificationView> findActiveByTable(
      String datasourceId, String dbName, String tableName);

  /** Legacy project-scoped lookup for the Asset publish precheck. */
  List<ClassificationView> findByTable(String dbName, String tableName);
}
