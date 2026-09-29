/**
 * @deprecated New data-service code should import from `@/services/data-service`.
 *
 * Debug, overview and log pages still use the historical response envelope.
 * Keep this facade until those routes are migrated independently.
 */
export * from '@/services/data-service/legacy';
export {
  DATA_SERVICE_NODE_SOURCE,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
} from '@/services/data-service/constants';
export type * from '@/services/data-service/types';
