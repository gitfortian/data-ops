package io.yak.ops.business.dataservice.repository;

/** Shared persistence boundary for cluster-wide API Key fixed-window rate limiting. */
public interface DataServiceRateLimitRepository {

  /** Returns true when one request is admitted into the shared minute window. */
  boolean tryAcquire(Long apiKeyId, long windowMinute, int limitPerMinute);

  /** Removes all windows for one key after rotate/delete so stale counters cannot leak. */
  void deleteForKey(Long apiKeyId);

  /** Bounded maintenance hook for old fixed-window rows. */
  int deleteBefore(long windowMinute);
}
