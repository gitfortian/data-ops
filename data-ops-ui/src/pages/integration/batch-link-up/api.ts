/**
 * @deprecated New code should import from `@/services/batch-link-up`.
 *
 * Configuration and detail pages still import this historical path. Keep the
 * public surface stable while the HTTP implementation lives in services.
 */
export * from '@/services/batch-link-up/definition-legacy';
export type * from '@/services/batch-link-up/types';
