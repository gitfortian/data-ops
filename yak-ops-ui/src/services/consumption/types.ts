export type ProductType = 'DATASET' | 'DATA_SERVICE';
export type ProductSearchState = 'READY' | 'FORBIDDEN' | 'UNAVAILABLE';
export type ProductLookupState = 'FOUND' | 'NOT_FOUND' | 'NOT_DISCOVERABLE' | 'FORBIDDEN' | 'UNAVAILABLE';
export type AvailabilityState = 'AVAILABLE' | 'UNAVAILABLE' | 'UNKNOWN';
export type SourceLifecycleState = 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | 'DEPRECATED';

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
  section: string;
  providerState: string;
  reason?: string | null;
}

export interface AccessProjection {
  providerState: string;
  decision?: string | null;
  reason?: string | null;
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

export interface ProductDiscoveryResult {
  products: DataProductView[];
  total: number;
  providerStates: Partial<Record<ProductType, ProductSearchState>>;
  providerReasons: Partial<Record<ProductType, string>>;
}

export interface ProductLookupResult {
  state: ProductLookupState;
  product?: DataProductView | null;
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
