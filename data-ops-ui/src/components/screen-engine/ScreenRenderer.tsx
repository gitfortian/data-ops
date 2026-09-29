import { useEffect, useRef, useState } from 'react';
import type {
  ScreenComponent,
  ScreenDataOverrides,
  ScreenTemplate,
} from './model';
import { SCREEN_MOTION_CSS } from './PremiumVisuals';
import { alpha, screenComponentRendererRegistry } from './runtime';

const withRuntimeData = (
  component: ScreenComponent,
  overrides?: ScreenDataOverrides,
): ScreenComponent => {
  const data = overrides?.[component.id];
  return data ? ({ ...component, data } as ScreenComponent) : component;
};

export interface ScreenRendererProps {
  template: ScreenTemplate;
  data?: ScreenDataOverrides;
  className?: string;
  selectedComponentId?: string;
  onComponentClick?: (component: ScreenComponent) => void;
}

/** Fixed canvas/layout host. Component-specific rendering is delegated to the renderer registry. */
export function ScreenRenderer({
  template,
  data,
  className = '',
  selectedComponentId,
  onComponentClick,
}: ScreenRendererProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const updateScale = () => {
      const width = containerRef.current?.clientWidth ?? template.width;
      setScale(width > 0 ? width / template.width : 1);
    };
    updateScale();
    const observer = new ResizeObserver(updateScale);
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [template.width]);

  return (
    <div
      ref={containerRef}
      className={`relative w-full overflow-hidden ${className}`}
      style={{
        aspectRatio: `${template.width} / ${template.height}`,
        background: template.theme.background,
      }}
    >
      <style>{SCREEN_MOTION_CSS}</style>
      <div
        className="absolute left-0 top-0 overflow-hidden"
        style={{
          width: template.width,
          height: template.height,
          transform: `scale(${scale})`,
          transformOrigin: 'left top',
          background: template.theme.background,
          color: template.theme.textColor,
          fontFamily: template.theme.fontFamily,
        }}
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-60"
          style={{
            backgroundImage: `linear-gradient(${alpha(template.theme.primaryColor, '05')} 1px, transparent 1px), linear-gradient(90deg, ${alpha(template.theme.primaryColor, '05')} 1px, transparent 1px)`,
            backgroundSize: '48px 48px',
            maskImage: 'linear-gradient(180deg, rgba(0,0,0,.55), transparent 88%)',
          }}
        />
        {template.components.map((component) => {
          const runtimeComponent = withRuntimeData(component, data);
          return screenComponentRendererRegistry.render(
            runtimeComponent,
            template.theme,
            {
              selected: component.id === selectedComponentId,
              onSelect: onComponentClick
                ? () => onComponentClick(runtimeComponent)
                : undefined,
            },
            component.id,
          );
        })}
      </div>
    </div>
  );
}
