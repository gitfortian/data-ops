# Screen Template Engine

`screen-engine` owns the generic, Dataset-agnostic rendering protocol used by Yak Ops digital screens. Layout, visual style and preview/runtime data are expressed through typed `ScreenTemplate` / `ScreenComponent` documents.

## Runtime boundaries

```text
ScreenTemplate
     |
     v
ScreenRenderer                 # canvas / scaling / component iteration
     |
     v
Renderer Registry              # component type -> React renderer
     |
     +-- metric
     +-- line / bar / pie
     +-- table
     +-- text
     +-- map
     `-- ticker
```

The renderer registry only knows how to draw components. Dataset binding, query planning and result adaptation live in `pages/digital-screen/runtime`, so the generic engine stays reusable by template previews and other future consumers.

## Public API

`ScreenRenderer` keeps its original public contract:

```tsx
import {
  ScreenRenderer,
  getScreenTemplateById,
} from '@/components/screen-engine';

const template = getScreenTemplateById('data-center');

export default function Preview() {
  if (!template) return null;
  return <ScreenRenderer template={template} />;
}
```

Runtime data can override one component without changing layout/style:

```tsx
<ScreenRenderer
  template={template}
  data={{
    tasks: {
      value: 320,
      unit: '个',
      trend: 8.4,
      trendDirection: 'up',
    },
  }}
/>
```

## Renderer roles

Built-in renderer definitions are declared in `runtime/builtin-renderers.tsx` through `defineScreenComponentRenderer(...)` and assembled by `ScreenComponentRendererRegistry`. Adding a supported component role therefore extends the registry instead of adding another branch to a central rendering `switch`.

The exported built-in registry is intentionally complete and duplicate registration is rejected. Digital Screen Dataset plugins use a separate registry because query/adapter concerns must not leak into this renderer layer.
