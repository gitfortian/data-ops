import type { ApiResponse } from '@/services/http/response';
import { previewDevelopmentSqlLineageRequest } from '@/services/data-development/legacy';

import { getSqlMetadataContext } from './editors/sql/metadata/sqlMetadataContextStore';
import type {
  DevelopmentId,
  DevelopmentSqlLineagePreview,
  DevelopmentSqlLineagePreviewRequest,
} from './types';

/**
 * @deprecated New data-development code should import from
 * `@/services/data-development`.
 */
export * from '@/services/data-development/legacy';

/**
 * Legacy editor adapter. SQL metadata belongs to the active editor session,
 * so this coordination remains at page level instead of making Service depend
 * on a page store.
 */
export const previewDevelopmentSqlLineage = (
  nodeId: DevelopmentId,
  payload: DevelopmentSqlLineagePreviewRequest,
): Promise<ApiResponse<DevelopmentSqlLineagePreview>> => {
  const metadataContext = getSqlMetadataContext(nodeId);
  return previewDevelopmentSqlLineageRequest(nodeId, {
    ...payload,
    databaseName: payload.databaseName ?? metadataContext?.database,
    schemaName: payload.schemaName ?? metadataContext?.schema,
  });
};
