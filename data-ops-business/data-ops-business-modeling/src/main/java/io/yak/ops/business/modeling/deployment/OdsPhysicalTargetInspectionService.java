package io.yak.ops.business.modeling.deployment;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.version.ModelVersionService;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * R11 / PR-B: read-only physical-target evidence for an exact, current,
 * immutable ODS version. D3 is preserved: Modeling MUST NOT execute DDL,
 * claim a deployment receipt, create a sync job, or claim data readiness.
 */
@Service
@RequiredArgsConstructor
public class OdsPhysicalTargetInspectionService {
  private static final int MAX_COLUMNS = 1000;

  private final ModelRepository models;
  private final ModelVersionService versions;
  private final DataSourceReader dataSources;
  private final DataSourceCatalogReader catalog;
  private final ActionAuthorization authorization;

  public record ColumnDifference(String column, String issue,
                                 String expectedType, String observedType) {}
  public record Report(Long modelId, int versionNo, Long versionId,
                       Long targetDatasourceId, String targetDatabase, String targetSchema,
                       String targetTable, String state, int frozenColumns,
                       int observedColumns, List<ColumnDifference> differences,
                       List<String> limitations, String observedCatalogSha256) {}

  public Report inspect(Long modelId, int versionNo, Long targetDatasourceId,
                        String database, String schema) {
    authorization.requirePermission(DataSourcePermissionCode.READ);
    if (modelId == null || modelId <= 0 || versionNo <= 0 || targetDatasourceId == null
        || targetDatasourceId <= 0 || !valid(database) || (schema != null && !valid(schema))) {
      throw invalid("须选择当前项目有权访问的模型版本、目标数据源及精确数据库/Schema");
    }
    Model model = models.findById(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, "模型不存在或无权访问"));
    if (model.isRecycled() || !"ODS".equalsIgnoreCase(model.layerCode())
        || model.publishedVersionId() == null || !Objects.equals(model.latestVersionNo(), versionNo)) {
      throw invalid("只允许核验当前发布指针上的 ODS 物理版本，不能把草稿或历史版本当作目标部署");
    }
    var version = versions.getVersionDetail(modelId, versionNo);
    if (version == null || version.version() == null || version.structure() == null
        || !Objects.equals(version.version().modelId(), modelId)
        || !Objects.equals(version.version().id(), model.publishedVersionId())
        || version.version().versionNo() != versionNo
        || !Objects.equals(version.structure().modelId(), modelId)) {
      throw invalid("版本结构与模型发布指针不一致");
    }
    var target = dataSources.require(targetDatasourceId);
    String sourceDialect = model.dialect().name();
    String targetDialect = target.getDbType().name();
    if (!sourceDialect.equals(targetDialect)
        && !("POSTGRESQL".equals(sourceDialect) && "POSTGRE_SQL".equals(targetDialect))) {
      throw invalid("目标数据源数据库类型与发布版本方言不同，禁止将该脚本当成可部署脚本");
    }
    StructureView frozen = version.structure();
    String table = StringUtils.hasText(frozen.tableName()) ? frozen.tableName() : model.code();
    if (!valid(table)) throw invalid("发布快照中的物理表名无效");
    List<StructureView.ColumnView> columns = frozen.columns() == null ? List.of() : frozen.columns();
    if (columns.isEmpty() || columns.size() > MAX_COLUMNS) {
      throw invalid("发布结构无列或超出最大核验范围");
    }
    Set<String> seen = new HashSet<>();
    for (var column : columns) {
      if (column == null || !StringUtils.hasText(column.columnName())
          || !seen.add(column.columnName())) throw invalid("冻结版本中存在缺失或重复列");
    }

    // Discovery is a hint only; always verify columns through the fresh
    // datasource catalog owner. No exception is translated into 'not found'.
    List<CatalogColumn> observed;
    try {
      observed = catalog.listColumnsFresh(targetDatasourceId, database, schema, table);
    } catch (RuntimeException unavailable) {
      return new Report(modelId, versionNo, version.version().id(),
          targetDatasourceId, database, schema, table, "NOT_CHECKABLE",
          columns.size(), 0, List.of(),
          List.of("目标 Catalog 不可访问或目标表不存在，无法区分；不认定部署失败或成功",
              "本次仅执行元数据读取，不执行 DDL、创建同步任务或确认数据质量"), null);
    }
    if (observed == null || observed.isEmpty() || observed.size() > MAX_COLUMNS) {
      return new Report(modelId, versionNo, version.version().id(),
          targetDatasourceId, database, schema, table, "NOT_CHECKABLE",
          columns.size(), observed == null ? 0 : observed.size(), List.of(),
          List.of("目标 Catalog 未返回有效列，不能认定已部署"), null);
    }
    Map<String, CatalogColumn> live = new HashMap<>();
    List<ColumnDifference> differences = new ArrayList<>();
    for (var item : observed) {
      if (item == null || live.putIfAbsent(item.name(), item) != null) {
        return new Report(modelId, versionNo, version.version().id(),
            targetDatasourceId, database, schema, table, "NOT_CHECKABLE",
            columns.size(), observed.size(), List.of(),
            List.of("Catalog 返回无效或重复物理列，不能认定目标状态"), null);
      }
    }
    for (var column : columns) {
      var actual = live.remove(column.columnName());
      if (actual == null) {
        differences.add(new ColumnDifference(column.columnName(), "MISSING",
            column.dataType(), null));
      } else if (!sameType(column.dataType(), actual.typeName())
          || !Objects.equals(column.nullable(), actual.nullable())) {
        differences.add(new ColumnDifference(column.columnName(), "TYPE_OR_NULLABILITY_REVIEW",
            column.dataType() + " / nullable=" + column.nullable(),
            actual.typeName() + " / nullable=" + actual.nullable()));
      }
    }
    live.forEach((name, actual) -> differences.add(
        new ColumnDifference(name, "UNEXPECTED", null, actual.typeName())));
    return new Report(modelId, versionNo, version.version().id(),
        targetDatasourceId, database, schema, table,
        differences.isEmpty() ? "COLUMN_MATCH_NEEDS_DDL_REVIEW" : "STRUCTURE_DIFFERS",
        columns.size(), observed.size(), List.copyOf(differences),
        List.of("只核验当前可读取的物理列类型与可空性；索引、分区、默认值、历史 DDL 和表数据未认证",
            "Catalog 当前匹配不是可持久回读的部署回执，更不是同步运行成功或质量通过",
            "使用已有离线同步模块独立创建、发布、执行并核对真实任务版本与执行证据"), catalogHash(observed));
  }

  /** Fingerprint only the columns actually observed by the DataSource owner. */
  private static String catalogHash(List<CatalogColumn> columns) {
    String canonical = columns.stream().sorted(Comparator.comparing(CatalogColumn::name))
        .map(c -> String.join("\u001f", c.name(),
            String.valueOf(c.typeName()), String.valueOf(c.size()),
            String.valueOf(c.scale()), String.valueOf(c.nullable()),
            String.valueOf(c.ordinalPosition()), String.valueOf(c.primaryKey())))
        .reduce("", (a, b) -> a + "\u001e" + b);
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private static boolean valid(String value) {
    return StringUtils.hasText(value) && value.length() <= 128 && value.equals(value.trim());
  }

  private static boolean sameType(String expected, String observed) {
    // Conservative equality avoids falsely claiming that INT/INTEGER, VARCHAR
    // widths, precision, or dialect aliases are known to be equivalent.
    return expected != null && observed != null
        && expected.replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
            .equals(observed.replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
  }

  private static ModelingException invalid(String message) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, message);
  }
}
