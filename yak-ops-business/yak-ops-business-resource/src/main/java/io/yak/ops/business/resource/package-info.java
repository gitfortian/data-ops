/**
 * Resource namespace, current revision metadata, content orchestration, storage routing, runtime
 * resolution and post-commit change propagation.
 *
 * <p>The database owns Resource identity/namespace/current revision metadata. Physical bytes are
 * owned by StorageOperator implementations and reached by Resource business roles only through the
 * ResourceStorageGateway boundary.
 */
package io.yak.ops.business.resource;
