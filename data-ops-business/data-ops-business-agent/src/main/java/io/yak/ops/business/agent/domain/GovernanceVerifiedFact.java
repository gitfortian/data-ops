package io.yak.ops.business.agent.domain;

/** Value copied from an allowed scalar in current invocation evidence, never model supplied. */
public record GovernanceVerifiedFact(String evidenceRef, String field, String value) {}
