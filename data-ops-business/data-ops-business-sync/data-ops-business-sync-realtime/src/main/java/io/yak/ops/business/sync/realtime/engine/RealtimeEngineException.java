package io.yak.ops.business.sync.realtime.engine;

import io.yak.ops.business.sync.realtime.domain.RealtimeOperationException;

/** Failure returned by the local Flink CDC submitter or the Flink REST API. */
public final class RealtimeEngineException extends RealtimeOperationException {

  private static final long serialVersionUID = 1L;

  public RealtimeEngineException(
      String message, boolean uncertain, Integer httpStatus, Throwable cause) {
    super(message, uncertain, httpStatus, cause);
  }
}
