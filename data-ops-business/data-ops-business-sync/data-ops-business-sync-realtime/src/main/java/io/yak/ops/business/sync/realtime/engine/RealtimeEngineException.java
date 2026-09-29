package io.yak.ops.business.sync.realtime.engine;

/** Failure returned by the local Flink CDC submitter or the Flink REST API. */
public final class RealtimeEngineException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final boolean uncertain;
  private final Integer httpStatus;

  public RealtimeEngineException(
      String message, boolean uncertain, Integer httpStatus, Throwable cause) {
    super(message, cause);
    this.uncertain = uncertain;
    this.httpStatus = httpStatus;
  }

  public boolean uncertain() {
    return uncertain;
  }

  public Integer httpStatus() {
    return httpStatus;
  }
}
