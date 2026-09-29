import {
  createElement,
  type ComponentType,
  type Key,
  type ReactElement,
} from 'react';
import type {
  ScreenComponent,
  ScreenComponentType,
  ScreenTheme,
} from '../model';

export interface ScreenComponentInteraction {
  selected?: boolean;
  onSelect?: () => void;
}

export interface ScreenComponentRendererProps extends ScreenComponentInteraction {
  component: ScreenComponent;
  theme: ScreenTheme;
}

export type TypedScreenComponentRendererProps<T extends ScreenComponentType> =
  Omit<ScreenComponentRendererProps, 'component'> & {
    component: Extract<ScreenComponent, { type: T }>;
  };

export type ScreenComponentRenderer = ComponentType<ScreenComponentRendererProps>;
export type TypedScreenComponentRenderer<T extends ScreenComponentType> =
  ComponentType<TypedScreenComponentRendererProps<T>>;

export interface ScreenComponentRendererDefinition {
  type: ScreenComponentType;
  renderer: ScreenComponentRenderer;
}

export const defineScreenComponentRenderer = <T extends ScreenComponentType>(
  type: T,
  renderer: TypedScreenComponentRenderer<T>,
): ScreenComponentRendererDefinition => ({
  type,
  renderer: renderer as unknown as ScreenComponentRenderer,
});

/** React renderer registry. Dataset binding/query roles live in Digital Screen Runtime. */
export class ScreenComponentRendererRegistry {
  private readonly renderers = new Map<ScreenComponentType, ScreenComponentRenderer>();

  register(definition: ScreenComponentRendererDefinition) {
    if (this.renderers.has(definition.type)) {
      throw new Error(`Duplicate screen component renderer "${definition.type}"`);
    }
    this.renderers.set(definition.type, definition.renderer);
    return this;
  }

  get(type: ScreenComponentType) {
    return this.renderers.get(type);
  }

  list() {
    return [...this.renderers.keys()];
  }

  render(
    component: ScreenComponent,
    theme: ScreenTheme,
    interaction: ScreenComponentInteraction = {},
    key?: Key,
  ): ReactElement | null {
    const Renderer = this.get(component.type);
    if (!Renderer) return null;
    return createElement(Renderer, {
      key,
      component,
      theme,
      ...interaction,
    });
  }
}
