package io.yak.ops.business.mdm.domain.distribution;

/** 分发方式:API(复用 data-service)/MESSAGE(参照 alert)/FILE(参照 storage)。 */
public enum MdmDistributionMode {
  API,
  MESSAGE,
  FILE
}
