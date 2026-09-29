package io.yak.ops.business.sync.realtime.domain;

/** Stable failure contract for operations delegated to an external realtime runtime. */
public class RealtimeOperationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final boolean uncertain;
  private final Integer httpStatus;

  public RealtimeOperationException(
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
