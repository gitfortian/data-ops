package io.yak.ops.business.metadata.harvest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.dao.mapper.MdFieldDefMapper;
import io.yak.ops.business.metadata.dao.mapper.MdTypeDefMapper;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.common.bean.po.metadata.MdCollectJobPO;
import io.yak.ops.common.bean.po.metadata.MdFieldDefPO;
import io.yak.ops.common.bean.po.metadata.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeCategory;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集侧单测夹具：类型与字段定义<b>逐字照抄 V2 迁移的 seed 行</b>。
 *
 * <p>为什么照抄而不是随手编一个：采集写出的属性袋键名、槽名、FQN 拼法全靠这两张表驱动。
 * 夹具里写 {@code "db_name"} 而迁移里是 {@code databaseName/s_str_2}，测试全绿、现网全错。
 * 迁移改列时这里的测试会跟着红——那正是想要的耦合。
 */
final class HarvestFixtures {

  static final long DATA_SOURCE_ID = 7L;
  static final long PROJECT_ID = 42L;
  static final long JOB_ID = 9L;

  static final long TYPE_SERVICE = 101L;
  static final long TYPE_DATABASE = 102L;
  static final long TYPE_TABLE = 103L;
  static final long TYPE_COLUMN = 104L;

  private HarvestFixtures() {}

  /** 一期四类可采集实体，字段与槽位分配对齐 V2 seed（{@code s_str_1}=dataType、{@code s_str_2}=databaseName…）。 */
  static MetadataTypeRegistry harvestTypeRegistry() {
    List<MdTypeDefPO> types =
        List.of(
            entityType(
                TYPE_SERVICE,
                "databaseService",
                "datasource:",
                "{dataSourceName}",
                "DATABASE_SERVICE"),
            entityType(
                TYPE_DATABASE, "database", "database:", "{dataSourceName}.{databaseName}", "DATABASE"),
            entityType(
                TYPE_TABLE,
                "table",
                "table:",
                "{databaseName}.{schemaName}.{tableName}",
                "TABLE"),
            entityType(
                TYPE_COLUMN,
                "tableColumn",
                "column:",
                "{databaseName}.{schemaName}.{tableName}.{columnName}",
                "COLUMN"));
    List<MdFieldDefPO> fields = new ArrayList<>();
    fields.add(field(TYPE_SERVICE, "dataSourceName", "STRING", null, 10));
    fields.add(field(TYPE_DATABASE, "databaseName", "STRING", null, 10));
    fields.add(field(TYPE_DATABASE, "characterSet", "STRING", null, 20));
    fields.add(field(TYPE_TABLE, "databaseName", "STRING", "s_str_2", 10));
    fields.add(field(TYPE_TABLE, "schemaName", "STRING", null, 20));
    fields.add(field(TYPE_TABLE, "tableName", "STRING", null, 30));
    fields.add(field(TYPE_TABLE, "tableType", "STRING", null, 40));
    fields.add(field(TYPE_TABLE, "tableComment", "STRING", null, 50));
    fields.add(field(TYPE_TABLE, "columnCount", "INTEGER", "s_num_1", 60));
    fields.add(field(TYPE_TABLE, "rowCountApprox", "INTEGER", "s_num_2", 70));
    fields.add(field(TYPE_TABLE, "partitioned", "BOOLEAN", "s_bool_1", 80));
    fields.add(field(TYPE_TABLE, "lastDdlTime", "DATETIME", "s_date_1", 90));
    fields.add(field(TYPE_COLUMN, "columnName", "STRING", null, 10));
    fields.add(field(TYPE_COLUMN, "dataType", "STRING", "s_str_1", 20));
    fields.add(field(TYPE_COLUMN, "columnSize", "INTEGER", null, 30));
    fields.add(field(TYPE_COLUMN, "decimalDigits", "INTEGER", null, 40));
    fields.add(field(TYPE_COLUMN, "nullable", "BOOLEAN", null, 50));
    fields.add(field(TYPE_COLUMN, "ordinalPosition", "INTEGER", null, 60));
    fields.add(field(TYPE_COLUMN, "primaryKey", "BOOLEAN", null, 70));
    fields.add(field(TYPE_COLUMN, "columnComment", "STRING", null, 80));

    MdTypeDefMapper typeMapper = mock(MdTypeDefMapper.class);
    MdFieldDefMapper fieldMapper = mock(MdFieldDefMapper.class);
    when(typeMapper.selectList(any())).thenReturn(types);
    when(fieldMapper.selectList(any())).thenReturn(fields);
    // ttl=0：每次 require 都重读 stub，测试里不需要关心缓存失效。
    return new MetadataTypeRegistry(typeMapper, fieldMapper, 0L);
  }

  static MetadataAttributeCodec codec() {
    return new MetadataAttributeCodec(new ObjectMapper());
  }

  static MdTypeDefPO entityType(
      long id, String typeName, String keyPrefix, String fqnPattern, String lineageAssetType) {
    MdTypeDefPO type = new MdTypeDefPO();
    type.setId(id);
    type.setTypeName(typeName);
    type.setCategory(TypeCategory.ENTITY.name());
    type.setNameSpace("platform");
    type.setDisplayName(typeName);
    type.setKeyPrefix(keyPrefix);
    type.setFqnPattern(fqnPattern);
    type.setKeySeparator(".");
    type.setLineageAssetType(lineageAssetType);
    type.setCollectible(true);
    type.setSearchDefaultWeight(1.0f);
    type.setSearchIncludeByDefault(true);
    type.setStatus(TypeStatus.ACTIVE.name());
    type.setVersion(1);
    return type;
  }

  static MdFieldDefPO field(long typeId, String fieldName, String baseType, String slot, int ordinal) {
    MdFieldDefPO field = new MdFieldDefPO();
    field.setId(typeId * 1000 + ordinal);
    field.setTypeId(typeId);
    field.setFieldName(fieldName);
    field.setFieldType(baseType);
    field.setDisplayName(fieldName);
    field.setBaseType(baseType);
    field.setMatchType("exact");
    field.setBoost(1.0f);
    field.setRequired(false);
    field.setIsNull(true);
    field.setSearchable(false);
    field.setFacetable(false);
    field.setShowInList(false);
    field.setDeprecated(false);
    field.setStorageSlot(slot);
    field.setOrdinal(ordinal);
    return field;
  }

  static MdCollectJobPO harvestJob() {
    MdCollectJobPO job = new MdCollectJobPO();
    job.setId(JOB_ID);
    job.setProjectId(PROJECT_ID);
    job.setJobCode("harvest-shop");
    job.setProviderType("HARVESTED");
    job.setDataSourceId(DATA_SOURCE_ID);
    job.setCollectColumns(true);
    job.setEnabled(false);
    return job;
  }

  static DataSourceTable table(String database, String name, String type, String remarks) {
    return new DataSourceTable(database, null, name, type, remarks);
  }

  static DataSourceColumn column(
      String name,
      String typeName,
      int jdbcType,
      Integer size,
      Integer scale,
      boolean nullable,
      int ordinal,
      boolean primaryKey,
      String remarks) {
    return new DataSourceColumn(
        name, typeName, jdbcType, size, scale, nullable, ordinal, primaryKey, remarks);
  }

  static DataSourceColumn idColumn() {
    return column("id", "bigint", -5, 19, null, false, 1, true, "主键");
  }
}
