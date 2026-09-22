package io.yak.ops.business.dataset;

/** Terminal diagnostic status for one Dataset Query Runtime attempt. */
public enum DatasetQueryStatus {
  SUCCESS,
  REJECTED,
  FAILED,
  TIMEOUT
}
