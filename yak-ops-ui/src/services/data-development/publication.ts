import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId } from './types';

const NODE_API = '/api/v1/data-development/nodes';
export const DATA_SERVICE_NODE_SOURCE =
  'DATA_DEVELOPMENT_DATA_SERVICE' as const;

export interface DataServicePublicationSource {
  sourceType: string;
  sourceRef: string;
  name: string;
  sourceKind: string;
  status: string;
  sourceRevisionId: number | string;
  sourceRevisionNo: number;
  dataSourceId: number | string;
  maxRows?: number | null;
  timeoutSeconds?: number | null;
  defaultPath: string;
  description?: string | null;
  updateTime?: string | null;
}

export interface DataServiceRuntimeApiSnapshot {
  id: number | string;
  name: string;
  path: string;
  runtimePath: string;
  enabled: boolean;
  sourceType?: string | null;
  sourceRef?: string | null;
  sourceRevisionId?: number | string | null;
  sourceRevisionNo?: number | null;
}

export interface DataServicePublicationState {
  published: boolean;
  updateAvailable: boolean;
  source: DataServicePublicationSource;
  detail?: DataServiceRuntimeApiSnapshot | null;
}

const publicationPath = (nodeId: DevelopmentId) =>
  `${NODE_API}/${encodeURIComponent(nodeId)}/data-service/publication`;

export const fetchDataServicePublicationState = (
  nodeId: DevelopmentId,
): Promise<DataServicePublicationState> =>
  HttpUtils.getData<DataServicePublicationState>(publicationPath(nodeId));

/** Product-level online action owned by the project-governed Data Development boundary. */
export const bringDataServiceOnline = async (
  nodeId: DevelopmentId,
  _state?: DataServicePublicationState,
): Promise<DataServiceRuntimeApiSnapshot> =>
  HttpUtils.postData<DataServiceRuntimeApiSnapshot>(
    `${publicationPath(nodeId)}/online`,
    {},
  );

export const takeDataServiceOffline = (
  nodeId: DevelopmentId,
): Promise<DataServiceRuntimeApiSnapshot> =>
  HttpUtils.postData<DataServiceRuntimeApiSnapshot>(
    `${publicationPath(nodeId)}/offline`,
    {},
  );
