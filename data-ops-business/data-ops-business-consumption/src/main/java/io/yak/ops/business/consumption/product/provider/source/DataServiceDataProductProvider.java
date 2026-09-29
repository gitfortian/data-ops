package io.yak.ops.business.consumption.product.provider.source;

import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceRef;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.AccessProjection;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.DataServiceContractPayload;
import io.yak.ops.business.consumption.product.model.DataServiceContractPayload.ParameterContract;
import io.yak.ops.business.consumption.product.model.DataServiceContractPayload.ResponseFieldContract;
import io.yak.ops.business.consumption.product.model.ProductSectionState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchResult;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.documentation.ApiDocumentation;
import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationReader;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Projects published Data Service definitions/runtime bindings into Consumption without owning them. */
@Component
@ConditionalOnDataSourceEnabled
public class DataServiceDataProductProvider implements DataProductProvider {

  private final DataServiceReader reader;
  private final DataServiceViewFactory viewFactory;
  private final AssetSourceLookupService assetLookup;
  private final DataServiceDocumentationReader documentationReader;

  @Autowired
  public DataServiceDataProductProvider(
      DataServiceReader reader,
      DataServiceViewFactory viewFactory,
      AssetSourceLookupService assetLookup,
      DataServiceDocumentationReader documentationReader) {
    this.reader = reader;
    this.viewFactory = viewFactory;
    this.assetLookup = assetLookup;
    this.documentationReader = documentationReader;
  }

  /** Compatibility constructor for focused tests without the documentation adapter. */
  public DataServiceDataProductProvider(
      DataServiceReader reader,
      DataServiceViewFactory viewFactory,
      AssetSourceLookupService assetLookup) {
    this(reader, viewFactory, assetLookup, null);
  }

  @Override
  public ProductType productType() {
    return ProductType.DATA_SERVICE;
  }

  @Override
  public ProductLookupResult get(ProductKey productKey) {
    if (productKey == null || productKey.productType() != ProductType.DATA_SERVICE) {
      return ProductLookupResult.notFound();
    }
    Long id = parsePositiveId(productKey.sourceIdentity());
    if (id == null) return ProductLookupResult.notFound();

    final DataServiceDefinition definition;
    try {
      definition = reader.require(id);
    } catch (IllegalArgumentException notFound) {
      return ProductLookupResult.notFound();
    } catch (RuntimeException exception) {
      return ProductLookupResult.unavailable(exception.getMessage());
    }

    if (definition.projectId() == null) return ProductLookupResult.notDiscoverable();
    try {
      return ProductLookupResult.found(project(definition));
    } catch (RuntimeException exception) {
      return ProductLookupResult.unavailable(exception.getMessage());
    }
  }

  @Override
  public ProductSearchResult search(ProductSearchCriteria criteria) {
    if (criteria != null && criteria.productType() != null
        && criteria.productType() != ProductType.DATA_SERVICE) {
      return ProductSearchResult.ready(List.of(), 0);
    }
    if (requiresUnavailableGovernanceFilter(criteria)) {
      return ProductSearchResult.unavailable(
          "Data Service owner/visibility owning evidence is not exposed yet");
    }
    try {
      List<DataProductView> products = reader.list().stream()
          .filter(definition -> definition.projectId() != null)
          .filter(definition -> matches(definition, criteria))
          .map(this::project)
          .toList();
      return ProductSearchResult.ready(products, products.size());
    } catch (RuntimeException exception) {
      return ProductSearchResult.unavailable(exception.getMessage());
    }
  }

  private boolean matches(DataServiceDefinition definition, ProductSearchCriteria criteria) {
    if (criteria == null) return true;
    if (criteria.projectId() != null && !criteria.projectId().equals(definition.projectId())) return false;
    if (criteria.lifecycle() != null && criteria.lifecycle() != SourceLifecycleState.PUBLISHED) return false;
    AvailabilityState availability = availability(definition);
    if (criteria.availability() != null && criteria.availability() != availability) return false;
    String keyword = normalize(criteria.keyword());
    if (keyword == null) return true;
    return contains(definition.settings().name(), keyword)
        || contains(definition.settings().description(), keyword)
        || contains(definition.settings().path(), keyword);
  }

  private DataProductView project(DataServiceDefinition definition) {
    DataServiceView view = viewFactory.view(definition);
    SourceReference source = definition.sourceReference();
    AssetProjection asset = assetProjection(definition.id());
    DocumentationProjection documentation = documentation(definition.id());
    List<ProductSectionState> sections = List.of(
        sourceGovernanceSection(definition),
        unavailableSection("ownership", "DATA_SERVICE", "Data Service owning contract does not expose owner"),
        unavailableSection("visibility", "SECURITY", "Data Service visibility policy is not exposed yet"),
        documentation.section(),
        unavailableSection("quality", "QUALITY", "Data Service Quality section reader is not connected to Consumption"),
        unavailableSection("security", "SECURITY", "Data Service Security classification evidence is not connected to Consumption"),
        unavailableSection("lineage", "LINEAGE", "Data Service lineage evidence is not connected to Consumption"),
        asset.section());
    return new DataProductView(
        new ProductKey(ProductType.DATA_SERVICE, String.valueOf(definition.id())),
        new SourceRef(ProductType.DATA_SERVICE, String.valueOf(definition.id())),
        producerRef(source),
        asset.ref(),
        definition.settings().name(),
        definition.settings().description(),
        null,
        definition.projectId(),
        null,
        activeVersion(definition),
        SourceLifecycleState.PUBLISHED,
        availability(definition),
        AccessProjection.unavailable(
            "A logged-in user's permissions do not determine public API-key invocation access",
            "Registered Data Service Consumer/API Key",
            "INVOKE",
            "PUBLIC_API_KEY",
            "Use the service's configured Consumer/API Key and public invocation endpoint"),
        sections,
        new DataServiceContractPayload(
            view.runtimePath(),
            view.parameterNames(),
            view.authMode(),
            definition.settings().maxRows(),
            definition.settings().timeoutSeconds(),
            definition.settings().paginationEnabled(),
            definition.settings().enabled(),
            source.sourceType(),
            source.sourceRef(),
            source.sourceRevisionId(),
            source.sourceRevisionNo(),
            definition.runtimeGeneration(),
            documentation.parameters(),
            documentation.responseFields(),
            documentation.documented(),
            documentation.schemaStale(),
            documentation.updatedAt()));
  }

  private DocumentationProjection documentation(Long apiId) {
    if (documentationReader == null) {
      return DocumentationProjection.unavailable("Data Service documentation reader is unavailable");
    }
    try {
      ApiDocumentation documentation = documentationReader.get(apiId);
      List<ParameterContract> parameters = documentation.parameters().stream()
          .map(item -> new ParameterContract(
              item.name(), item.type(), item.required(), item.description(), item.example()))
          .toList();
      List<ResponseFieldContract> responseFields = documentation.responseFields().stream()
          .map(item -> new ResponseFieldContract(
              item.name(), item.type(), item.nullable(), item.description(), item.example()))
          .toList();
      ProviderEvidenceState state = documentation.schemaStale()
          ? ProviderEvidenceState.UNAVAILABLE
          : ProviderEvidenceState.READY;
      String reason = documentation.schemaStale() ? "Documented schema is stale for the current runtime SQL" : null;
      return new DocumentationProjection(
          parameters, responseFields, documentation.documented(), documentation.schemaStale(),
          documentation.updateTime(), new ProductSectionState(
              "service-interface", state, "DATA_SERVICE", null, reason));
    } catch (RuntimeException failure) {
      return DocumentationProjection.unavailable(failure.getMessage());
    }
  }

  private ProductSectionState sourceGovernanceSection(DataServiceDefinition definition) {
    return unavailableSection(
        "source-governance",
        "DATA_SERVICE",
        "Data Service source contract exposes update time but no governed evidence projection");
  }

  private SourceVersionRef activeVersion(DataServiceDefinition definition) {
    SourceReference source = definition.sourceReference();
    if (source.sourceRevisionId() != null) {
      String display = source.sourceRevisionNo() == null ? null : "v" + source.sourceRevisionNo();
      return new SourceVersionRef(String.valueOf(source.sourceRevisionId()), display);
    }
    return new SourceVersionRef(
        "runtime-generation:" + definition.runtimeGeneration(),
        "runtime-" + definition.runtimeGeneration());
  }

  private DomainRef producerRef(SourceReference source) {
    if (source.sourceType() == null || source.sourceType().isBlank()
        || source.sourceRef() == null || source.sourceRef().isBlank()) {
      return null;
    }
    return new DomainRef(source.sourceType(), source.sourceRef());
  }

  private AvailabilityState availability(DataServiceDefinition definition) {
    return definition.settings().enabled() ? AvailabilityState.UNKNOWN : AvailabilityState.UNAVAILABLE;
  }

  private boolean requiresUnavailableGovernanceFilter(ProductSearchCriteria criteria) {
    return criteria != null && (normalize(criteria.owner()) != null || normalize(criteria.visibility()) != null);
  }

  private AssetProjection assetProjection(Long serviceId) {
    try {
      AssetSourceLookupService.SourceLookup lookup = assetLookup.lookup("DATA_SERVICE", String.valueOf(serviceId));
      if ("FOUND".equals(lookup.state()) && lookup.assetId() != null) {
        return new AssetProjection(
            new DomainRef("ASSET", String.valueOf(lookup.assetId())),
            new ProductSectionState("asset", ProviderEvidenceState.READY, "ASSET", null, null));
      }
      return new AssetProjection(null,
          new ProductSectionState("asset", ProviderEvidenceState.EMPTY, "ASSET", null,
              "Data Service is not indexed in Asset Registry"));
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

  private record DocumentationProjection(
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields,
      boolean documented,
      boolean schemaStale,
      java.time.LocalDateTime updatedAt,
      ProductSectionState section) {
    private DocumentationProjection {
      parameters = List.copyOf(parameters);
      responseFields = List.copyOf(responseFields);
    }

    private static DocumentationProjection unavailable(String reason) {
      return new DocumentationProjection(
          List.of(), List.of(), false, false, null,
          new ProductSectionState("service-interface", ProviderEvidenceState.UNAVAILABLE,
              "DATA_SERVICE", null, reason));
    }
  }
}
