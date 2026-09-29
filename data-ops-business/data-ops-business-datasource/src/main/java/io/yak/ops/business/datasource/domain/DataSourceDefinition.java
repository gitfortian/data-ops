package io.yak.ops.business.datasource.domain;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 数据源聚合根；当前保留 {@code DataSourceDefinition} 历史命名以维持兼容。
 *
 * <p>业务侧只通过聚合行为修改配置和连接状态；持久化重建统一使用 {@link #restore}，避免 setter 绕过领域约束。
 */
@Getter
@EqualsAndHashCode
@ToString
public class DataSourceDefinition {
  private Long id;
  private Long projectId;
  private String name;
  private DataSourceDbType dbType;
  @ToString.Exclude private String jdbcUrl;
  private DataSourceEnvironment environment;
  private DataSourceConnStatus connStatus;
  private String remark;
  @ToString.Exclude private String connectionParams;
  @ToString.Exclude private String originalJson;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  private DataSourceDefinition() {}

  /** 创建新的数据源聚合。创建只代表配置有效，连接状态必须从 UNKNOWN 开始。 */
  public static DataSourceDefinition create(
      String name,
      DataSourceDbType dbType,
      ConnectionProfile connectionProfile,
      DataSourceEnvironment environment,
      String remark) {
    DataSourceDefinition definition = new DataSourceDefinition();
    definition.name = requireText(name, "数据源名称不能为空");
    definition.dbType = Objects.requireNonNull(dbType, "数据源类型不能为空");
    definition.environment = Objects.requireNonNull(environment, "数据源环境不能为空");
    definition.remark = normalizeNullable(remark);
    definition.replaceConnectionProfile(connectionProfile);
    return definition;
  }

  /** 保留旧持久化重建签名，供未接入 Project Space 的调用方继续使用。 */
  public static DataSourceDefinition restore(
      Long id,
      String name,
      DataSourceDbType dbType,
      String jdbcUrl,
      DataSourceEnvironment environment,
      DataSourceConnStatus connStatus,
      String remark,
      String connectionParams,
      String originalJson,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
    return restore(
        id,
        null,
        name,
        dbType,
        jdbcUrl,
        environment,
        connStatus,
        remark,
        connectionParams,
        originalJson,
        createTime,
        updateTime);
  }

  /** Rehydrates an aggregate from persistence without replaying business state transitions. */
  public static DataSourceDefinition restore(
      Long id,
      Long projectId,
      String name,
      DataSourceDbType dbType,
      String jdbcUrl,
      DataSourceEnvironment environment,
      DataSourceConnStatus connStatus,
      String remark,
      String connectionParams,
      String originalJson,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
    DataSourceDefinition definition = new DataSourceDefinition();
    definition.id = id;
    definition.projectId = projectId;
    definition.name = name;
    definition.dbType = dbType;
    definition.jdbcUrl = jdbcUrl;
    definition.environment = environment;
    definition.connStatus = connStatus;
    definition.remark = remark;
    definition.connectionParams = connectionParams;
    definition.originalJson = originalJson;
    definition.createTime = createTime;
    definition.updateTime = updateTime;
    return definition;
  }

  /** 回填数据库生成的主键，供创建命令发布对外事件；已有主键时拒绝二次赋值。 */
  public void assignId(Long id) {
    if (id == null || id <= 0L) {
      throw new IllegalArgumentException("id 必须大于 0");
    }
    if (this.id != null && !this.id.equals(id)) {
      throw new IllegalStateException("数据源主键不允许被改写");
    }
    this.id = id;
  }

  /** 在持久化前绑定可信 Project Space；已经归属其他项目时拒绝覆盖。 */
  public void assignProject(Long projectId) {
    if (projectId == null || projectId <= 0L) {
      throw new IllegalArgumentException("projectId 必须大于 0");
    }
    if (this.projectId != null && !this.projectId.equals(projectId)) {
      throw new IllegalStateException("数据源不允许跨 Project Space 迁移");
    }
    this.projectId = projectId;
  }

  /**
   * 修改数据源可编辑配置。
   *
   * <p>数据源类型创建后不可修改；连接配置发生变化后，旧连接测试结果失效并回到 UNKNOWN。
   */
  public void updateConfiguration(
      String name,
      DataSourceDbType requestedType,
      ConnectionProfile connectionProfile,
      DataSourceEnvironment environment,
      String remark) {
    assertTypeUnchanged(requestedType);
    this.name = requireText(name, "数据源名称不能为空");
    this.environment = Objects.requireNonNull(environment, "数据源环境不能为空");
    this.remark = normalizeNullable(remark);
    replaceConnectionProfile(connectionProfile);
  }

  /** 将当前连接配置作为一个完整值对象读取。 */
  public ConnectionProfile connectionProfile() {
    return new ConnectionProfile(jdbcUrl, connectionParams, originalJson);
  }

  /** 整体替换连接配置，并使旧连接测试结果失效。 */
  public void replaceConnectionProfile(ConnectionProfile connectionProfile) {
    ConnectionProfile profile =
        Objects.requireNonNull(connectionProfile, "数据源连接配置不能为空");
    this.jdbcUrl = profile.jdbcUrl();
    this.connectionParams = profile.normalizedJson();
    this.originalJson = profile.originalJson();
    markConnectionUnknown();
  }

  /** 校验编辑请求没有修改数据源类型。 */
  public void assertTypeUnchanged(DataSourceDbType requestedType) {
    DataSourceDbType target = Objects.requireNonNull(requestedType, "数据源类型不能为空");
    if (dbType != null && dbType != target) {
      throw new IllegalArgumentException("编辑数据源时不允许修改数据源类型");
    }
  }

  /** 最近一次针对当前已保存配置的连接测试成功。 */
  public void markConnected() {
    connStatus = DataSourceConnStatus.CONNECTED;
  }

  /** 最近一次针对当前已保存配置的连接测试失败。 */
  public void markDisconnected() {
    connStatus = DataSourceConnStatus.DISCONNECTED;
  }

  /** 当前连接配置尚未被验证，或原有验证结果已因配置修改失效。 */
  public void markConnectionUnknown() {
    connStatus = DataSourceConnStatus.UNKNOWN;
  }

  private static String requireText(String value, String message) {
    String normalized = normalizeNullable(value);
    if (normalized == null) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  private static String normalizeNullable(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
