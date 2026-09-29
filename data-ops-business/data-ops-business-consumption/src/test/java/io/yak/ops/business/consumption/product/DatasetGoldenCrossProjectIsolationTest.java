package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryResult;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import io.yak.ops.business.consumption.product.provider.source.DatasetDataProductProvider;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.repository.DatasetRepositoryAdapter;
import io.yak.ops.business.dataset.repository.support.DatasetJsonCodec;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path A: cross-project Dataset requests stay outside Consumption. */
class DatasetGoldenCrossProjectIsolationTest {

  @Test
  void foreignProjectDatasetIsNeitherDiscoverableNorResolvableByStableProductKey() {
    DatasetDao datasetDao = mock(DatasetDao.class);
    DatasetJsonCodec jsonCodec = mock(DatasetJsonCodec.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    CurrentProject currentProject = () -> Optional.of(new ProjectContext(8L, "Project B"));
    DatasetReader reader = new DatasetReader(
        new DatasetRepositoryAdapter(datasetDao, jsonCodec, currentProject));

    // Dataset 42 belongs to another project. The repository boundary queries only Project B,
    // therefore the foreign object is absent for both discovery and direct stable-ID lookup.
    when(datasetDao.selectDatasets(8L)).thenReturn(List.of());
    when(datasetDao.selectDatasetsByIds(8L, List.of(42L))).thenReturn(List.of());

    DatasetDataProductProvider provider = new DatasetDataProductProvider(reader, assetLookup);
    ProductDiscoveryService discoveryService =
        new ProductDiscoveryService(new DataProductRegistry(List.of(provider)));
    CanonicalProductService canonicalService =
        new CanonicalProductService(discoveryService, assetAppService);

    ProductDiscoveryResult discovery = discoveryService.search(new ProductSearchCriteria(
        ProductType.DATASET,
        null,
        null,
        null,
        null,
        null,
        null));

    assertEquals(ProductSearchState.READY, discovery.providerStates().get(ProductType.DATASET));
    assertEquals(0L, discovery.total());
    assertEquals(List.of(), discovery.products());

    ProductKey foreignKey = new ProductKey(ProductType.DATASET, "42");
    CanonicalProductDetail detail = canonicalService.detail(foreignKey);

    assertEquals(ProductLookupState.NOT_FOUND, detail.state());
    assertNull(detail.product());
    assertNull(detail.navigation());
    assertEquals(List.of(), detail.governanceEvidence());

    verify(datasetDao).selectDatasets(8L);
    verify(datasetDao).selectDatasetsByIds(8L, List.of(42L));
  }
}
