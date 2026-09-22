package io.yak.ops.business.datasource.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.util.List;

/** 数据源数据访问接口，只暴露持久化模型和 DAO 查询条件。 */
public interface DataSourceDao {

  int addDataSource(DataSourcePO dataSourcePO);

  int editDataSource(DataSourcePO dataSourcePO);

  DataSourcePO selectById(Long id);

  DataSourcePO selectById(Long projectId, Long id);

  List<DataSourcePO> selectByIds(List<Long> ids);

  IPage<DataSourcePO> selectPage(PageQuery query);

  DataSourceSummaryRow selectSummary();

  DataSourceSummaryRow selectSummary(Long projectId);

  List<DataSourcePO> selectAll(DataSourceDbType dbType);

  List<DataSourcePO> selectAll(Long projectId, DataSourceDbType dbType);

  boolean existsByName(String name, Long excludeId);

  boolean existsByName(Long projectId, String name, Long excludeId);

  boolean deleteById(Long id);

  boolean deleteById(Long projectId, Long id);

  boolean updateConnectionStatus(Long id, DataSourceConnStatus connStatus);

  boolean updateConnectionStatus(
      Long projectId, Long id, DataSourceConnStatus connStatus);

  /**
   * 健康巡检专用的跨项目扫描口:只取 project_id 列,不读连接参数,不受 CurrentProject 约束。
   * 业务读写一律走项目窄化方法,不要用这个口子绕过。
   */
  List<Long> selectDistinctProjectIds();

  /**
   * 把该项目下仍是明文的凭证列洗成密文,返回真正被改写的行数(Ticket 05 存量补加密)。
   * 已带 {@code ENC:} 前缀的行原样跳过,因此可重复执行;无密钥模式下不会有行被改写。
   */
  int encryptPlainCredentials(Long projectId);

  /** DAO 自有分页条件，不依赖 HTTP DTO。 */
  record PageQuery(
      Long projectId,
      int pageNo,
      int pageSize,
      String name,
      String keyword,
      DataSourceDbType dbType,
      DataSourceEnvironment environment,
      DataSourceConnStatus connStatus) {

    public PageQuery(
        int pageNo,
        int pageSize,
        String name,
        String keyword,
        DataSourceDbType dbType,
        DataSourceEnvironment environment,
        DataSourceConnStatus connStatus) {
      this(null, pageNo, pageSize, name, keyword, dbType, environment, connStatus);
    }
  }
}
