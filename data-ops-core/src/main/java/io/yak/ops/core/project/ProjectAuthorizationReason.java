package io.yak.ops.core.project;

/** Stable internal reason codes for Project Space authorization decisions. */
public enum ProjectAuthorizationReason {
  PROJECT_REQUIRED,
  PROJECT_ID_INVALID,
  PROJECT_ACCESS_INPUT_INVALID,
  PROJECT_NOT_FOUND,
  PROJECT_ACTOR_NOT_FOUND,
  PROJECT_MEMBERSHIP_REQUIRED,
  PROJECT_UNAVAILABLE,
  PROJECT_OWNER_ACCESS_ALLOWED,
  PROJECT_MEMBER_ACCESS_ALLOWED
}
