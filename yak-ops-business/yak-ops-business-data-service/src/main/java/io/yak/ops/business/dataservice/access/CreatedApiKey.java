package io.yak.ops.business.dataservice.access;

/** Secret is intentionally returned only by create/rotate endpoints and is never persisted. */
public record CreatedApiKey(ApiKeyView key, String secret) {}
