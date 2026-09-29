package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A physical model: exactly one table (modeling caliber A1). The aggregate root
 * of the modeling catalog; structure editing arrives with ticket 05.
 *
 * <p>{@code directoryId} and {@code tagIds} are view-assembly fields populated
 * by the repository (null directoryId = uncategorized). Non-null
 * {@code deletedBy}/{@code deletedTime} mark a soft-deleted (recycle bin) model.
 * {@code layerCode}/{@code processId} are view-assembly fields populated by the
 * repository from the model row's derivation references (44; 2026-09-17 工作台列表展示).
 */
public record Model(
    Long id,
    String code,
    String name,
    ModelDialect dialect,
    String description,
    ModelStatus status,
    String owner,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    String layerCode,
    Long processId,
    Long directoryId,
    List<Long> tagIds,
    String deletedBy,
    LocalDateTime deletedTime,
    Long sourceDatasourceId,
    String sourceDatabase,
    String sourceTable,
    /** 统计周期(51;DWS/ADS 表级周期约定,如 1d)。 */
    String statPeriod,
    /** 应用/报表绑定(52;ADS 松散引用)。 */
    String appCode,
    String appName,
    /** 字段导入方式(血缘追溯):MANUAL/SOURCE_TABLE/MODEL/BUSINESS_PROCESS。 */
    String importMode,
    /** 来源模型ID(import_mode=MODEL时记录,血缘追溯)。 */
    Long sourceModelId,
    /** 当前发布版本ID(未发布时为 null)。 */
    Long publishedVersionId,
    /** 最新版本号(0=未发布)。 */
    Integer latestVersionNo,
    /** 业务域(semantic 松散引用,新建模型向导写入)。 */
    Long domainId,
    /** 最后更新人(V20;创建时同创建人,历史行为 null)。 */
    String updatedBy) {

  /** Business key discipline: identifier-like, no whitespace, project-unique (enforced by storage). */
  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$");

  /** Compatibility view without a source binding (pre-44 call sites and tests). */
  public Model(
      Long id,
      String code,
      String name,
      ModelDialect dialect,
      String description,
      ModelStatus status,
      String owner,
      LocalDateTime createTime,
      LocalDateTime updateTime,
      String layerCode,
      Long processId,
      Long directoryId,
      List<Long> tagIds,
      String deletedBy,
      LocalDateTime deletedTime) {
    this(id, code, name, dialect, description, status, owner, createTime, updateTime, layerCode,
        processId, directoryId, tagIds, deletedBy, deletedTime, null, null, null, null, null, null, null, null, null, 0, null, null);
  }

  /** Compatibility view without aggregate/application metadata (pre-51 call sites and tests). */
  public Model(
      Long id, String code, String name, ModelDialect dialect, String description,
      ModelStatus status, String owner, LocalDateTime createTime, LocalDateTime updateTime,
      String layerCode, Long processId, Long directoryId, List<Long> tagIds, String deletedBy,
      LocalDateTime deletedTime, Long sourceDatasourceId, String sourceDatabase,
      String sourceTable) {
    this(id, code, name, dialect, description, status, owner, createTime, updateTime, layerCode,
        processId, directoryId, tagIds, deletedBy, deletedTime, sourceDatasourceId, sourceDatabase,
        sourceTable, null, null, null, null, null, null, 0, null, null);
  }

  /** 逆向导入建模型:带来源绑定(08),供 44 反查对应 ODS 模型。 */
  public Model withSource(Long sourceDatasourceId, String sourceDatabase, String sourceTable) {
    return new Model(id, code, name, dialect, description, status, owner, createTime, updateTime,
        layerCode, processId, directoryId, tagIds, deletedBy, deletedTime, sourceDatasourceId,
        sourceDatabase, sourceTable, statPeriod, appCode, appName, importMode, sourceModelId,
        publishedVersionId, latestVersionNo, domainId, updatedBy);
  }

  /** 聚合/应用层元数据(51/52)。 */
  public Model withAggregateMeta(String statPeriod, String appCode, String appName) {
    return new Model(id, code, name, dialect, description, status, owner, createTime, updateTime,
        layerCode, processId, directoryId, tagIds, deletedBy, deletedTime, sourceDatasourceId,
        sourceDatabase, sourceTable, statPeriod, appCode, appName, importMode, sourceModelId,
        publishedVersionId, latestVersionNo, domainId, updatedBy);
  }

  /** 血缘信息(导入方式 + 来源模型)。 */
  public Model withImportLineage(String importMode, Long sourceModelId) {
    return new Model(id, code, name, dialect, description, status, owner, createTime, updateTime,
        layerCode, processId, directoryId, tagIds, deletedBy, deletedTime, sourceDatasourceId,
        sourceDatabase, sourceTable, statPeriod, appCode, appName, importMode, sourceModelId,
        publishedVersionId, latestVersionNo, domainId, updatedBy);
  }

  /** 版本发布信息:更新状态、发布版本指针。 */
  public Model withPublishState(ModelStatus newStatus, Long publishedVersionId, Integer latestVersionNo) {
    return new Model(id, code, name, dialect, description, newStatus, owner, createTime, updateTime,
        layerCode, processId, directoryId, tagIds, deletedBy, deletedTime, sourceDatasourceId,
        sourceDatabase, sourceTable, statPeriod, appCode, appName, importMode, sourceModelId,
        publishedVersionId, latestVersionNo, domainId, updatedBy);
  }

  public static Model create(String code, String name, ModelDialect dialect, String description) {
    return create(code, name, dialect, description, null, null, null);
  }

  public static Model create(String code, String name, ModelDialect dialect, String description, Long directoryId) {
    return create(code, name, dialect, description, directoryId, null, null);
  }

  public static Model create(String code, String name, ModelDialect dialect, String description, Long directoryId, String layerCode, Long processId) {
    return create(code, name, dialect, description, directoryId, layerCode, processId, null);
  }

  public static Model create(
      String code, String name, ModelDialect dialect, String description, Long directoryId,
      String layerCode, Long processId, Long domainId) {
    if (code == null || !CODE_PATTERN.matcher(code).matches()) {
      throw new IllegalArgumentException("Model code must match [A-Za-z0-9_][A-Za-z0-9_-]{0,127}");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Model name must not be blank");
    }
    if (dialect == null) {
      throw new IllegalArgumentException("Model dialect must not be null");
    }
    return new Model(
        null,
        code,
        name.trim(),
        dialect,
        normalize(description),
        ModelStatus.DRAFT,
        null,
        null,
        null,
        layerCode,
        processId,
        directoryId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        domainId,
        null);
  }

  public Model withPersisted(Long id, String owner, LocalDateTime createTime, LocalDateTime updateTime) {
    return new Model(
        id,
        code,
        name,
        dialect,
        description,
        status,
        owner,
        createTime,
        updateTime,
        layerCode,
        processId,
        directoryId,
        tagIds,
        deletedBy,
        deletedTime,
        sourceDatasourceId,
        sourceDatabase,
        sourceTable,
        statPeriod,
        appCode,
        appName,
        importMode,
        sourceModelId,
        publishedVersionId,
        latestVersionNo,
        domainId,
        owner);
  }

  public boolean isRecycled() {
    return deletedTime != null;
  }

  private static String normalize(String description) {
    return description == null || description.isBlank() ? null : description.trim();
  }
}
