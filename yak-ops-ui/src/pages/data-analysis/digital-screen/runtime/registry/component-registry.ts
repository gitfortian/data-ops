import type {
  ScreenComponent,
  ScreenComponentData,
  ScreenComponentType,
} from '@/components/screen-engine';
import type { DatasetQueryPayload } from '@/services/dataset';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';
import type { ScreenRuntimeAdapterContext } from '../model';

export interface ScreenRuntimeComponentPlugin {
  type: ScreenComponentType;
  bindable: boolean;
  canQuery?: (binding: DigitalScreenComponentBinding) => boolean;
  buildQuery?: (binding: DigitalScreenComponentBinding) => DatasetQueryPayload;
  adaptData?: (context: ScreenRuntimeAdapterContext) => ScreenComponentData | undefined;
}

/** Runtime role registry. It owns binding/query/adapter capabilities, not React rendering. */
export class ScreenRuntimeComponentRegistry {
  private readonly plugins = new Map<ScreenComponentType, ScreenRuntimeComponentPlugin>();

  register(plugin: ScreenRuntimeComponentPlugin) {
    if (this.plugins.has(plugin.type)) {
      throw new Error(`Duplicate screen runtime component plugin "${plugin.type}"`);
    }
    this.plugins.set(plugin.type, plugin);
    return this;
  }

  get(type: ScreenComponentType) {
    return this.plugins.get(type);
  }

  list() {
    return [...this.plugins.values()];
  }

  isBindable(component?: ScreenComponent) {
    return Boolean(component && this.get(component.type)?.bindable);
  }

  canQuery(component: ScreenComponent, binding?: DigitalScreenComponentBinding) {
    if (!binding?.datasetId) return false;
    const plugin = this.get(component.type);
    return Boolean(plugin?.bindable && plugin.canQuery?.(binding));
  }

  buildQuery(component: ScreenComponent, binding: DigitalScreenComponentBinding) {
    const plugin = this.get(component.type);
    if (!plugin?.buildQuery) {
      throw new Error(`组件 ${component.type} 不支持 Dataset 查询`);
    }
    return plugin.buildQuery(binding);
  }

  adaptData(
    component: ScreenComponent,
    binding: DigitalScreenComponentBinding,
    context: Omit<ScreenRuntimeAdapterContext, 'component' | 'binding'>,
  ) {
    const plugin = this.get(component.type);
    return plugin?.adaptData?.({ component, binding, ...context });
  }
}
