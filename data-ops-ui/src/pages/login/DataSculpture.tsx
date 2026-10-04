import { useEffect, useRef, type PointerEvent } from 'react';
import dataStructure from './data-structure.svg';

/** Decorative artwork has no business state or relationship to authentication. */
export default function DataSculpture() {
  const element = useRef<HTMLDivElement>(null);
  const pendingFrame = useRef<number>();

  const reset = () => {
    if (pendingFrame.current !== undefined) cancelAnimationFrame(pendingFrame.current);
    element.current?.style.setProperty('--art-x', '0px');
    element.current?.style.setProperty('--art-y', '0px');
  };

  useEffect(
    () => () => {
      if (pendingFrame.current !== undefined) cancelAnimationFrame(pendingFrame.current);
    },
    [],
  );

  const followPointer = (event: PointerEvent<HTMLDivElement>) => {
    if (!window.matchMedia('(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)').matches)
      return;
    const bounds = event.currentTarget.getBoundingClientRect();
    const x = Math.max(-1, Math.min(1, ((event.clientX - bounds.left) / bounds.width) * 2 - 1)) * 6;
    const y = Math.max(-1, Math.min(1, ((event.clientY - bounds.top) / bounds.height) * 2 - 1)) * 4;
    if (pendingFrame.current !== undefined) cancelAnimationFrame(pendingFrame.current);
    pendingFrame.current = requestAnimationFrame(() => {
      element.current?.style.setProperty('--art-x', `${x}px`);
      element.current?.style.setProperty('--art-y', `${y}px`);
    });
  };

  return (
    <div
      ref={element}
      className="dataops-login-art"
      aria-hidden="true"
      onPointerMove={followPointer}
      onPointerLeave={reset}
    >
      <img src={dataStructure} width="540" height="340" alt="" draggable={false} />
    </div>
  );
}
