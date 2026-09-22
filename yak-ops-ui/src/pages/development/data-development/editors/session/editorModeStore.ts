import { useSyncExternalStore } from 'react';

import type { DevelopmentId } from '../../types';

export type EditorMode = 'inline' | 'resource';

const DEFAULT_MODE: EditorMode = 'inline';

const modes = new Map<DevelopmentId, EditorMode>();
const listeners = new Set<() => void>();
let version = 0;

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

const getVersion = () => version;

const emitChange = () => {
  version += 1;
  listeners.forEach((listener) => listener());
};

export const getEditorMode = (nodeId: DevelopmentId): EditorMode =>
  modes.get(nodeId) || DEFAULT_MODE;

export const setEditorMode = (
  nodeId: DevelopmentId,
  mode: EditorMode,
) => {
  if (getEditorMode(nodeId) === mode) return;
  modes.set(nodeId, mode);
  emitChange();
};

export const clearEditorMode = (nodeId: DevelopmentId) => {
  if (!modes.has(nodeId)) return;
  modes.delete(nodeId);
  emitChange();
};

export const useEditorMode = (nodeId: DevelopmentId) => {
  useSyncExternalStore(subscribe, getVersion, getVersion);

  return {
    mode: getEditorMode(nodeId),
    setMode: (mode: EditorMode) => setEditorMode(nodeId, mode),
  };
};
