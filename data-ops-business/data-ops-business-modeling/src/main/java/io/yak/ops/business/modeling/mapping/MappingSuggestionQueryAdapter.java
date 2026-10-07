package io.yak.ops.business.modeling.mapping;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.modeling.api.MappingSuggestionQueryApi;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.Comparator;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MappingSuggestionQueryAdapter implements MappingSuggestionQueryApi {
  private final MappingService mappings;
  private final DataSourceCatalogReader catalog;
  private final ActionAuthorization authorization;

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Context require(long modelId, String targetColumn, long datasourceId, String database, String table, String keyword) {
    authorization.requirePermission(ModelingPermissionCode.READ);
    authorization.requirePermission(DataSourcePermissionCode.READ);
    if (datasourceId <= 0 || database == null || database.isBlank() || database.length() > 128
        || table == null || table.isBlank() || table.length() > 128
        || keyword == null || keyword.length() > 64) throw new IllegalArgumentException("来源表或检索词无效");
    var edit = mappings.editContext(modelId, targetColumn);
    var columns = catalog.listColumnsFresh(datasourceId, database, null, table);
    if (columns == null) throw new IllegalStateException("源目录暂不可用");
    String word = keyword.trim().toLowerCase(Locale.ROOT);
    var selected = columns.stream().filter(c -> word.isEmpty()
        || c.name().toLowerCase(Locale.ROOT).contains(word)
        || (c.remarks() != null && c.remarks().toLowerCase(Locale.ROOT).contains(word)))
        .sorted(Comparator.comparing(io.yak.ops.business.datasource.domain.catalog.CatalogColumn::name))
        .limit(51).toList();
    var bounded = selected.stream().limit(50).map(c -> {
      if (c.name().length() > 128 || (c.typeName() != null && c.typeName().length() > 128)) {
        throw new IllegalStateException("源字段标识或类型超出场景支持范围");
      }
      return new SourceColumn(c.name(), c.typeName(), text(c.remarks()), c.nullable());
    }).toList();
    return new Context(edit.modelName(), edit.dialect(), edit.column().columnName(), edit.column().dataType(),
        text(edit.column().businessDescription() == null ? edit.column().comment() : edit.column().businessDescription()),
        edit.definition(), MappingContextFingerprint.source(columns), bounded, selected.size() > 50);
  }
  private static String text(String value) { return value == null ? "" : value.substring(0, Math.min(512, value.length())); }
}
