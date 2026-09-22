import type { ReactNode } from 'react';
import type { ScreenComponent, ScreenTheme } from '../model';
import type { ScreenComponentInteraction } from './renderer-registry';

export const alpha = (hex: string, suffix: string) => (
  /^#[0-9a-fA-F]{6}$/.test(hex) ? `${hex}${suffix}` : hex
);

export function ScreenComponentFrame({
  component,
  theme,
  children,
  selected = false,
  onSelect,
}: {
  component: ScreenComponent;
  theme: ScreenTheme;
  children: ReactNode;
} & ScreenComponentInteraction) {
  const style = component.style;
  const transparent = component.type === 'text';
  const frame = style?.frame ?? 'standard';
  const borderColor = style?.borderColor ?? theme.panelBorderColor;
  const accentColor = style?.accentColor ?? theme.primaryColor;
  const background = style?.background ?? (transparent ? 'transparent' : theme.panelBackground);
  const glowShadow = style?.glow
    ? `0 0 28px ${alpha(accentColor, '12')}, inset 0 0 34px ${alpha(accentColor, '08')}`
    : undefined;

  return (
    <div
      data-screen-component={component.id}
      data-screen-selected={selected || undefined}
      onClick={onSelect}
      className={[
        'absolute box-border flex min-h-0 flex-col overflow-hidden transition-[outline,filter] duration-150',
        onSelect ? 'cursor-pointer hover:brightness-[1.05]' : '',
      ].join(' ')}
      style={{
        left: component.x,
        top: component.y,
        width: component.width,
        height: component.height,
        padding: style?.padding ?? (transparent ? 0 : 20),
        background,
        border: transparent ? undefined : `1px solid ${borderColor}`,
        borderRadius: style?.borderRadius ?? (transparent ? 0 : frame === 'hud' ? 4 : 10),
        boxShadow: style?.shadow ?? glowShadow,
        color: style?.color ?? theme.textColor,
        outline: selected ? '3px solid rgba(254, 44, 85, 0.92)' : undefined,
        outlineOffset: selected ? -3 : undefined,
        backdropFilter: frame === 'glass' ? 'blur(12px)' : undefined,
        animation: style?.effect === 'pulse' ? 'yak-screen-pulse 3.8s ease-in-out infinite' : undefined,
      }}
    >
      {frame === 'hud' && !transparent ? (
        <>
          <span className="pointer-events-none absolute left-[-1px] top-[-1px] h-4 w-4 border-l-2 border-t-2" style={{ borderColor: accentColor }} />
          <span className="pointer-events-none absolute right-[-1px] top-[-1px] h-4 w-4 border-r-2 border-t-2" style={{ borderColor: accentColor }} />
          <span className="pointer-events-none absolute bottom-[-1px] left-[-1px] h-4 w-4 border-b-2 border-l-2" style={{ borderColor: accentColor }} />
          <span className="pointer-events-none absolute bottom-[-1px] right-[-1px] h-4 w-4 border-b-2 border-r-2" style={{ borderColor: accentColor }} />
          <span
            className="pointer-events-none absolute left-5 top-0 h-px w-24"
            style={{ background: `linear-gradient(90deg, ${accentColor}, transparent)` }}
          />
        </>
      ) : null}
      {style?.effect === 'scan' && component.type !== 'map' ? (
        <span
          className="pointer-events-none absolute left-0 right-0 top-0 h-12"
          style={{
            animation: 'yak-screen-scan 7s linear infinite',
            background: `linear-gradient(180deg, transparent, ${alpha(accentColor, '18')}, transparent)`,
          }}
        />
      ) : null}
      {component.title ? (
        <div className="relative z-[2] mb-3 shrink-0">
          <div className="flex items-center gap-2">
            {frame === 'hud' ? (
              <span
                className="h-1.5 w-1.5 rotate-45"
                style={{ background: accentColor, boxShadow: `0 0 8px ${accentColor}` }}
              />
            ) : null}
            <div
              className="text-[16px] font-semibold leading-6 tracking-[0.02em]"
              style={{ color: style?.titleColor ?? theme.textColor }}
            >
              {component.title}
            </div>
          </div>
          {component.subtitle ? (
            <div
              className="mt-1 text-[11px] tracking-[0.04em]"
              style={{ color: style?.subtitleColor ?? theme.mutedTextColor }}
            >
              {component.subtitle}
            </div>
          ) : null}
        </div>
      ) : null}
      <div className="relative z-[1] min-h-0 flex-1">{children}</div>
    </div>
  );
}
