package io.yak.ops.business.consumption.product.provider.source;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceRef;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.AccessDecision;
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
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Projects the Dataset owning contract into the governed Consumption contract without persisting a copy. */
@Component
@ConditionalOnDataSourceEnabled
public class DatasetDataProductProvider implements DataProductProvider {

  private final DatasetReader datasetReader;
  private final AssetSourceLookupService assetLookup;
  private final DatasetTaskCatalogGateway taskCatalog;
  private final ActionAuthorization actionAuthorization;

  @Autowired
  public DatasetDataProductProvider(
      DatasetReader datasetReader,
      AssetSourceLookupService assetLookup,
      DatasetTaskCatalogGateway taskCatalog,
      ActionAuthorization actionAuthorization) {
    this.datasetReader = datasetReader;
    this.assetLookup = assetLookup;
    this.taskCatalog = taskCatalog;
    this.actionAuthorization = actionAuthorization;
  }

  public DatasetDataProductProvider(
      DatasetReader datasetReader,
      AssetSourceLookupService assetLookup,
      DatasetTaskCatalogGateway taskCatalog) {
    this(datasetReader, assetLookup, taskCatalog, null);
  }

  /** Compatibility constructor for focused tests without the Task Catalog adapter. */
  public DatasetDataProductProvider(DatasetReader datasetReader, AssetSourceLookupService assetLookup) {
    this(datasetReader, assetLookup, null, null);
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
      return ProductLookupResult.found(project(entry, actionAccess()));
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
      AccessProjection access = actionAccess();
      List<DataProductView> products = datasetReader.catalog(List.of(), false).stream()
          .filter(this::isPublished)
          .filter(entry -> matches(entry, criteria))
          .map(entry -> project(entry, access))
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
    if (criteria.availability() != null && criteria.availability() != availability(dataset)) return false;
    String keyword = normalize(criteria.keyword());
    if (keyword == null) return true;
    return contains(dataset.name(), keyword) || contains(dataset.description(), keyword);
  }

  private DataProductView project(DatasetCatalogEntry entry, AccessProjection access) {
    Dataset dataset = entry.dataset();
    DatasetVersion version = entry.currentVersion();
    AssetProjection asset = assetProjection(dataset.id());
    ProducerProjection producer = producerProjection(version);
    List<ProductSectionState> sections = List.of(
        sourceGovernanceSection(dataset),
        unavailableSection("ownership", "DATASET", "Dataset owning contract does not expose owner"),
        unavailableSection("visibility", "SECURITY", "Dataset visibility policy is not exposed yet"),
        producer.section(),
        unavailableSection("quality", "QUALITY", "Dataset Quality section reader is not connected to Consumption"),
        unavailableSection("security", "SECURITY", "Dataset Security classification evidence is not connected to Consumption"),
        unavailableSection("lineage", "LINEAGE", "Dataset lineage evidence is not connected to Consumption"),
        asset.section());
    return new DataProductView(
        new ProductKey(ProductType.DATASET, String.valueOf(dataset.id())),
        new SourceRef(ProductType.DATASET, String.valueOf(dataset.id())),
        producer.ref(),
        asset.ref(),
        dataset.name(),
        dataset.description(),
        null,
        dataset.requireProjectId(),
        null,
        new SourceVersionRef(String.valueOf(version.id()), "v" + version.versionNo()),
        SourceLifecycleState.PUBLISHED,
        availability(dataset),
        access,
        sections,
        new DatasetContractPayload(
            version.id(),
            version.versionNo(),
            entry.fields().stream().map(this::column).toList(),
            List.of("QUERY", "PREVIEW")));
  }

  private ProductSectionState sourceGovernanceSection(Dataset dataset) {
    return unavailableSection(
        "source-governance",
        "DATASET",
        "Dataset source contract exposes update time but no governed evidence projection");
  }

  private DatasetColumnContract column(DatasetField field) {
    return new DatasetColumnContract(
        field.fieldId(), field.physicalName(), field.displayName(),
        field.dataType() == null ? null : field.dataType().name(), field.nullable(), field.description(),
        field.defaultRole() == null ? null : field.defaultRole().name(), field.sortOrder());
  }

  private boolean isPublished(DatasetCatalogEntry entry) {
    return entry.currentVersion() != null;
  }

  private AccessProjection actionAccess() {
    if (actionAuthorization == null) {
      return AccessProjection.unavailable(
          "Dataset action access provider is not available",
          "Current authenticated user", "QUERY", "LOGGED_IN_DATASET_QUERY",
          "Open the Dataset query page; the execution gate makes the final decision");
    }
    try {
      actionAuthorization.requirePermission("dataset:query");
      return AccessProjection.ready(AccessDecision.ALLOWED);
    } catch (ActionAccessDeniedException denied) {
      return AccessProjection.ready(AccessDecision.FORBIDDEN);
    } catch (RuntimeException unavailable) {
      return AccessProjection.unavailable(
          "Dataset query authorization provider is unavailable",
          "Current authenticated user", "QUERY", "LOGGED_IN_DATASET_QUERY",
          "Retry from the Dataset query page; the execution gate makes the final decision");
    }
  }

  private ProducerProjection producerProjection(DatasetVersion version) {
    if (version.sourceType() != DatasetSourceType.QUERY_REVISION || version.sourceTaskAssetId() <= 0L) {
      return new ProducerProjection(null, new ProductSectionState(
          "producer", ProviderEvidenceState.EMPTY, "DATA_DEVELOPMENT", null,
          "This Dataset version has no linked development producer identity"));
    }
    if (taskCatalog == null) {
      return new ProducerProjection(null, unavailableSection(
          "producer", "DATA_DEVELOPMENT", "Task Catalog producer lookup is unavailable"));
    }
    try {
      DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot source = taskCatalog.get(version.sourceTaskAssetId());
      if (source.sourceOrigin() != DatasetTaskCatalogGateway.SourceOrigin.DATA_DEVELOPMENT
          || source.sourceRef() == null || source.sourceRef().isBlank()) {
        return new ProducerProjection(null, new ProductSectionState(
            "producer", ProviderEvidenceState.EMPTY, "DATA_DEVELOPMENT", null,
            "Source task asset is not linked to a Data Development node"));
      }
      return new ProducerProjection(
          new DomainRef("DATA_DEVELOPMENT_NODE", source.sourceRef()),
          new ProductSectionState("producer", ProviderEvidenceState.READY, "DATA_DEVELOPMENT", null, null));
    } catch (RuntimeException failure) {
      return new ProducerProjection(null, unavailableSection("producer", "DATA_DEVELOPMENT", failure.getMessage()));
    }
  }

  private AvailabilityState availability(Dataset dataset) {
    return dataset.status() == DatasetStatus.ONLINE
        ? AvailabilityState.UNKNOWN
        : AvailabilityState.UNAVAILABLE;
  }

  private boolean requiresUnavailableGovernanceFilter(ProductSearchCriteria criteria) {
    return criteria != null && (normalize(criteria.owner()) != null || normalize(criteria.visibility()) != null);
  }

  private AssetProjection assetProjection(long datasetId) {
    try {
      AssetSourceLookupService.SourceLookup lookup = assetLookup.lookup("DATASET", String.valueOf(datasetId));
      if ("FOUND".equals(lookup.state()) && lookup.assetId() != null) {
        return new AssetProjection(
            new DomainRef("ASSET", String.valueOf(lookup.assetId())),
            new ProductSectionState("asset", ProviderEvidenceState.READY, "ASSET", null, null));
      }
      return new AssetProjection(null,
          new ProductSectionState("asset", ProviderEvidenceState.EMPTY, "ASSET", null,
              "Dataset is not indexed in Asset Registry"));
    } catch (RuntimeException exception) {
      return new AssetProjection(null, unavailableSection("asset", "ASSET", exception.getMessage()));
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

  private record AssetProjection(DomainRef ref, ProductSectionState section) {}
  private record ProducerProjection(DomainRef ref, ProductSectionState section) {}
}
