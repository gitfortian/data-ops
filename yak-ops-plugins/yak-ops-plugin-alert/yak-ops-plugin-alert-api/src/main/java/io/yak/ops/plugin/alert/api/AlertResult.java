package io.yak.ops.plugin.alert.api;

/**
 * Result of an alert send operation.
 *
 * @param success whether the alert was delivered successfully
 * @param errorMessage error description when {@code success} is {@code false}; {@code null} otherwise
 */
public record AlertResult(boolean success, String errorMessage) {

  private static final AlertResult OK = new AlertResult(true, null);

  /** Successful result singleton. */
  public static AlertResult ok() {
    return OK;
  }

  /** Failed result with an error message. */
  public static AlertResult fail(String errorMessage) {
    return new AlertResult(false, errorMessage);
  }
}
