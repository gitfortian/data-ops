package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentNodeMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.common.bean.po.development.DevelopmentNodePO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.task.model.SqlDialect;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for data-development tree node metadata. */
@Repository
public class DevelopmentNodeRepositoryAdapter implements DevelopmentNodeRepository {

  private static final long ROOT_DIRECTORY_ID = 0L;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final DevelopmentNodeMapper mapper;
  private final JdbcTemplate jdbcTemplate;
  private final CurrentProject currentProject;

  @Autowired
  public DevelopmentNodeRepositoryAdapter(
      DevelopmentNodeMapper mapper,
      JdbcTemplate jdbcTemplate,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations will fail closed. */
  public DevelopmentNodeRepositoryAdapter(
      DevelopmentNodeMapper mapper, JdbcTemplate jdbcTemplate) {
    this(mapper, jdbcTemplate, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public DevelopmentNode insert(
      String name,
      String type,
      Long ignoredProjectId,
      Long directoryId,
      boolean configured) {
    Long projectId = requiredProjectId();
    Instant now = Instant.now();
    DevelopmentNodePO po = new DevelopmentNodePO();
    po.setName(name);
    po.setType(type);
    po.setProjectId(projectId);
    po.setDirectoryId(toStoredDirectoryId(directoryId));
    po.setConfigured(configured);
    po.setDeleted(false);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return toDomain(po, false, null);
  }

  @Override
  public Optional<DevelopmentNode> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<DevelopmentNodePO>()
                    .eq(DevelopmentNodePO::getId, id)
                    .eq(DevelopmentNodePO::getProjectId, projectId)))
        .map(po -> toDomain(
            po,
            hasUnpublishedChanges(po.getId()),
            loadSqlDialect(po.getId(), projectId)));
  }

  @Override
  public List<DevelopmentNode> list() {
    Long projectId = requiredProjectId();
    List<DevelopmentNodePO> nodes = mapper.selectList(
        new LambdaQueryWrapper<DevelopmentNodePO>()
            .eq(DevelopmentNodePO::getProjectId, projectId)
            .orderByAsc(DevelopmentNodePO::getName)
            .orderByAsc(DevelopmentNodePO::getId));
    Map<Long, Boolean> pendingPublishByNodeId = loadPendingPublishByNodeId(projectId);
    Map<Long, String> sqlDialectByNodeId = loadSqlDialectByNodeId(projectId);
    return nodes.stream()
        .map(po -> toDomain(
            po,
            Boolean.TRUE.equals(pendingPublishByNodeId.get(po.getId())),
            sqlDialectByNodeId.get(po.getId())))
        .toList();
  }

  @Override
  public long count() {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<DevelopmentNodePO>()
            .eq(DevelopmentNodePO::getProjectId, projectId));
  }

  @Override
  public boolean existsByName(Long directoryId, String name) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .eq(DevelopmentNodePO::getDirectoryId, toStoredDirectoryId(directoryId))
                .eq(DevelopmentNodePO::getName, name))
        > 0L;
  }

  @Override
  public boolean existsInDirectory(Long directoryId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .eq(DevelopmentNodePO::getDirectoryId, toStoredDirectoryId(directoryId)))
        > 0L;
  }

  @Override
  public boolean updateName(Long id, String name) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .set(DevelopmentNodePO::getName, name)
                .set(DevelopmentNodePO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean updateConfigured(Long id, boolean configured) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .set(DevelopmentNodePO::getConfigured, configured)
                .set(DevelopmentNodePO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean updateUpdatedBy(Long id, String updatedBy) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .set(DevelopmentNodePO::getUpdatedBy, updatedBy))
        > 0;
  }

  @Override
  public boolean updateDirectoryId(Long id, Long directoryId) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .set(DevelopmentNodePO::getDirectoryId, toStoredDirectoryId(directoryId))
                .set(DevelopmentNodePO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private Long toStoredDirectoryId(Long directoryId) {
    return directoryId == null || directoryId <= 0L ? ROOT_DIRECTORY_ID : directoryId;
  }

  private Map<Long, Boolean> loadPendingPublishByNodeId(Long projectId) {
    Map<Long, Boolean> result = new HashMap<>();
    String sql = "SELECT d.node_id, d.draft_revision, MAX(r.source_draft_revision) AS published_draft_revision "
        + "FROM yak_dev_task_draft d "
        + "JOIN yak_dev_node n ON n.id = d.node_id "
        + "LEFT JOIN yak_dev_task_revision r ON r.node_id = d.node_id "
        + "WHERE n.project_id = ? "
        + "GROUP BY d.node_id, d.draft_revision";
    jdbcTemplate.query(
        sql,
        rs -> {
          long nodeId = rs.getLong("node_id");
          long draftRevision = rs.getLong("draft_revision");
          long publishedDraftRevision = rs.getLong("published_draft_revision");
          boolean hasPublishedRevision = !rs.wasNull();
          result.put(nodeId, !hasPublishedRevision || draftRevision > publishedDraftRevision);
        },
        projectId);
    return result;
  }

  private Map<Long, String> loadSqlDialectByNodeId(Long projectId) {
    Map<Long, String> result = new HashMap<>();
    jdbcTemplate.query(
        "SELECT d.node_id, d.config_json FROM yak_dev_task_draft d "
            + "JOIN yak_dev_node n ON n.id = d.node_id "
            + "WHERE n.project_id = ? AND n.deleted = 0 AND UPPER(d.task_type) = 'SQL'",
        rs -> {
          result.put(rs.getLong("node_id"), parseSqlDialect(rs.getString("config_json")));
        },
        projectId);
    return result;
  }

  private String loadSqlDialect(Long nodeId, Long projectId) {
    List<String> values = jdbcTemplate.query(
        "SELECT d.config_json FROM yak_dev_task_draft d "
            + "JOIN yak_dev_node n ON n.id = d.node_id "
            + "WHERE d.node_id = ? AND n.project_id = ? AND n.deleted = 0 "
            + "AND UPPER(d.task_type) = 'SQL'",
        (rs, rowNum) -> parseSqlDialect(rs.getString("config_json")),
        nodeId,
        projectId);
    return values.isEmpty() ? null : values.get(0);
  }

  private String parseSqlDialect(String configJson) {
    if (configJson == null || configJson.isBlank()) return SqlDialect.GENERIC.name();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(configJson);
      if (root == null || !root.isObject()) return SqlDialect.GENERIC.name();
      String value = text(root, "dialect");
      if (value == null) value = text(root, "databaseType");
      if (value == null) value = text(root, "dbType");
      return SqlDialect.parseOrGeneric(value).name();
    } catch (Exception ignored) {
      return SqlDialect.GENERIC.name();
    }
  }

  private String text(JsonNode root, String key) {
    JsonNode value = root.get(key);
    if (value == null || value.isNull()) return null;
    String text = value.asText();
    return text == null || text.isBlank() ? null : text.trim();
  }

  private boolean hasUnpublishedChanges(Long nodeId) {
    Long draftRevision = jdbcTemplate.queryForObject(
        "SELECT MAX(draft_revision) FROM yak_dev_task_draft WHERE node_id = ?",
        Long.class,
        nodeId);
    if (draftRevision == null) return false;

    Long publishedDraftRevision = jdbcTemplate.queryForObject(
        "SELECT MAX(source_draft_revision) FROM yak_dev_task_revision WHERE node_id = ?",
        Long.class,
        nodeId);
    return publishedDraftRevision == null || draftRevision > publishedDraftRevision;
  }

  private DevelopmentNode toDomain(
      DevelopmentNodePO po,
      boolean pendingPublish,
      String sqlDialect) {
    Long directoryId = po.getDirectoryId() == null || po.getDirectoryId() == ROOT_DIRECTORY_ID
        ? null
        : po.getDirectoryId();
    return new DevelopmentNode(
        po.getId(),
        po.getName(),
        po.getType(),
        po.getProjectId(),
        directoryId,
        Boolean.TRUE.equals(po.getConfigured()),
        po.getCreateTime(),
        po.getUpdateTime(),
        po.getUpdatedBy(),
        pendingPublish,
        "SQL".equalsIgnoreCase(po.getType()) ? sqlDialect : null);
  }
}
