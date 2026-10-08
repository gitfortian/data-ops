export type ProductType = 'DATASET' | 'DATA_SERVICE';
export type ProductSearchState = 'READY' | 'FORBIDDEN' | 'UNAVAILABLE';
export type ProductLookupState = 'FOUND' | 'NOT_FOUND' | 'NOT_DISCOVERABLE' | 'FORBIDDEN' | 'UNAVAILABLE';
export type AvailabilityState = 'AVAILABLE' | 'UNAVAILABLE' | 'UNKNOWN';
export type SourceLifecycleState = 'NOT_PUBLISHED' | 'PUBLISHED' | 'DEPRECATED' | 'RETIRED';
export type ProviderEvidenceState = 'READY' | 'EMPTY' | 'FORBIDDEN' | 'UNAVAILABLE' | 'NOT_APPLICABLE';

export interface ProductKeyRef {
  productType: ProductType;
  sourceIdentity: string;
}

export interface SourceVersionRef {
  identity: string;
  displayVersion?: string | null;
}

export interface DomainRef {
  domain: string;
  identity: string;
}

export interface ProductSectionState {
  sectionKey: string;
  state: ProviderEvidenceState;
  ownerDomain: string;
  observedAt?: string | null;
  reason?: string | null;
}

export interface AccessProjection {
  decision?: string | null;
  providerState: ProviderEvidenceState;
  reason?: string | null;
  subject?: string | null;
  action?: string | null;
  plane?: string | null;
  nextStep?: string | null;
}

export interface DataProductView {
  productKey: ProductKeyRef;
  sourceRef: unknown;
  producerRef?: DomainRef | null;
  assetRef?: DomainRef | null;
  name: string;
  description?: string | null;
  owner?: string | null;
  projectId: number;
  visibility?: string | null;
  activeVersion?: SourceVersionRef | null;
  lifecycle: SourceLifecycleState;
  availability: AvailabilityState;
  access: AccessProjection;
  sections: ProductSectionState[];
  contractPayload: Record<string, unknown> & { productType?: ProductType };
}

export type ConsumptionMode = 'QUERY' | 'PREVIEW' | 'EXPORT' | 'API_INVOKE' | 'DOWNSTREAM';
export type SubscriptionStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED';

export interface ConsumerRef {
  consumerType: 'USER' | 'TEAM' | 'DASHBOARD' | 'DATA_SERVICE' | 'JOB';
  sourceDomain: string;
  sourceIdentity: string;
  displayHint?: string | null;
}

export interface Subscription {
  id: number;
  projectId: number;
  productKey: string;
  consumerRef: ConsumerRef;
  consumptionMode: ConsumptionMode;
  status: SubscriptionStatus;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export type EvidenceState = 'READY' | 'EMPTY' | 'UNAVAILABLE' | 'FORBIDDEN';

/** Successful usage grouped by immutable source version in the returned evidence window. */
export interface ObservedVersion {
  sourceVersion: SourceVersionRef;
  successfulUsageCount: number;
  lastObservedAt?: string | null;
  providerEvidenceRefs: string[];
}

export interface KnownConsumer {
  consumerRef: ConsumerRef;
  declaredModes: ConsumptionMode[];
  observedModes: ConsumptionMode[];
  activeSubscriptionCount: number;
  successfulUsageCount: number;
  lastDeclaredAt?: string | null;
  lastObservedAt?: string | null;
  providerEvidenceRefs: string[];
  observedVersions?: ObservedVersion[];
}

/** Bounded provider and normalized read windows; never a full-history guarantee. */
export interface ImpactEvidenceCoverage {
  requestedUsageLimit: number;
  sourceRecordCount: number;
  normalizedUsageCount: number;
  sourceWindowLimitReached: boolean;
  normalizedUsageWindowLimitReached: boolean;
  normalizationGapCount: number;
  sourceReadUnavailable: boolean;
}

export interface ConsumerImpact {
  productKey: { productType: ProductType; sourceIdentity: string };
  subscriptionState: EvidenceState;
  usageState: EvidenceState;
  consumers: KnownConsumer[];
  coverageNote: string;
  /** Optional for compatibility with existing responses and test fixtures. */
  coverage?: ImpactEvidenceCoverage | null;
}

export interface ProductDiscoveryResult {
  products: DataProductView[];
  total: number;
  providerStates: Partial<Record<ProductType, ProductSearchState>>;
  providerReasons: Partial<Record<ProductType, string>>;
}

export interface ProductNavigation {
  canonicalHref: string;
  sourceHref?: string | null;
  assetHref?: string | null;
  producerHref?: string | null;
}

export interface GovernanceEvidence {
  sectionKey: string;
  state: ProviderEvidenceState;
  ownerDomain: string;
  observedAt?: string | null;
  facts: Record<string, unknown>;
  reason?: string | null;
}

export interface ProductLookupResult {
  state: ProductLookupState;
  product?: DataProductView | null;
  navigation?: ProductNavigation | null;
  governanceEvidence?: GovernanceEvidence[];
  reason?: string | null;
}

export interface NavigationResolution {
  state: ProductLookupState | 'NOT_APPLICABLE';
  productKey?: ProductKeyRef | null;
  canonicalHref?: string | null;
  reason?: string | null;
}

export interface ProductDiscoveryQuery {
  productType?: ProductType;
  keyword?: string;
  owner?: string;
  visibility?: string;
  lifecycle?: SourceLifecycleState;
  availability?: AvailabilityState;
}

export const productKeyValue = (ref: ProductKeyRef) => `${ref.productType}:${ref.sourceIdentity}`;
