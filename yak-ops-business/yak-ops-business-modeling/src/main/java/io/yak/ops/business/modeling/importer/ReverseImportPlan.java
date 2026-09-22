package io.yak.ops.business.modeling.importer;

import io.yak.ops.business.modeling.api.ModelingStructureApi;
import java.util.List;

/**
 * Resolved write plan for one reverse-imported table: everything the writer
 * needs, so reading metadata, standard matching and recommendation all happen
 * outside the transaction and the write stays short and atomic.
 *
 * <p>{@code tableName} is the physical table name (source table, or the name an
 * existing model already carries); {@code layerCode} is null when no layer
 * should be written; the {@code source*} triple is null when the model's source
 * binding must not be touched (nil on legacy rows or already set).
 */
public record ReverseImportPlan(
    String name,
    String code,
    String dialect,
    String description,
    Long directoryId,
    String tableName,
    String layerCode,
    String partitionExpr,
    Long sourceDatasourceId,
    String sourceDatabase,
    String sourceTable,
    List<ModelingStructureApi.ColumnInput> columns,
    List<String> primaryKey) {}
