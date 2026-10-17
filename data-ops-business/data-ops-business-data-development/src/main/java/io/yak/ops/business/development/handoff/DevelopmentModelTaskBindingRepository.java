package io.yak.ops.business.development.handoff;

import io.yak.ops.core.project.CurrentProject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * DataDev-owned JDBC storage. Reads and writes are always scoped to the
 * trusted current Project; no modeling tables are queried here.
 */
@Repository
public class DevelopmentModelTaskBindingRepository {
  private static final String FIELDS = "id, project_id, model_id, model_version_id, "
      + "model_version_no, mapping_revision_id, mapping_sha256, logical_sha256, "
      + "physical_sha256, current_standard_sha256, node_id, task_revision_id, "
      + "task_revision_no, task_definition_sha256, target_datasource_id, "
      + "target_database, target_schema, target_table, write_mode, "
      + "binding_digest, idempotency_key, state";
  private final JdbcTemplate jdbc;
  private final CurrentProject currentProject;

  public DevelopmentModelTaskBindingRepository(
      JdbcTemplate jdbc, CurrentProject currentProject) {
    this.jdbc = jdbc;
    this.currentProject = currentProject;
  }

  /** Serializes binding decisions on one DataDev node, not a browser-provided project. */
  public void lockNode(long nodeId) {
    long projectId = currentProject.requireProjectId();
    var locked = jdbc.queryForList(
        "SELECT id FROM yak_dev_node WHERE id=? AND project_id=? AND deleted=0 FOR UPDATE",
        Long.class, nodeId, projectId);
    if (locked.size() != 1) {
      throw new IllegalStateException("当前项目 SQL 节点不存在或已删除");
    }
  }

  public Optional<DevelopmentModelTaskBinding> byKey(String key) {
    return jdbc.query(
        "SELECT " + FIELDS + " FROM yak_dev_model_task_binding "
            + "WHERE project_id=? AND idempotency_key=? LIMIT 1",
        DevelopmentModelTaskBindingRepository::map,
        currentProject.requireProjectId(), key).stream().findFirst();
  }

  public Optional<DevelopmentModelTaskBinding> byId(long id) {
    return jdbc.query(
        "SELECT " + FIELDS + " FROM yak_dev_model_task_binding "
            + "WHERE project_id=? AND id=? LIMIT 1",
        DevelopmentModelTaskBindingRepository::map,
        currentProject.requireProjectId(), id).stream().findFirst();
  }

  public DevelopmentModelTaskBinding insert(DevelopmentModelTaskBinding candidate) {
    if (candidate.projectId() != currentProject.requireProjectId()
        || candidate.id() != null) {
      throw new IllegalArgumentException("绑定记录的 Project 或持久身份非法");
    }
    String sql = "INSERT INTO yak_dev_model_task_binding "
        + "(project_id, model_id, model_version_id, model_version_no, mapping_revision_id, "
        + "mapping_sha256, logical_sha256, physical_sha256, current_standard_sha256, "
        + "node_id, task_revision_id, task_revision_no, task_definition_sha256, "
        + "target_datasource_id, target_database, target_schema, target_table, write_mode, "
        + "binding_digest, idempotency_key, state, create_time) "
        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))";
    var key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var p = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      p.setLong(1, candidate.projectId());
      p.setLong(2, candidate.modelId());
      p.setLong(3, candidate.modelVersionId());
      p.setInt(4, candidate.modelVersionNo());
      p.setLong(5, candidate.mappingRevisionId());
      p.setString(6, candidate.mappingSha256());
      p.setString(7, candidate.logicalSha256());
      p.setString(8, candidate.physicalSha256());
      p.setString(9, candidate.currentStandardSha256());
      p.setLong(10, candidate.nodeId());
      p.setLong(11, candidate.taskRevisionId());
      p.setInt(12, candidate.taskRevisionNo());
      p.setString(13, candidate.taskDefinitionSha256());
      p.setLong(14, candidate.targetDatasourceId());
      p.setString(15, candidate.targetDatabase());
      p.setString(16, candidate.targetSchema());
      p.setString(17, candidate.targetTable());
      p.setString(18, candidate.writeMode());
      p.setString(19, candidate.bindingDigest());
      p.setString(20, candidate.idempotencyKey());
      p.setString(21, candidate.state());
      return p;
    }, key);
    var generated = key.getKey();
    if (generated == null) throw new IllegalStateException("绑定事务未返回持久化主键");
    return byId(generated.longValue()).orElseThrow(
        () -> new IllegalStateException("绑定后无法从 DataDev 原域回读真实身份"));
  }

  private static DevelopmentModelTaskBinding map(ResultSet rs, int row) throws SQLException {
    return new DevelopmentModelTaskBinding(
        rs.getLong("id"), rs.getLong("project_id"), rs.getLong("model_id"),
        rs.getLong("model_version_id"), rs.getInt("model_version_no"),
        rs.getLong("mapping_revision_id"), rs.getString("mapping_sha256"),
        rs.getString("logical_sha256"), rs.getString("physical_sha256"),
        rs.getString("current_standard_sha256"), rs.getLong("node_id"),
        rs.getLong("task_revision_id"), rs.getInt("task_revision_no"),
        rs.getString("task_definition_sha256"), rs.getLong("target_datasource_id"),
        rs.getString("target_database"), rs.getString("target_schema"),
        rs.getString("target_table"), rs.getString("write_mode"),
        rs.getString("binding_digest"), rs.getString("idempotency_key"),
        rs.getString("state"));
  }
}
