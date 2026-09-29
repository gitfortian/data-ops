package io.yak.ops.business.dataset.query;

/** Dataset-owned rejection raised when its source violates the execution policy. */
public final class DatasetQueryRejectedException extends RuntimeException {

  public DatasetQueryRejectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
