package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.Dataset;
import java.time.Instant;
import java.util.List;

/** Persistence port for bounded Dataset overview reads. */
public interface DatasetOverviewRepository {

  Summary summarize(Instant from, Instant to);

  List<Dataset> listRecent(int limit);

  List<Dataset> listRecentOnline(int limit);

  record Summary(long datasetCount, long createdCount) {}
}
