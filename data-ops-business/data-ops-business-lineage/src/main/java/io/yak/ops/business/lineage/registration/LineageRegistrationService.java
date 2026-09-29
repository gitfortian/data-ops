package io.yak.ops.business.lineage.registration;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable registration facade; registrars own asset/relation implementation details. */
@Service
public class LineageRegistrationService {

  private final LineageAssetRegistrar assetRegistrar;
  private final LineageRelationRegistrar relationRegistrar;

  public LineageRegistrationService(
      LineageAssetRegistrar assetRegistrar, LineageRelationRegistrar relationRegistrar) {
    this.assetRegistrar = assetRegistrar;
    this.relationRegistrar = relationRegistrar;
  }

  @Transactional("yakBusinessTransactionManager")
  public LineageAsset registerAsset(RegisterAssetCommand command) {
    return assetRegistrar.register(command);
  }

  @Transactional("yakBusinessTransactionManager")
  public LineageRelation registerRelation(RegisterRelationCommand command) {
    return relationRegistrar.register(command);
  }

  @Transactional("yakBusinessTransactionManager")
  public Map<String, LineageAsset> registerAssetsBatch(
      List<RegisterAssetCommand> commands, int batchSize) {
    return assetRegistrar.registerBatch(commands, batchSize);
  }

  @Transactional("yakBusinessTransactionManager")
  public void registerRelationsBatch(
      List<RegisterRelationCommand> commands, int batchSize) {
    relationRegistrar.registerBatch(commands, batchSize);
  }

  public record RegisterAssetCommand(
      String assetKey,
      LineageAssetType assetType,
      String name,
      String sourceType,
      String sourceId,
      Long parentAssetId,
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName,
      String columnName,
      JsonNode properties,
      Long sourceProjectId) {

    /** Compatibility constructor; HTTP and pre-Stage-4 producers do not provide project identity. */
    public RegisterAssetCommand(
        String assetKey,
        LineageAssetType assetType,
        String name,
        String sourceType,
        String sourceId,
        Long parentAssetId,
        String dataSourceId,
        String databaseName,
        String schemaName,
        String tableName,
        String columnName,
        JsonNode properties) {
      this(
          assetKey,
          assetType,
          name,
          sourceType,
          sourceId,
          parentAssetId,
          dataSourceId,
          databaseName,
          schemaName,
          tableName,
          columnName,
          properties,
          null);
    }
  }

  public record RegisterRelationCommand(
      long sourceAssetId,
      long targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      JsonNode properties,
      Long sourceProjectId) {

    /** Compatibility constructor for existing producers; project will be derived from endpoints/context. */
    public RegisterRelationCommand(
        long sourceAssetId,
        long targetAssetId,
        LineageRelationType relationType,
        String sourceType,
        String sourceId,
        String expression,
        BigDecimal confidence,
        String version,
        Instant observedAt,
        JsonNode properties) {
      this(
          sourceAssetId,
          targetAssetId,
          relationType,
          sourceType,
          sourceId,
          expression,
          confidence,
          version,
          observedAt,
          properties,
          null);
    }
  }
}
