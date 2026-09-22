/** @deprecated Import runtime roles from `runtime/registry` modules. */
export { screenRuntimeComponentRegistry } from './registry/builtin-plugins';
export {
  ScreenRuntimeComponentRegistry,
  type ScreenRuntimeComponentPlugin,
} from './registry/component-registry';
export type { ScreenRuntimeAdapterContext } from './model';
