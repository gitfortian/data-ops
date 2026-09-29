import type { SVGProps } from 'react';

import runningWebp from '../../assets/gif/running.webp';
import './index.less';

export const YAK_STATUS_VALUES = [
  'success',
  'failed',
  'running',
  'pending',
  'warning',
  'paused',
  'canceled',
  'unknown',
] as const;

export type YakStatus = (typeof YAK_STATUS_VALUES)[number];

export type YakStatusIconProps = Omit<
  SVGProps<SVGSVGElement>,
  'children' | 'height' | 'width'
> & {
  /** Business-agnostic semantic status rendered by the icon. */
  status: YakStatus;
  /** Icon size in pixels. */
  size?: number;
  /** Enables subtle motion for active states such as running and pending. */
  animated?: boolean;
  /** Optional accessible title. Decorative icons are hidden from assistive tech. */
  title?: string;
};

const BLOB_PATH =
  'M12.08 4.25c4.55 0 7.67 2.58 7.67 6.92 0 4.93-2.89 8.58-7.9 8.58-4.69 0-7.6-3.04-7.6-7.62 0-4.61 2.87-7.88 7.83-7.88Z';

function StaticSurface() {
  return <path className="yak-status-icon__surface" d={BLOB_PATH} />;
}

function StaticRunningGlyph() {
  return (
    <>
      <path
        className="yak-status-icon__surface yak-status-icon__surface--soft"
        d={BLOB_PATH}
      />
      <g className="yak-status-icon__orbit">
        <path
          className="yak-status-icon__orbit-line"
          d="M4.55 10.55c.6-3.23 3.19-5.72 6.45-6.1"
        />
        <path
          className="yak-status-icon__orbit-line"
          d="M19.45 13.45c-.6 3.23-3.19 5.72-6.45 6.1"
        />
        <circle
          className="yak-status-icon__orbit-node"
          cx="17.45"
          cy="7.15"
          r="1.05"
        />
        <circle
          className="yak-status-icon__orbit-node yak-status-icon__orbit-node--minor"
          cx="6.35"
          cy="16.9"
          r="0.72"
        />
      </g>
      <circle className="yak-status-icon__core" cx="12" cy="12" r="3.15" />
    </>
  );
}

function StatusGlyph({
  status,
  animated,
}: {
  status: YakStatus;
  animated: boolean;
}) {
  switch (status) {
    case 'success':
      return (
        <>
          <StaticSurface />
          <path
            className="yak-status-icon__symbol"
            d="m8.15 12.1 2.38 2.4 5.36-5.55"
          />
        </>
      );
    case 'failed':
      return (
        <>
          <StaticSurface />
          <path
            className="yak-status-icon__symbol"
            d="m9.08 9.08 5.84 5.84m0-5.84-5.84 5.84"
          />
        </>
      );
    case 'running':
      if (!animated) {
        return <StaticRunningGlyph />;
      }
      return (
        <>
          <image
            className="yak-status-icon__running-image"
            href={runningWebp}
            x="0"
            y="0"
            width="24"
            height="24"
            preserveAspectRatio="xMidYMid meet"
            aria-hidden="true"
          />
          <g
            className="yak-status-icon__running-reduced-motion"
            aria-hidden="true"
          >
            <StaticRunningGlyph />
          </g>
        </>
      );
    case 'pending':
      return (
        <>
          <StaticSurface />
          <g className="yak-status-icon__dots">
            <circle
              className="yak-status-icon__dot yak-status-icon__dot--1"
              cx="8.7"
              cy="12"
              r="1"
            />
            <circle
              className="yak-status-icon__dot yak-status-icon__dot--2"
              cx="12"
              cy="12"
              r="1"
            />
            <circle
              className="yak-status-icon__dot yak-status-icon__dot--3"
              cx="15.3"
              cy="12"
              r="1"
            />
          </g>
        </>
      );
    case 'warning':
      return (
        <>
          <StaticSurface />
          <path className="yak-status-icon__symbol" d="M12 8.35v4.75" />
          <circle
            className="yak-status-icon__symbol-dot"
            cx="12"
            cy="15.75"
            r="1"
          />
        </>
      );
    case 'paused':
      return (
        <>
          <StaticSurface />
          <path
            className="yak-status-icon__symbol"
            d="M10 9.05v5.9m4-5.9v5.9"
          />
        </>
      );
    case 'canceled':
      return (
        <>
          <StaticSurface />
          <path className="yak-status-icon__symbol" d="m8.2 15.8 7.6-7.6" />
        </>
      );
    case 'unknown':
      return (
        <>
          <StaticSurface />
          <path
            className="yak-status-icon__symbol"
            d="M9.65 9.7a2.55 2.55 0 0 1 4.91.95c0 1.85-2.56 1.94-2.56 3.18"
          />
          <circle
            className="yak-status-icon__symbol-dot"
            cx="12"
            cy="16.15"
            r="0.9"
          />
        </>
      );
  }
}

export default function YakStatusIcon({
  status,
  size = 18,
  animated = true,
  className,
  title,
  'aria-label': ariaLabel,
  'aria-labelledby': ariaLabelledby,
  ...svgProps
}: YakStatusIconProps) {
  const accessibleLabel = ariaLabel ?? (ariaLabelledby ? undefined : title);
  const isAccessible = Boolean(accessibleLabel || ariaLabelledby);

  return (
    <svg
      {...svgProps}
      className={[
        'yak-status-icon',
        `yak-status-icon--${status}`,
        animated ? 'yak-status-icon--animated' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      focusable="false"
      data-status={status}
      data-animated={animated ? 'true' : 'false'}
      role={isAccessible ? 'img' : undefined}
      aria-label={accessibleLabel}
      aria-labelledby={ariaLabelledby}
      aria-hidden={isAccessible ? undefined : true}
    >
      {title ? <title>{title}</title> : null}
      <StatusGlyph status={status} animated={animated} />
    </svg>
  );
}
