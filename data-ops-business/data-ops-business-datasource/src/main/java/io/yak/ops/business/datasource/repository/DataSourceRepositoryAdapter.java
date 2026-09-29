package io.yak.ops.business.datasource.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.DataSourceDao.PageQuery;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** MyBatis persistence adapter; every business operation is scoped to the trusted CurrentProject. */
@Repository
@ConditionalOnDataSourceEnabled
public class DataSourceRepositoryAdapter implements DataSourceRepository {

  private final DataSourceDao dao;
  private final CurrentProject currentProject;

  @Autowired
  public DataSourceRepositoryAdapter(DataSourceDao dao, CurrentProject currentProject) {
    this.dao = dao;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor. Calls still fail closed until a CurrentProject is supplied. */
  public DataSourceRepositoryAdapter(DataSourceDao dao) {
    this(dao, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public Optional<DataSourceDefinition> findById(Long id) {
    return Optional.ofNullable(toDomain(dao.selectById(currentProjectId(), id)));
  }

  @Override
  public boolean insert(DataSourceDefinition definition) {
    long projectId = currentProjectId();
    if (definition.getProjectId() != null && !Objects.equals(projectId, definition.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    definition.assignProject(projectId);
    DataSourcePO persisted = toPO(definition);
    if (dao.addDataSource(persisted) <= 0) return false;
    // 主键回填到聚合，创建命令才能带着 dataSourceId 发布对外事件。
    if (persisted.getId() != null) definition.assignId(persisted.getId());
    return true;
  }

  @Override
  public boolean update(DataSourceDefinition definition) {
    ensureCurrentProject(definition.getProjectId());
    return dao.editDataSource(toPO(definition)) > 0;
  }

  @Override
  public boolean delete(Long id) {
    return dao.deleteById(currentProjectId(), id);
  }

  @Override
  public boolean existsByName(String name, Long excludeId) {
    return dao.existsByName(currentProjectId(), name, excludeId);
  }

  @Override
  public PageData<DataSourceDefinition> page(DataSourceQuery query) {
    DataSourceQuery condition =
        query == null ? new DataSourceQuery(1, 10, null, null, null, null, null) : query;
    IPage<DataSourcePO> page =
        dao.selectPage(
            new PageQuery(
                currentProjectId(),
                condition.pageNo(),
                condition.pageSize(),
                condition.name(),
                condition.keyword(),
                condition.dbType(),
                condition.environment(),
                condition.connStatus()));
    List<DataSourceDefinition> records = page.getRecords().stream().map(this::toDomain).toList();
    return new PageData<>(
        records,
        page.getTotal(),
        page.getPages(),
        page.getCurrent(),
        page.getSize());
  }

  @Override
  public List<DataSourceDefinition> findAll(DataSourceDbType dbType) {
    return dao.selectAll(currentProjectId(), dbType).stream().map(this::toDomain).toList();
  }

  @Override
  public DataSourceSummary summary() {
    DataSourceSummaryRow row = dao.selectSummary(currentProjectId());
    return row == null
        ? DataSourceSummary.empty()
        : new DataSourceSummary(
            row.getTotal(),
            row.getConnected(),
            row.getDisconnected(),
            row.getUnknown(),
            row.getEnvironmentCount());
  }

  @Override
  public boolean updateConnectionStatus(Long id, DataSourceConnStatus status) {
    return dao.updateConnectionStatus(currentProjectId(), id, status);
  }

  @Override
  public List<Long> distinctProjectIds() {
    // 唯一的跨项目读取口:只回 project_id,连接参数不外泄。
    return dao.selectDistinctProjectIds();
  }

  @Override
  public int encryptStoredCredentials() {
    return dao.encryptPlainCredentials(currentProjectId());
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private void ensureCurrentProject(Long ownerProjectId) {
    long projectId = currentProjectId();
    if (ownerProjectId == null || !Objects.equals(projectId, ownerProjectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  private DataSourceDefinition toDomain(DataSourcePO po) {
    if (po == null) return null;
    return DataSourceDefinition.restore(
        po.getId(),
        po.getProjectId(),
        po.getName(),
        po.getDbType(),
        po.getJdbcUrl(),
        po.getEnvironment(),
        po.getConnStatus(),
        po.getRemark(),
        po.getConnectionParams(),
        po.getOriginalJson(),
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private DataSourcePO toPO(DataSourceDefinition definition) {
    DataSourcePO po = new DataSourcePO();
    po.setId(definition.getId());
    po.setProjectId(definition.getProjectId());
    po.setName(definition.getName());
    po.setDbType(definition.getDbType());
    po.setJdbcUrl(definition.getJdbcUrl());
    po.setEnvironment(definition.getEnvironment());
    po.setConnStatus(definition.getConnStatus());
    po.setRemark(definition.getRemark());
    po.setConnectionParams(definition.getConnectionParams());
    po.setOriginalJson(definition.getOriginalJson());
    po.setCreateTime(definition.getCreateTime());
    po.setUpdateTime(definition.getUpdateTime());
    return po;
  }
}
