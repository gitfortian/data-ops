package io.yak.ops.business.datasource.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Optional;

/** 数据源领域仓储。 */
public interface DataSourceRepository {
  Optional<DataSourceDefinition> findById(Long id);

  /** 落库并把数据库生成的主键回填到聚合，便于创建命令发布对外事件。 */
  boolean insert(DataSourceDefinition definition);

  boolean update(DataSourceDefinition definition);

  boolean delete(Long id);

  boolean existsByName(String name, Long excludeId);

  PageData<DataSourceDefinition> page(DataSourceQuery query);

  List<DataSourceDefinition> findAll(DataSourceDbType dbType);

  DataSourceSummary summary();

  boolean updateConnectionStatus(Long id, DataSourceConnStatus status);

  /** 巡检专用的跨项目 project_id 清单;业务读写不要用它绕过项目窄化。 */
  List<Long> distinctProjectIds();

  /** 把当前项目下存量明文凭证补加密，返回改写行数；幂等，可重复执行。 */
  int encryptStoredCredentials();
}
