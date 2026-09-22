/** Compatibility façade for runtime data roles. New code should import from binding/query directly. */
export {
  buildScreenDatasetQueryPayload,
  canQueryScreenComponent,
  isBindableScreenComponent,
  SCREEN_AGGREGATION_LABELS,
} from './binding';
export { queryScreenComponentData, toScreenComponentData } from './query';
