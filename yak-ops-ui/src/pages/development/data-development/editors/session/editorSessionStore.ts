import { useSyncExternalStore } from 'react';

import type {
  DevelopmentId,
  DevelopmentTaskType,
} from '../../types';
import type {
  DevelopmentEditorSelection,
  DevelopmentEditorSession,
  DevelopmentEditorViewState,
} from './types';

const STORAGE_KEY = 'yak-data-development.editor-sessions.v1';
const PERSIST_DELAY = 200;
const TASK_TYPES = new Set<DevelopmentTaskType>([
  'SQL',
  'SHELL',
  'HTTP',
  'PYTHON',
  'JAVA',
]);

interface PersistedEditorSessions {
  version: 1;
  sessions: DevelopmentEditorSession[];
}

const sessions = new Map<DevelopmentId, DevelopmentEditorSession>();
const listeners = new Set<() => void>();

let hydrated = false;
let version = 0;
let persistTimer: number | undefined;
let pageHideBound = false;

const isBrowser = () => typeof window !== 'undefined';
const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value);

const normalizedConfigJson = (value?: string) => value || '{}';

const calculateDirty = (session: DevelopmentEditorSession) =>
  session.content !== session.originalContent ||
  normalizedConfigJson(session.configJson) !==
    normalizedConfigJson(session.originalConfigJson);

const isSelection = (value: unknown): value is DevelopmentEditorSelection => {
  if (!value || typeof value !== 'object') return false;
  const selection = value as Partial<DevelopmentEditorSelection>;
  return (
    isFiniteNumber(selection.startLineNumber) &&
    isFiniteNumber(selection.startColumn) &&
    isFiniteNumber(selection.endLineNumber) &&
    isFiniteNumber(selection.endColumn)
  );
};

const isViewState = (value: unknown): value is DevelopmentEditorViewState => {
  if (!value || typeof value !== 'object') return false;
  const viewState = value as Partial<DevelopmentEditorViewState>;
  return (
    isFiniteNumber(viewState.lineNumber) &&
    isFiniteNumber(viewState.column) &&
    isFiniteNumber(viewState.scrollTop) &&
    isFiniteNumber(viewState.scrollLeft) &&
    (viewState.selection === undefined || isSelection(viewState.selection))
  );
};

const sameViewState = (
  left?: DevelopmentEditorViewState,
  right?: DevelopmentEditorViewState,
) => {
  if (left === right) return true;
  if (!left || !right) return false;

  const leftSelection = left.selection;
  const rightSelection = right.selection;

  return (
    left.lineNumber === right.lineNumber &&
    left.column === right.column &&
    left.scrollTop === right.scrollTop &&
    left.scrollLeft === right.scrollLeft &&
    leftSelection?.startLineNumber === rightSelection?.startLineNumber &&
    leftSelection?.startColumn === rightSelection?.startColumn &&
    leftSelection?.endLineNumber === rightSelection?.endLineNumber &&
    leftSelection?.endColumn === rightSelection?.endColumn
  );
};

const persistNow = () => {
  if (!isBrowser()) return;
  if (persistTimer !== undefined) {
    window.clearTimeout(persistTimer);
    persistTimer = undefined;
  }

  const payload: PersistedEditorSessions = {
    version: 1,
    sessions: [...sessions.values()],
  };

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Local storage can be unavailable or full. The in-memory session still works.
  }
};

const schedulePersist = () => {
  if (!isBrowser()) return;
  if (persistTimer !== undefined) window.clearTimeout(persistTimer);
  persistTimer = window.setTimeout(persistNow, PERSIST_DELAY);
};

const bindPageHide = () => {
  if (!isBrowser() || pageHideBound) return;
  pageHideBound = true;
  window.addEventListener('pagehide', persistNow);
};

const isPersistedSession = (
  value: unknown,
): value is DevelopmentEditorSession => {
  if (!value || typeof value !== 'object') return false;
  const session = value as Partial<DevelopmentEditorSession>;

  return (
    typeof session.nodeId === 'string' &&
    typeof session.nodeType === 'string' &&
    TASK_TYPES.has(session.nodeType as DevelopmentTaskType) &&
    typeof session.content === 'string' &&
    typeof session.originalContent === 'string' &&
    typeof session.dirty === 'boolean' &&
    isFiniteNumber(session.updatedAt) &&
    (session.schemaVersion === undefined || isFiniteNumber(session.schemaVersion)) &&
    (session.configJson === undefined || typeof session.configJson === 'string') &&
    (session.originalConfigJson === undefined ||
      typeof session.originalConfigJson === 'string') &&
    (session.draftRevision === undefined || isFiniteNumber(session.draftRevision)) &&
    (session.viewState === undefined || isViewState(session.viewState))
  );
};

const ensureHydrated = () => {
  if (hydrated) return;
  hydrated = true;
  bindPageHide();

  if (!isBrowser()) return;

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return;

    const parsed = JSON.parse(raw) as Partial<PersistedEditorSessions>;
    if (parsed.version !== 1 || !Array.isArray(parsed.sessions)) return;

    parsed.sessions.forEach((session) => {
      if (!isPersistedSession(session)) return;
      const normalized: DevelopmentEditorSession = {
        ...session,
        schemaVersion: session.schemaVersion || 1,
        configJson: normalizedConfigJson(session.configJson),
        originalConfigJson: normalizedConfigJson(session.originalConfigJson),
      };
      sessions.set(session.nodeId, {
        ...normalized,
        dirty: calculateDirty(normalized),
      });
    });
  } catch {
    // Ignore malformed or unavailable persisted state and start with a clean store.
  }
};

const emitChange = () => {
  version += 1;
  listeners.forEach((listener) => listener());
};

const subscribe = (listener: () => void) => {
  ensureHydrated();
  listeners.add(listener);
  return () => listeners.delete(listener);
};

const getVersion = () => {
  ensureHydrated();
  return version;
};

export const ensureEditorSession = (
  nodeId: DevelopmentId,
  nodeType: DevelopmentTaskType,
  initialContent = '',
  initialConfigJson = '{}',
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (current) return current;

  const session: DevelopmentEditorSession = {
    nodeId,
    nodeType,
    schemaVersion: 1,
    content: initialContent,
    originalContent: initialContent,
    configJson: initialConfigJson,
    originalConfigJson: initialConfigJson,
    draftRevision: 0,
    dirty: false,
    updatedAt: Date.now(),
  };
  sessions.set(nodeId, session);
  return session;
};

export const getEditorSession = (nodeId: DevelopmentId) => {
  ensureHydrated();
  return sessions.get(nodeId);
};

export const hydrateEditorSession = (
  nodeId: DevelopmentId,
  nodeType: DevelopmentTaskType,
  schemaVersion: number,
  content: string,
  configJson: string,
  draftRevision: number,
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (current?.dirty) return current;

  const next: DevelopmentEditorSession = {
    ...(current || {}),
    nodeId,
    nodeType,
    schemaVersion,
    content,
    originalContent: content,
    configJson,
    originalConfigJson: configJson,
    draftRevision,
    dirty: false,
    updatedAt: Date.now(),
  };
  sessions.set(nodeId, next);
  schedulePersist();
  emitChange();
  return next;
};

export const updateEditorSessionContent = (
  nodeId: DevelopmentId,
  content: string,
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current || current.content === content) return;

  const next: DevelopmentEditorSession = {
    ...current,
    content,
    updatedAt: Date.now(),
  };
  sessions.set(nodeId, {
    ...next,
    dirty: calculateDirty(next),
  });
  schedulePersist();
  emitChange();
};

export const updateEditorSessionConfig = (
  nodeId: DevelopmentId,
  configJson: string,
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current || normalizedConfigJson(current.configJson) === normalizedConfigJson(configJson)) {
    return;
  }

  const next: DevelopmentEditorSession = {
    ...current,
    configJson: normalizedConfigJson(configJson),
    updatedAt: Date.now(),
  };
  sessions.set(nodeId, {
    ...next,
    dirty: calculateDirty(next),
  });
  schedulePersist();
  emitChange();
};

export const updateEditorSessionViewState = (
  nodeId: DevelopmentId,
  viewState: DevelopmentEditorViewState,
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current || sameViewState(current.viewState, viewState)) return;

  sessions.set(nodeId, {
    ...current,
    viewState,
    updatedAt: Date.now(),
  });
  schedulePersist();
};

export const markEditorSessionSaved = (
  nodeId: DevelopmentId,
  draftRevision?: number,
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current) return;

  sessions.set(nodeId, {
    ...current,
    originalContent: current.content,
    configJson: normalizedConfigJson(current.configJson),
    originalConfigJson: normalizedConfigJson(current.configJson),
    draftRevision: draftRevision ?? current.draftRevision,
    dirty: false,
    updatedAt: Date.now(),
  });
  schedulePersist();
  emitChange();
};

export const restoreEditorSessionOriginal = (nodeId: DevelopmentId) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current) return undefined;

  const restored: DevelopmentEditorSession = {
    ...current,
    content: current.originalContent,
    configJson: normalizedConfigJson(current.originalConfigJson),
    dirty: false,
    updatedAt: Date.now(),
  };
  sessions.set(nodeId, restored);
  schedulePersist();
  emitChange();
  return restored;
};

export const useEditorSession = (
  nodeId: DevelopmentId,
  nodeType: DevelopmentTaskType,
  initialContent = '',
) => {
  useSyncExternalStore(subscribe, getVersion, getVersion);
  return ensureEditorSession(nodeId, nodeType, initialContent);
};

export const useEditorSessionVersion = () =>
  useSyncExternalStore(subscribe, getVersion, getVersion);
