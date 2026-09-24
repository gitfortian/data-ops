package io.yak.ops.business.consumption.product.provider.source;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceRef;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.AccessProjection;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.DatasetContractPayload;
import io.yak.ops.business.consumption.product.model.DatasetContractPayload.DatasetColumnContract;
import io.yak.ops.business.consumption.product.model.ProductSectionState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchResult;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Projects the Dataset owning contract into the governed Consumption contract without persisting a copy. */
@Component
@ConditionalOnDataSourceEnabled
public class DatasetDataProductProvider implements DataProductProvider {

  private final DatasetReader datasetReader;
  private final AssetSourceLookupService assetLookup;

  public DatasetDataProductProvider(DatasetReader datasetReader, AssetSourceLookupService assetLookup) {
    this.datasetReader = datasetReader;
    this.assetLookup = assetLookup;
  }

  @Override
  public ProductType productType() {
    return ProductType.DATASET;
  }

  @Override
  public ProductLookupResult get(ProductKey productKey) {
    if (productKey == null || productKey.productType() != ProductType.DATASET) {
      return ProductLookupResult.notFound();
    }
    Long id = parsePositiveId(productKey.sourceIdentity());
    if (id == null) return ProductLookupResult.notFound();
    try {
      List<DatasetCatalogEntry> entries = datasetReader.catalog(List.of(id), false);
      if (entries.isEmpty()) return ProductLookupResult.notFound();
      DatasetCatalogEntry entry = entries.get(0);
      if (!isPublished(entry)) return ProductLookupResult.notDiscoverable();
      return ProductLookupResult.found(project(entry));
    } catch (RuntimeException exception) {
      return ProductLookupResult.unavailable(exception.getMessage());
    }
  }

  @Override
  public ProductSearchResult search(ProductSearchCriteria criteria) {
    if (criteria != null && criteria.productType() != null
        && criteria.productType() != ProductType.DATASET) {
      return ProductSearchResult.ready(List.of(), 0);
    }
    if (requiresUnavailableGovernanceFilter(criteria)) {
      return ProductSearchResult.unavailable(
          "Dataset owner/visibility owning evidence is not exposed yet");
    }
    try {
      List<DataProductView> products = datasetReader.catalog(List.of(), true).stream()
          .filter(this::isPublished)
          .filter(entry -> matches(entry, criteria))
          .map(this::project)
          .toList();
      return ProductSearchResult.ready(products, products.size());
    } catch (RuntimeException exception) {
      return ProductSearchResult.unavailable(exception.getMessage());
    }
  }

  private boolean matches(DatasetCatalogEntry entry, ProductSearchCriteria criteria) {
    if (criteria == null) return true;
    Dataset dataset = entry.dataset();
    if (criteria.projectId() != null && !criteria.projectId().equals(dataset.projectId())) return false;
    if (criteria.lifecycle() != null && criteria.lifecycle() != SourceLifecycleState.PUBLISHED) return false;
    if (criteria.availability() != null && criteria.availability() != AvailabilityState.UNKNOWN) return false;
    String keyword = normalize(criteria.keyword());
    if (keyword == null) return true;
    return contains(dataset.name(), keyword) || contains(dataset.description(), keyword);
  }

  private DataProductView project(DatasetCatalogEntry entry) {
    Dataset dataset = entry.dataset();
    DatasetVersion version = entry.currentVersion();
    List<ProductSectionState> sections = List.of(
        unavailableSection("ownership", "DATASET", "Dataset owning contract does not expose owner"),
        unavailableSection("visibility", "SECURITY", "Dataset visibility policy is not exposed yet"),
        assetSection(dataset.id()));
    return new DataProductView(
        new ProductKey(ProductType.DATASET, String.valueOf(dataset.id())),
        new SourceRef(ProductType.DATASET, String.valueOf(dataset.id())),
        new DomainRef("TASK_ASSET", String.valueOf(version.sourceTaskAssetId())),
        assetRef(dataset.id()),
        dataset.name(),
        dataset.description(),
        null,
        dataset.requireProjectId(),
        null,
        new SourceVersionRef(String.valueOf(version.id()), "v" + version.versionNo()),
        SourceLifecycleState.PUBLISHED,
        AvailabilityState.UNKNOWN,
        AccessProjection.unavailable("Dataset action access is delivered by #103"),
        sections,
        new DatasetContractPayload(
            version.id(),
            version.versionNo(),
            entry.fields().stream().map(this::column).toList(),
            List.of("QUERY", "PREVIEW")));
  }

  private DatasetColumnContract column(DatasetField field) {
    return new DatasetColumnContract(
        field.fieldId(), field.physicalName(), field.displayName(),
        field.dataType() == null ? null : field.dataType().name(), field.nullable(), field.description(),
        field.defaultRole() == null ? null : field.defaultRole().name(), field.sortOrder());
  }

  private boolean isPublished(DatasetCatalogEntry entry) {
    return entry.dataset().status() == DatasetStatus.ONLINE && entry.currentVersion() != null;
  }

  private boolean requiresUnavailableGovernanceFilter(ProductSearchCriteria criteria) {
    return criteria != null && (normalize(criteria.owner()) != null || normalize(criteria.visibility()) != null);
  }

  private ProductSectionState assetSection(long datasetId) {
    try {
      AssetSourceLookupService.SourceLookup lookup = assetLookup.lookup("DATASET", String.valueOf(datasetId));
      ProviderEvidenceState state = "FOUND".equals(lookup.state())
          ? ProviderEvidenceState.READY : ProviderEvidenceState.EMPTY;
      return new ProductSectionState("asset", state, "ASSET", null,
          state == ProviderEvidenceState.EMPTY ? "Dataset is not indexed in Asset Registry" : null);
    } catch (RuntimeException exception) {
      return unavailableSection("asset", "ASSET", exception.getMessage());
    }
  }

  private DomainRef assetRef(long datasetId) {
    try {
      AssetSourceLookupService.SourceLookup lookup = assetLookup.lookup("DATASET", String.valueOf(datasetId));
      return "FOUND".equals(lookup.state()) && lookup.assetId() != null
          ? new DomainRef("ASSET", String.valueOf(lookup.assetId())) : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private ProductSectionState unavailableSection(String key, String ownerDomain, String reason) {
    return new ProductSectionState(key, ProviderEvidenceState.UNAVAILABLE, ownerDomain, null, reason);
  }

  private boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private Long parsePositiveId(String value) {
    try {
      long parsed = Long.parseLong(value);
      return parsed > 0 ? parsed : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
