import type { ScreenComponent } from '../model';
import {
  defineScreenComponentRenderer,
  ScreenComponentRendererRegistry,
} from './renderer-registry';

const metric = {
  id: 'metric-1',
  type: 'metric',
  x: 0,
  y: 0,
  width: 100,
  height: 100,
} as ScreenComponent;

const theme = {
  background: '#000000',
  textColor: '#ffffff',
  mutedTextColor: '#999999',
  primaryColor: '#46d9ff',
  panelBackground: '#111111',
  panelBorderColor: '#222222',
};

describe('ScreenComponentRendererRegistry', () => {
  it('resolves rendering by component role instead of a central switch', () => {
    const registry = new ScreenComponentRendererRegistry();
    registry.register(defineScreenComponentRenderer('metric', () => <div>metric</div>));

    expect(registry.get('metric')).toBeDefined();
    expect(registry.render(metric, theme)).not.toBeNull();
  });

  it('rejects duplicate renderer registration', () => {
    const registry = new ScreenComponentRendererRegistry();
    const renderer = defineScreenComponentRenderer('metric', () => null);
    registry.register(renderer);

    expect(() => registry.register(renderer)).toThrow('Duplicate screen component renderer');
  });
});
