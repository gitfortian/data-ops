package io.yak.ops.business.datasource.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.CredentialCipher;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.mapper.DataSourceMapper;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.business.datasource.dao.model.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * MyBatis DataSource DAO. Every business read/write requires the trusted
 * CurrentProject; no global (no-Project) fallback exists.
 *
 * <p>凭证列(connection_params / original_json)的加解密钩子放在本类而不是仓储适配器：
 * 离线/实时同步模块直接注入本 DAO 读取连接参数，只有在这里收口才保证任何消费者看到的都是明文、
 * 落库的都是密文。未配置密钥时 {@link CredentialCipher} 直通，行为与改造前一致。
 */
@Repository
@ConditionalOnDataSourceEnabled
public class DataSourceDaoImpl implements DataSourceDao {

  private final DataSourceMapper dataSourceMapper;
  private final CurrentProject currentProject;
  private final CredentialCipher credentialCipher;

  @org.springframework.beans.factory.annotation.Autowired
  public DataSourceDaoImpl(
      DataSourceMapper dataSourceMapper,
      CurrentProject currentProject,
      CredentialCipher credentialCipher) {
    this.dataSourceMapper = dataSourceMapper;
    this.currentProject = currentProject;
    this.credentialCipher = credentialCipher;
  }

  @Override
  public int addDataSource(DataSourcePO dataSourcePO) {
    long projectId = currentProjectId();
    if (dataSourcePO.getProjectId() != null
        && !Objects.equals(projectId, dataSourcePO.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    dataSourcePO.setProjectId(projectId);
    encryptCredentials(dataSourcePO);
    return dataSourceMapper.insert(dataSourcePO);
  }

  @Override
  public int editDataSource(DataSourcePO dataSourcePO) {
    long projectId = currentProjectId();
    if (dataSourcePO.getProjectId() != null
        && !Objects.equals(projectId, dataSourcePO.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    dataSourcePO.setProjectId(projectId);
    encryptCredentials(dataSourcePO);
    return dataSourceMapper.update(
        dataSourcePO,
        Wrappers.<DataSourcePO>lambdaUpdate()
            .eq(DataSourcePO::getProjectId, projectId)
            .eq(DataSourcePO::getId, dataSourcePO.getId()));
  }

  @Override
  public DataSourcePO selectById(Long id) {
    return selectById(currentProjectId(), id);
  }

  @Override
  public DataSourcePO selectById(Long projectId, Long id) {
    long trustedProjectId = requireCurrentProject(projectId);
    if (id == null) return null;
    return decryptCredentials(
        dataSourceMapper.selectOne(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(DataSourcePO::getId, id)));
  }

  @Override
  public List<DataSourcePO> selectByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    List<Long> normalizedIds = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (normalizedIds.isEmpty()) return List.of();
    long projectId = currentProjectId();
    List<DataSourcePO> rows =
        dataSourceMapper.selectList(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, projectId)
                .in(DataSourcePO::getId, normalizedIds));
    decryptCredentials(rows);
    return rows;
  }

  @Override
  public List<DataSourcePO> selectReferences(Long projectId, List<Long> ids) {
    long trustedProjectId = requireCurrentProject(projectId);
    if (ids == null || ids.isEmpty()) return List.of();
    if (ids.size() > 1000) throw new IllegalArgumentException("最多批量读取 1000 个数据源");
    List<Long> normalizedIds = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    if (normalizedIds.isEmpty()) return List.of();
    return dataSourceMapper.selectList(Wrappers.<DataSourcePO>lambdaQuery()
        .select(DataSourcePO::getId, DataSourcePO::getProjectId, DataSourcePO::getName, DataSourcePO::getDbType)
        .eq(DataSourcePO::getProjectId, trustedProjectId).in(DataSourcePO::getId, normalizedIds));
  }

  @Override
  public IPage<DataSourcePO> selectPage(PageQuery query) {
    long projectId = currentProjectId();
    PageQuery source = query == null
        ? new PageQuery(projectId, 1, 10, null, null, null, null, null)
        : query;
    long trustedProjectId =
        source.projectId() == null ? projectId : requireCurrentProject(source.projectId());
    PageQuery condition =
        new PageQuery(
            trustedProjectId,
            source.pageNo(),
            source.pageSize(),
            source.name(),
            source.keyword(),
            source.dbType(),
            source.environment(),
            source.connStatus());
    Page<DataSourcePO> page =
        Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
    IPage<DataSourcePO> result =
        dataSourceMapper.selectPage(
            page,
            queryWrapper(condition)
                .orderByDesc(DataSourcePO::getUpdateTime)
                .orderByDesc(DataSourcePO::getId));
    decryptCredentials(result.getRecords());
    return result;
  }

  @Override
  public DataSourceSummaryRow selectSummary() {
    return selectSummary(currentProjectId());
  }

  @Override
  public DataSourceSummaryRow selectSummary(Long projectId) {
    return dataSourceMapper.selectSummaryByProject(requireCurrentProject(projectId));
  }

  @Override
  public List<DataSourcePO> selectAll(DataSourceDbType dbType) {
    return selectAll(currentProjectId(), dbType);
  }

  @Override
  public List<DataSourcePO> selectAll(Long projectId, DataSourceDbType dbType) {
    long trustedProjectId = requireCurrentProject(projectId);
    List<DataSourcePO> rows =
        dataSourceMapper.selectList(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(dbType != null, DataSourcePO::getDbType, dbType)
                .orderByAsc(DataSourcePO::getName)
                .orderByAsc(DataSourcePO::getId));
    decryptCredentials(rows);
    return rows;
  }

  @Override
  public boolean existsByName(String name, Long excludeId) {
    return existsByName(currentProjectId(), name, excludeId);
  }

  @Override
  public boolean existsByName(Long projectId, String name, Long excludeId) {
    if (!StringUtils.hasText(name)) return false;
    long trustedProjectId = requireCurrentProject(projectId);
    Long count =
        dataSourceMapper.selectCount(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(DataSourcePO::getName, name)
                .ne(excludeId != null, DataSourcePO::getId, excludeId));
    return count != null && count > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    return deleteById(currentProjectId(), id);
  }

  @Override
  public boolean deleteById(Long projectId, Long id) {
    if (id == null) return false;
    long trustedProjectId = requireCurrentProject(projectId);
    return dataSourceMapper.delete(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(DataSourcePO::getId, id))
        > 0;
  }

  @Override
  public boolean updateConnectionStatus(Long id, DataSourceConnStatus connStatus) {
    return updateConnectionStatus(currentProjectId(), id, connStatus);
  }

  @Override
  public boolean updateConnectionStatus(
      Long projectId, Long id, DataSourceConnStatus connStatus) {
    if (id == null || connStatus == null) return false;
    long trustedProjectId = requireCurrentProject(projectId);
    return dataSourceMapper.update(
            null,
            Wrappers.<DataSourcePO>lambdaUpdate()
                .set(DataSourcePO::getConnStatus, connStatus)
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(DataSourcePO::getId, id))
        > 0;
  }

  @Override
  public List<Long> selectDistinctProjectIds() {
    return dataSourceMapper
        .selectObjs(new QueryWrapper<DataSourcePO>().select("DISTINCT project_id"))
        .stream()
        .filter(Objects::nonNull)
        .map(value -> ((Number) value).longValue())
        .sorted()
        .toList();
  }

  @Override
  public int encryptPlainCredentials(Long projectId) {
    long trustedProjectId = requireCurrentProject(projectId);
    // 这里刻意读原始行(不走解密钩子)：已经带 ENC: 前缀的行会被 encrypt 直通，天然幂等。
    List<DataSourcePO> rows =
        dataSourceMapper.selectList(
            Wrappers.<DataSourcePO>lambdaQuery().eq(DataSourcePO::getProjectId, trustedProjectId));
    int upgraded = 0;
    for (DataSourcePO row : rows) {
      String encryptedParams = credentialCipher.encrypt(row.getConnectionParams());
      String encryptedOriginal = credentialCipher.encrypt(row.getOriginalJson());
      boolean changed =
          !Objects.equals(encryptedParams, row.getConnectionParams())
              || !Objects.equals(encryptedOriginal, row.getOriginalJson());
      if (!changed) continue;
      dataSourceMapper.update(
          null,
          Wrappers.<DataSourcePO>lambdaUpdate()
              .set(DataSourcePO::getConnectionParams, encryptedParams)
              .set(DataSourcePO::getOriginalJson, encryptedOriginal)
              .eq(DataSourcePO::getProjectId, trustedProjectId)
              .eq(DataSourcePO::getId, row.getId()));
      upgraded++;
    }
    return upgraded;
  }

  private void encryptCredentials(DataSourcePO dataSourcePO) {
    dataSourcePO.setConnectionParams(credentialCipher.encrypt(dataSourcePO.getConnectionParams()));
    dataSourcePO.setOriginalJson(credentialCipher.encrypt(dataSourcePO.getOriginalJson()));
  }

  private DataSourcePO decryptCredentials(DataSourcePO dataSourcePO) {
    if (dataSourcePO == null) return null;
    dataSourcePO.setConnectionParams(credentialCipher.decrypt(dataSourcePO.getConnectionParams()));
    dataSourcePO.setOriginalJson(credentialCipher.decrypt(dataSourcePO.getOriginalJson()));
    return dataSourcePO;
  }

  private void decryptCredentials(List<DataSourcePO> dataSourcePOs) {
    dataSourcePOs.forEach(this::decryptCredentials);
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private long requireCurrentProject(Long requestedProjectId) {
    long projectId = currentProjectId();
    if (requestedProjectId == null || requestedProjectId <= 0L
        || !Objects.equals(projectId, requestedProjectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return projectId;
  }

  private LambdaQueryWrapper<DataSourcePO> queryWrapper(PageQuery query) {
    LambdaQueryWrapper<DataSourcePO> wrapper = Wrappers.lambdaQuery();
    if (StringUtils.hasText(query.keyword())) {
      wrapper.and(
          nested ->
              nested
                  .like(DataSourcePO::getName, query.keyword())
                  .or()
                  .like(DataSourcePO::getJdbcUrl, query.keyword()));
    }
    return wrapper
        .eq(DataSourcePO::getProjectId, query.projectId())
        .like(StringUtils.hasText(query.name()), DataSourcePO::getName, query.name())
        .eq(query.dbType() != null, DataSourcePO::getDbType, query.dbType())
        .eq(query.environment() != null, DataSourcePO::getEnvironment, query.environment())
        .eq(query.connStatus() != null, DataSourcePO::getConnStatus, query.connStatus());
  }
}
