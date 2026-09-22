package io.yak.ops.business.job.task;

/**
 * Workflow-visible task descriptor.
 *
 * <p>The historical class name is kept for compatibility. This value describes a discoverable task;
 * the immutable executable truth is {@link TaskVersionSnapshot}.</p>
 */
public record TaskDefinition(
    String id,
    String name,
    String type) {}
