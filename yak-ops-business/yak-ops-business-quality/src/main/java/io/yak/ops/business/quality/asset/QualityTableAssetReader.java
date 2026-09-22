package io.yak.ops.business.quality.asset;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.TableAsset;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to registered quality table assets. */
@Component
@ConditionalOnQualityEnabled
public class QualityTableAssetReader {
  private final QualityTableAssetRepository repository;

  public QualityTableAssetReader(QualityTableAssetRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<TableAsset> page(QualityQuery.TableAsset query) {
    if (query.dataSourceId() <= 0L) throw new IllegalArgumentException("数据源编号无效");
    return repository.pageTableAssets(query);
  }
}
