package io.yak.ops.business.lineage.dao.mapper;

import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Complex and atomic lineage writes.
 *
 * <p>原 XML 已移除。这批语句都需要数据库原子语义或跨表关联——MySQL {@code ON DUPLICATE KEY UPDATE}
 * upsert、带 JOIN 的搬行 UPDATE、{@code FOR UPDATE} 行锁、批量 {@code foreach} 写入、
 * 带存在性守卫的删除——Wrapper 无法表达，SQL 逐字保留在注解里。
 *
 * <p>共表 {@code yak_metadata_asset} 的列归属契约（ticket 133）由 SQL 形状本身承载：
 * {@link #upsertAsset} / {@link #upsertAssets} 的 UPDATE 子句里只有 lineage owns 的列，
 * 不得把目录列（{@code md_attributes} / {@code entity_status} / {@code fqn_hash} …）写进去。
 */
@Mapper
public interface LineageWriteMapper {

  /** 搬行：把 legacy（project_id IS NULL）行收进当前 project；目标 project 已有同 key 行时不生效（撞唯一键由上层识别）。 */
  @Update(
      """
      UPDATE yak_metadata_asset legacy
      LEFT JOIN yak_metadata_asset scoped
        ON scoped.asset_key = legacy.asset_key
       AND scoped.project_id = #{projectId}
      SET legacy.project_id = #{projectId}, legacy.update_time = NOW(6)
      WHERE legacy.asset_key = #{assetKey}
        AND legacy.project_id IS NULL
        AND scoped.id IS NULL
      """)
  @Update(databaseId = "postgresql", value = """
      UPDATE yak_metadata_asset AS legacy
      SET project_id = #{projectId}, update_time = CURRENT_TIMESTAMP(6)
      WHERE legacy.asset_key = #{assetKey} AND legacy.project_id IS NULL
        AND NOT EXISTS (SELECT 1 FROM yak_metadata_asset scoped
          WHERE scoped.asset_key = legacy.asset_key AND scoped.project_id = #{projectId})
      """)
  int claimLegacyAssetProject(
      @Param("assetKey") String assetKey,
      @Param("projectId") Long projectId);

  @Insert(
      """
      INSERT INTO yak_metadata_asset
          (project_id, asset_key, asset_type, name, source_type, source_id, parent_asset_id,
           data_source_id, database_name, schema_name, table_name, column_name, properties,
           create_time, update_time)
      VALUES
          (#{row.projectId}, #{row.assetKey}, #{row.assetType}, #{row.name}, #{row.sourceType}, #{row.sourceId},
           #{row.parentAssetId}, #{row.dataSourceId}, #{row.databaseName}, #{row.schemaName},
           #{row.tableName}, #{row.columnName}, #{row.properties}, NOW(6), NOW(6))
      ON DUPLICATE KEY UPDATE
          project_id = VALUES(project_id), asset_type = VALUES(asset_type), name = VALUES(name),
          source_type = VALUES(source_type), source_id = VALUES(source_id),
          parent_asset_id = VALUES(parent_asset_id), data_source_id = VALUES(data_source_id),
          database_name = VALUES(database_name), schema_name = VALUES(schema_name),
          table_name = VALUES(table_name), column_name = VALUES(column_name),
          properties = VALUES(properties), update_time = NOW(6)
      """)
  @Insert(databaseId = "postgresql", value = """
      INSERT INTO yak_metadata_asset
          (project_id, asset_key, asset_type, name, source_type, source_id, parent_asset_id,
           data_source_id, database_name, schema_name, table_name, column_name, properties,
           create_time, update_time)
      VALUES
          (#{row.projectId}, #{row.assetKey}, #{row.assetType}, #{row.name}, #{row.sourceType}, #{row.sourceId},
           #{row.parentAssetId}, #{row.dataSourceId}, #{row.databaseName}, #{row.schemaName},
           #{row.tableName}, #{row.columnName}, #{row.properties}, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
      ON CONFLICT (project_scope_id, asset_key) DO UPDATE SET
        project_id = excluded.project_id,
        asset_type = excluded.asset_type,
        name = excluded.name,
        source_type = excluded.source_type,
        source_id = excluded.source_id,
        parent_asset_id = excluded.parent_asset_id,
        data_source_id = excluded.data_source_id,
        database_name = excluded.database_name,
        schema_name = excluded.schema_name,
        table_name = excluded.table_name,
        column_name = excluded.column_name,
        properties = excluded.properties,
        update_time = CURRENT_TIMESTAMP
      """)
  int upsertAsset(@Param("row") LineageAssetPO row);

  @Insert(
      """
      INSERT INTO yak_metadata_relation
          (project_id, source_asset_id, target_asset_id, relation_type, source_type, source_id,
           expression, confidence, version, observed_at, properties, create_time, update_time)
      VALUES
          (#{row.projectId}, #{row.sourceAssetId}, #{row.targetAssetId}, #{row.relationType}, #{row.sourceType},
           #{row.sourceId}, #{row.expression}, #{row.confidence}, #{row.version},
           #{row.observedAt}, #{row.properties}, NOW(6), NOW(6))
      ON DUPLICATE KEY UPDATE
          project_id = VALUES(project_id), expression = VALUES(expression), confidence = VALUES(confidence),
          observed_at = VALUES(observed_at), properties = VALUES(properties), update_time = NOW(6)
      """)
  @Insert(databaseId = "postgresql", value = """
      INSERT INTO yak_metadata_relation
          (project_id, source_asset_id, target_asset_id, relation_type, source_type, source_id,
           expression, confidence, version, observed_at, properties, create_time, update_time)
      VALUES
          (#{row.projectId}, #{row.sourceAssetId}, #{row.targetAssetId}, #{row.relationType}, #{row.sourceType},
           #{row.sourceId}, #{row.expression}, #{row.confidence}, #{row.version},
           #{row.observedAt}, #{row.properties}, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
      ON CONFLICT (source_asset_id, target_asset_id, relation_type, source_type, source_id, version) DO UPDATE SET
        project_id = excluded.project_id,
        expression = excluded.expression,
        confidence = excluded.confidence,
        observed_at = excluded.observed_at,
        properties = excluded.properties,
        update_time = CURRENT_TIMESTAMP
      """)
  int upsertRelation(@Param("row") LineageRelationPO row);

  @Insert(
      """
      <script>
      INSERT INTO yak_metadata_asset
          (project_id, asset_key, asset_type, name, source_type, source_id, parent_asset_id,
           data_source_id, database_name, schema_name, table_name, column_name, properties,
           create_time, update_time)
      VALUES
      <foreach collection="rows" item="row" separator=",">
          (#{row.projectId}, #{row.assetKey}, #{row.assetType}, #{row.name}, #{row.sourceType}, #{row.sourceId},
           #{row.parentAssetId}, #{row.dataSourceId}, #{row.databaseName}, #{row.schemaName},
           #{row.tableName}, #{row.columnName}, #{row.properties}, NOW(6), NOW(6))
      </foreach>
      ON DUPLICATE KEY UPDATE
          project_id = VALUES(project_id), asset_type = VALUES(asset_type), name = VALUES(name),
          source_type = VALUES(source_type), source_id = VALUES(source_id),
          parent_asset_id = VALUES(parent_asset_id), data_source_id = VALUES(data_source_id),
          database_name = VALUES(database_name), schema_name = VALUES(schema_name),
          table_name = VALUES(table_name), column_name = VALUES(column_name),
          properties = VALUES(properties), update_time = NOW(6)
      </script>
      """)
  @Insert(databaseId = "postgresql", value = """
      <script>
      INSERT INTO yak_metadata_asset
          (project_id, asset_key, asset_type, name, source_type, source_id, parent_asset_id,
           data_source_id, database_name, schema_name, table_name, column_name, properties,
           create_time, update_time)
      VALUES
      <foreach collection="rows" item="row" separator=",">
          (#{row.projectId}, #{row.assetKey}, #{row.assetType}, #{row.name}, #{row.sourceType}, #{row.sourceId},
           #{row.parentAssetId}, #{row.dataSourceId}, #{row.databaseName}, #{row.schemaName},
           #{row.tableName}, #{row.columnName}, #{row.properties}, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
      </foreach>
      ON CONFLICT (project_scope_id, asset_key) DO UPDATE SET
        project_id = excluded.project_id,
        asset_type = excluded.asset_type,
        name = excluded.name,
        source_type = excluded.source_type,
        source_id = excluded.source_id,
        parent_asset_id = excluded.parent_asset_id,
        data_source_id = excluded.data_source_id,
        database_name = excluded.database_name,
        schema_name = excluded.schema_name,
        table_name = excluded.table_name,
        column_name = excluded.column_name,
        properties = excluded.properties,
        update_time = CURRENT_TIMESTAMP
      </script>
      """)
  int upsertAssets(@Param("rows") List<LineageAssetPO> rows);

  @Insert(
      """
      <script>
      INSERT INTO yak_metadata_relation
          (project_id, source_asset_id, target_asset_id, relation_type, source_type, source_id,
           expression, confidence, version, observed_at, properties, create_time, update_time)
      VALUES
      <foreach collection="rows" item="row" separator=",">
          (#{row.projectId}, #{row.sourceAssetId}, #{row.targetAssetId}, #{row.relationType}, #{row.sourceType},
           #{row.sourceId}, #{row.expression}, #{row.confidence}, #{row.version},
           #{row.observedAt}, #{row.properties}, NOW(6), NOW(6))
      </foreach>
      ON DUPLICATE KEY UPDATE
          project_id = VALUES(project_id), expression = VALUES(expression), confidence = VALUES(confidence),
          observed_at = VALUES(observed_at), properties = VALUES(properties), update_time = NOW(6)
      </script>
      """)
  @Insert(databaseId = "postgresql", value = """
      <script>
      INSERT INTO yak_metadata_relation
          (project_id, source_asset_id, target_asset_id, relation_type, source_type, source_id,
           expression, confidence, version, observed_at, properties, create_time, update_time)
      VALUES
      <foreach collection="rows" item="row" separator=",">
          (#{row.projectId}, #{row.sourceAssetId}, #{row.targetAssetId}, #{row.relationType}, #{row.sourceType},
           #{row.sourceId}, #{row.expression}, #{row.confidence}, #{row.version},
           #{row.observedAt}, #{row.properties}, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
      </foreach>
      ON CONFLICT (source_asset_id, target_asset_id, relation_type, source_type, source_id, version) DO UPDATE SET
        project_id = excluded.project_id,
        expression = excluded.expression,
        confidence = excluded.confidence,
        observed_at = excluded.observed_at,
        properties = excluded.properties,
        update_time = CURRENT_TIMESTAMP
      </script>
      """)
  int upsertRelations(@Param("rows") List<LineageRelationPO> rows);

  /**
   * 行锁读回。{@code projectId == null} 时不加 project 谓词、按 {@code asset_key LIMIT 1}
   * 命中任意项目的行——这是既有契约（见 docs/data-metadata/issues/119），改动前须先评审。
   */
  @Select(
      """
      <script>
      SELECT id, project_id, asset_key, asset_type, name, source_type, source_id, parent_asset_id,
             data_source_id, database_name, schema_name, table_name, column_name, properties,
             create_time, update_time
      FROM yak_metadata_asset
      WHERE asset_key = #{assetKey}
      <if test="projectId != null">
          AND project_id = #{projectId}
      </if>
      LIMIT 1
      FOR UPDATE
      </script>
      """)
  LineageAssetPO selectAssetForUpdate(
      @Param("assetKey") String assetKey,
      @Param("projectId") Long projectId);

  @Delete(
      """
      <script>
      DELETE asset
      FROM yak_metadata_asset asset
      LEFT JOIN yak_metadata_relation outgoing ON outgoing.source_asset_id = asset.id
      LEFT JOIN yak_metadata_relation incoming ON incoming.target_asset_id = asset.id
      LEFT JOIN yak_metadata_asset child ON child.parent_asset_id = asset.id
      WHERE asset.id IN
      <foreach collection="assetIds" item="assetId" open="(" separator="," close=")">
          #{assetId}
      </foreach>
        AND asset.source_type = #{ownerType}
        AND asset.source_id = #{ownerId}
      <if test="projectId != null">
        AND asset.project_id = #{projectId}
      </if>
        AND outgoing.id IS NULL
        AND incoming.id IS NULL
        AND child.id IS NULL
      </script>
      """)
  @Delete(databaseId = "postgresql", value = """
      <script>
      DELETE FROM yak_metadata_asset AS asset
      WHERE asset.id IN
      <foreach collection="assetIds" item="assetId" open="(" separator="," close=")">#{assetId}</foreach>
        AND asset.source_type = #{ownerType} AND asset.source_id = #{ownerId}
      <if test="projectId != null">AND asset.project_id = #{projectId}</if>
        AND NOT EXISTS (SELECT 1 FROM yak_metadata_relation outgoing WHERE outgoing.source_asset_id = asset.id)
        AND NOT EXISTS (SELECT 1 FROM yak_metadata_relation incoming WHERE incoming.target_asset_id = asset.id)
        AND NOT EXISTS (SELECT 1 FROM yak_metadata_asset child WHERE child.parent_asset_id = asset.id)
      </script>
      """)
  int deleteUnreferencedOwnedAssets(
      @Param("assetIds") Set<Long> assetIds,
      @Param("ownerType") String ownerType,
      @Param("ownerId") String ownerId,
      @Param("projectId") Long projectId);
}
