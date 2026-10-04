package io.yak.ops.business.dataservice.domain;

/** Stable source-owned key shared by catalog and future lineage projections. */
public final class DataServiceIdentity {
  private DataServiceIdentity() {}

  public static String assetKey(long serviceId) {
    if (serviceId <= 0L) throw new IllegalArgumentException("Data Service identity must be positive");
    return "data_service:" + serviceId;
  }
}
