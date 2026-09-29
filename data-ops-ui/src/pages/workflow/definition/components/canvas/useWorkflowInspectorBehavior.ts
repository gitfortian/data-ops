import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';

const DEFAULT_PANEL_WIDTH = 380;
const MIN_PANEL_WIDTH = 320;
const MAX_PANEL_WIDTH = 640;
const MIN_CANVAS_WIDTH = 360;
const PANEL_WIDTH_STORAGE_KEY = 'yak.workflow.inspector.width';

const readStoredPanelWidth = (fallback: number) => {
  if (typeof window === 'undefined') return fallback;
  const stored = window.localStorage.getItem(PANEL_WIDTH_STORAGE_KEY);
  if (!stored) return fallback;
  const value = Number(stored);
  if (!Number.isFinite(value)) return fallback;
  return Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, value));
};

const useWorkflowInspectorBehavior = (defaultWidth = DEFAULT_PANEL_WIDTH) => {
  const [panelWidth, setPanelWidth] = useState(() => readStoredPanelWidth(defaultWidth));
  const [resizing, setResizing] = useState(false);
  const panelWidthRef = useRef(panelWidth);
  const cleanupResizeRef = useRef<(() => void) | undefined>(undefined);

  useEffect(() => {
    panelWidthRef.current = panelWidth;
  }, [panelWidth]);

  useEffect(() => {
    const keepInspectorOpen = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;

      // React Flow nodes live inside the pane. Let node clicks propagate so the
      // editor can switch the inspector to the newly selected node while it is open.
      if (target.closest('.react-flow__node')) return;

      if (!target.closest('.react-flow__pane')) return;

      // React Flow clears node selection when the empty pane receives a click. The
      // workflow inspector intentionally behaves like Dify: clicking empty canvas
      // keeps the current inspector open, and only its explicit close button dismisses it.
      event.stopPropagation();
      event.stopImmediatePropagation();
    };

    document.addEventListener('click', keepInspectorOpen, true);
    return () => document.removeEventListener('click', keepInspectorOpen, true);
  }, []);

  useEffect(() => () => cleanupResizeRef.current?.(), []);

  useEffect(() => {
    const miniMap = document.querySelector('.react-flow__minimap');
    if (!(miniMap instanceof HTMLElement)) return undefined;

    miniMap.style.setProperty('right', `${Math.round(panelWidth) + 24}px`, 'important');
    return () => miniMap.style.removeProperty('right');
  }, [panelWidth]);

  const handleResizePointerDown = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;

    event.preventDefault();
    event.stopPropagation();
    cleanupResizeRef.current?.();

    const handle = event.currentTarget;
    const pointerId = event.pointerId;
    const startX = event.clientX;
    const startWidth = panelWidthRef.current;
    const flowBounds = document.querySelector('.react-flow')?.getBoundingClientRect();
    const maxWidth = flowBounds
      ? Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, flowBounds.width - MIN_CANVAS_WIDTH))
      : MAX_PANEL_WIDTH;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    // The resize handle sits on top of a React Flow surface, which has its own pointer
    // gestures. Capture the pointer explicitly so the drag keeps belonging to the
    // inspector even after the cursor leaves the narrow handle hit area.
    try {
      handle.setPointerCapture(pointerId);
    } catch {
      // Older browsers may not expose pointer capture; window listeners below remain
      // as a safe fallback.
    }

    const handlePointerMove = (moveEvent: PointerEvent) => {
      if (moveEvent.pointerId !== pointerId) return;
      moveEvent.preventDefault();
      const nextWidth = startWidth + startX - moveEvent.clientX;
      setPanelWidth(Math.min(maxWidth, Math.max(MIN_PANEL_WIDTH, nextWidth)));
    };

    const cleanup = (endEvent?: PointerEvent) => {
      if (endEvent && endEvent.pointerId !== pointerId) return;
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', cleanup);
      window.removeEventListener('pointercancel', cleanup);
      try {
        if (handle.hasPointerCapture(pointerId)) handle.releasePointerCapture(pointerId);
      } catch {
        // Pointer capture can already be released by the browser on pointercancel.
      }
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      setResizing(false);
      cleanupResizeRef.current = undefined;
    };

    cleanupResizeRef.current = cleanup;
    setResizing(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    window.addEventListener('pointermove', handlePointerMove, { passive: false });
    window.addEventListener('pointerup', cleanup);
    window.addEventListener('pointercancel', cleanup);
  }, []);

  useEffect(() => {
    window.localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(Math.round(panelWidth)));
  }, [panelWidth]);

  return {
    panelWidth,
    resizing,
    handleResizePointerDown,
  };
};

export default useWorkflowInspectorBehavior;
