package io.yak.ops.business.modeling.importer;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Read-only, source-exact ODS continuation. Only Modeling owns candidate identity;
 * DataSource owns live table/namespace verification. Never use model code as identity.
 */
@Service
@RequiredArgsConstructor
public class OdsSourceCandidateService {
  private final DataSourceCatalogReader catalog;
  private final ModelRepository models;
  private final ActionAuthorization authorization;

  public record Candidate(Long modelId, String code, String name, String status,
                          String sourceDatabase, String sourceSchema, String sourceTable) {}
  public record Lookup(String state, String reason, Long datasourceId, String database,
                       String schema, String table, List<Candidate> candidates) {}

  private static String clean(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  public Lookup find(Long datasourceId, String database, String schema, String table) {
    authorization.requirePermission(DataSourcePermissionCode.READ);
    String db = clean(database);
    String namespace = clean(schema);
    String name = clean(table);
    if (datasourceId == null || datasourceId <= 0 || db == null || name == null
        || db.length() > 128 || (namespace != null && namespace.length() > 128)
        || name.length() > 128) {
      return new Lookup("UNVERIFIED", "缺少合法来源 ID/数据库/表；不能按表名猜测模型", datasourceId, db, namespace, name, List.of());
    }
    // The catalog's keyword may be broad: filter by every segment and demand exactly one.
    List<CatalogTable> all = catalog.listTables(datasourceId, db, namespace, name);
    List<CatalogTable> physical = all.stream()
        .filter(t -> Objects.equals(db, clean(t.database()))
            && Objects.equals(namespace, clean(t.schema()))
            && Objects.equals(name, clean(t.name())))
        .toList();
    if (physical.size() != 1) {
      return new Lookup("UNVERIFIED",
          physical.isEmpty() ? "来源表不可核验；目录查询未返回精确物理表" : "物理表身份不唯一；不执行来源导入",
          datasourceId, db, namespace, name, List.of());
    }

    List<Model> sourceModels = models.listBySource(datasourceId, name).stream()
        .filter(m -> "ODS".equalsIgnoreCase(m.layerCode())
            && Objects.equals(clean(m.sourceDatabase()), db))
        .toList();
    // A pre-S1 model without schema provenance cannot be confidently assigned to a named schema.
    if (namespace != null && sourceModels.stream().anyMatch(m -> clean(m.sourceSchema()) == null)) {
      return new Lookup("UNVERIFIED", "存在旧 ODS 未保存 Schema 来源；需人工补证，禁止自动匹配或新建覆盖",
          datasourceId, db, namespace, name, List.of());
    }
    List<Candidate> exact = sourceModels.stream()
        .filter(m -> Objects.equals(clean(m.sourceSchema()), namespace))
        .map(m -> new Candidate(m.id(), m.code(), m.name(), m.status().name(),
            m.sourceDatabase(), m.sourceSchema(), m.sourceTable()))
        .toList();
    return new Lookup("READY", exact.isEmpty() ? "已核验来源，当前项目无精确 ODS"
        : "当前项目已找到精确来源 ODS；优先复用，不修改已有结构",
        datasourceId, db, namespace, name, exact);
  }
}
