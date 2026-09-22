import type { ScreenAggregation } from '@/components/screen-engine';

export type DigitalScreenStatus = 'draft' | 'published';

export interface DigitalScreenMetricBinding {
  field: string;
  aggregation: ScreenAggregation;
}

export interface DigitalScreenComponentBinding {
  datasetId: string;
  dimensions: string[];
  metrics: DigitalScreenMetricBinding[];
}

export type DigitalScreenBindings = Record<string, DigitalScreenComponentBinding>;

export interface DigitalScreenInstance {
  id: string;
  name: string;
  description?: string;
  templateId: string;
  templateVersion: 1;
  status: DigitalScreenStatus;
  bindings: DigitalScreenBindings;
  /** Mutable Draft revision. Local repository compatibility keeps this optional. */
  revision?: number;
  publishedRevision?: number;
  publishedVersionNo?: number;
  hasUnpublishedChanges?: boolean;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

export interface DigitalScreenVersionSummary {
  id: string;
  versionNo: number;
  sourceRevision: number;
  name: string;
  publishedAt: string;
  current: boolean;
}

export interface DigitalScreenVersion {
  id: string;
  screenId: string;
  versionNo: number;
  sourceRevision: number;
  name: string;
  description?: string;
  templateId: string;
  templateVersion: 1;
  bindings: DigitalScreenBindings;
  publishedAt: string;
  createdAt: string;
}

export interface CreateDigitalScreenInput {
  name: string;
  description?: string;
  templateId: string;
  bindings?: DigitalScreenBindings;
}

export interface UpdateDigitalScreenInput {
  name?: string;
  description?: string;
  bindings?: DigitalScreenBindings;
}
