import { builtinScreenComponentRenderers } from './builtin-renderers';
import { ScreenComponentRendererRegistry } from './renderer-registry';

export const screenComponentRendererRegistry = builtinScreenComponentRenderers.reduce(
  (registry, definition) => registry.register(definition),
  new ScreenComponentRendererRegistry(),
);

export { alpha, ScreenComponentFrame } from './frame';
export {
  defineScreenComponentRenderer,
  ScreenComponentRendererRegistry,
} from './renderer-registry';
export type {
  ScreenComponentInteraction,
  ScreenComponentRendererDefinition,
  ScreenComponentRendererProps,
  TypedScreenComponentRenderer,
  TypedScreenComponentRendererProps,
} from './renderer-registry';
