package io.yak.ops.business.sync.offline.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao.PageQuery;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

/** MyBatis 持久化模型与任务定义领域模型之间的适配器。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineJobDefinitionRepositoryAdapter implements OfflineJobDefinitionRepository {
  private final OfflineJobDefinitionDao dao;
  private final DataSourceDao dataSourceDao;
  private final CurrentProject currentProject;

  @Override
  public void lock(Long id) {
    requireProject();
    if (dao.lockById(id) == null) {
      throw new IllegalArgumentException("离线同步任务不存在：" + id);
    }
  }

  @Override
  public Optional<OfflineJobDefinition> findById(Long id) {
    long projectId = requireProject();
    OfflineJobDefinitionPO po = dao.selectById(id);
    requireCurrentProject(po, projectId);
    return Optional.ofNullable(toDomain(po, Map.of()));
  }

  @Override
  public Optional<OfflineJobDefinition> findForViewById(Long id) {
    long projectId = requireProject();
    OfflineJobDefinitionPO po = dao.selectById(id);
    if (po == null) return Optional.empty();
    requireCurrentProject(po, projectId);
    return Optional.of(toDomain(po, dataSourceNames(List.of(po))));
  }

  @Override
  public boolean insert(OfflineJobDefinition definition) {
    bindCurrentProject(definition);
    return dao.insert(toPO(definition));
  }

  @Override
  public boolean update(OfflineJobDefinition definition) {
    bindCurrentProject(definition);
    return dao.updateById(toPO(definition));
  }

  @Override
  public boolean delete(Long id) {
    requireProject();
    return dao.deleteById(id);
  }

  @Override
  public boolean existsByName(String jobName, Long excludeId) {
    requireProject();
    return dao.existsByName(jobName, excludeId);
  }

  @Override
  public PageData<OfflineJobDefinition> page(OfflineDefinitionQuery query) {
    requireProject();
    return page(query, false);
  }

  @Override
  public PageData<OfflineJobDefinition> pageForView(OfflineDefinitionQuery query) {
    requireProject();
    return page(query, true);
  }

  @Override
  public List<ProjectDefinitionRef> findScheduledForReconciliation() {
    return dao.selectWithCronForReconciliation().stream()
        .map(
            po ->
                new ProjectDefinitionRef(
                    requirePersistedProject(po.getProjectId(), po.getId()),
                    requirePositive(po.getId(), "definitionId")))
        .toList();
  }

  private PageData<OfflineJobDefinition> page(
      OfflineDefinitionQuery query,
      boolean includeDisplayNames) {
    long projectId = requireProject();
    OfflineDefinitionQuery q = query == null
        ? new OfflineDefinitionQuery(1, 10, null, null, null, null, null, null, null, null, null)
        : query;
    IPage<OfflineJobDefinitionPO> page = dao.selectPage(
        new PageQuery(
            q.current(), q.pageSize(), q.id(), q.jobName(), q.status(),
            q.sourceType(), q.sinkType(), q.sourceTable(), q.sinkTable(),
            q.createTimeStart(), q.createTimeEnd()));
    List<OfflineJobDefinitionPO> pageRecords = page.getRecords();
    requireCurrentProject(pageRecords, projectId);
    Map<Long, String> dataSourceNames =
        includeDisplayNames ? dataSourceNames(pageRecords) : Map.of();
    List<OfflineJobDefinition> records = pageRecords.stream()
        .map(po -> toDomain(po, dataSourceNames))
        .toList();
    return new PageData<>(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
  }

  private OfflineJobDefinition toDomain(
      OfflineJobDefinitionPO po,
      Map<Long, String> dataSourceNames) {
    if (po == null) return null;
    OfflineJobDefinition value = new OfflineJobDefinition();
    BeanUtils.copyProperties(po, value);
    value.requireProjectId();
    if (!dataSourceNames.isEmpty()) {
      value.setSourceDatasourceName(dataSourceNames.get(po.getSourceDatasourceId()));
      value.setSinkDatasourceName(dataSourceNames.get(po.getSinkDatasourceId()));
    }
    return value;
  }

  private OfflineJobDefinitionPO toPO(OfflineJobDefinition value) {
    OfflineJobDefinitionPO po = new OfflineJobDefinitionPO();
    BeanUtils.copyProperties(value, po);
    return po;
  }

  private Map<Long, String> dataSourceNames(List<OfflineJobDefinitionPO> definitions) {
    if (definitions == null || definitions.isEmpty()) return Map.of();
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    for (OfflineJobDefinitionPO definition : definitions) {
      if (definition == null) continue;
      if (definition.getSourceDatasourceId() != null) {
        ids.add(definition.getSourceDatasourceId());
      }
      if (definition.getSinkDatasourceId() != null) {
        ids.add(definition.getSinkDatasourceId());
      }
    }
    if (ids.isEmpty()) return Map.of();

    Map<Long, String> names = new LinkedHashMap<>();
    for (DataSourcePO dataSource : dataSourceDao.selectByIds(List.copyOf(ids))) {
      if (dataSource != null && dataSource.getId() != null) {
        names.put(dataSource.getId(), dataSource.getName());
      }
    }
    return names;
  }

  private void requireCurrentProject(List<OfflineJobDefinitionPO> definitions, long projectId) {
    if (definitions == null || definitions.isEmpty()) return;
    for (OfflineJobDefinitionPO definition : definitions) {
      requireCurrentProject(definition, projectId);
    }
  }

  private void requireCurrentProject(OfflineJobDefinitionPO definition, long projectId) {
    if (definition == null) return;
    if (!Objects.equals(definition.getProjectId(), projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  private long requireProject() {
    return currentProject.requireProjectId();
  }

  private void bindCurrentProject(OfflineJobDefinition definition) {
    if (definition == null) throw new IllegalArgumentException("离线同步任务不能为空");
    long projectId = requireProject();
    if (definition.getProjectId() != null && !Objects.equals(definition.getProjectId(), projectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    definition.setProjectId(projectId);
  }

  private long requirePersistedProject(Long projectId, Long definitionId) {
    if (projectId == null || projectId <= 0L) {
      throw new IllegalStateException("离线同步任务缺少 Project：" + definitionId);
    }
    return projectId;
  }

  private long requirePositive(Long value, String name) {
    if (value == null || value <= 0L) throw new IllegalStateException(name + " 必须大于 0");
    return value;
  }
}
