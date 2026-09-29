package io.yak.ops.business.development.service;

/** Optimistic authoring conflict retained as a compatibility corridor for Task and Data Service. */
public class DevelopmentDraftConflictException extends RuntimeException {

  public DevelopmentDraftConflictException(String message) {
    super(message);
  }
}
