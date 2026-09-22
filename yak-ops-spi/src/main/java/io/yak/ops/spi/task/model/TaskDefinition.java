package io.yak.ops.spi.task.model;

/**
 * Plugin-neutral immutable task payload.
 *
 * @param taskType stable task type, for example SQL or SHELL
 * @param schemaVersion version of the plugin-owned configuration schema
 * @param content primary task content, for example SQL or script text
 * @param configJson plugin-owned configuration serialized as JSON
 */
public record TaskDefinition(
    String taskType,
    int schemaVersion,
    String content,
    String configJson) {
}
