import type { ScreenComponent } from '@/components/screen-engine';
import {
  isBindableScreenComponent,
  SCREEN_AGGREGATION_LABELS,
} from '@/features/digital-screen/binding';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';
import { screenRuntimeComponentRegistry } from './registry/builtin-plugins';

export { isBindableScreenComponent, SCREEN_AGGREGATION_LABELS };

export const canQueryScreenComponent = (
  component: ScreenComponent,
  binding?: DigitalScreenComponentBinding,
) => screenRuntimeComponentRegistry.canQuery(component, binding);

export const buildScreenDatasetQueryPayload = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
) => screenRuntimeComponentRegistry.buildQuery(component, binding);
