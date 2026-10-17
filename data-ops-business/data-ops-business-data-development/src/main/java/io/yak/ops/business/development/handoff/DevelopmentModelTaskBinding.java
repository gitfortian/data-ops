package io.yak.ops.business.development.handoff;

/** DataDev-owned immutable association; no execution or target-data receipt is implied. */
public record DevelopmentModelTaskBinding(
    Long id,
    long projectId,
    long modelId,
    long modelVersionId,
    int modelVersionNo,
    long mappingRevisionId,
    String mappingSha256,
    String logicalSha256,
    String physicalSha256,
    String currentStandardSha256,
    long nodeId,
    long taskRevisionId,
    int taskRevisionNo,
    String taskDefinitionSha256,
    long targetDatasourceId,
    String targetDatabase,
    String targetSchema,
    String targetTable,
    String writeMode,
    String bindingDigest,
    String idempotencyKey,
    String state) {}
