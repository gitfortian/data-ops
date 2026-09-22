/**
 * @deprecated New realtime-sync code should import from
 * `@/services/realtime-sync`. This facade preserves the envelope-based API
 * used by the existing editors and runtime detail screens.
 */
export { realtimeApi } from '@/services/realtime-sync/legacy';
export type {
  RealtimeAction,
  RealtimePageQuery,
} from '@/services/realtime-sync';
