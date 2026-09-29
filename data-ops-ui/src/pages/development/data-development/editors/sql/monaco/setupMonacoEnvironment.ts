type MonacoEnvironment = {
  getWorker?: (_moduleId: string, label: string) => Worker;
};

type MonacoGlobal = typeof globalThis & {
  MonacoEnvironment?: MonacoEnvironment;
};

let initialized = false;

/**
 * Configure the worker used by Monaco's editor core.
 *
 * SQL currently only needs the generic editor worker. Language-specific workers
 * can be routed here later when JSON / TypeScript editors are introduced.
 */
export const setupMonacoEnvironment = () => {
  if (initialized || typeof window === 'undefined') return;
  initialized = true;

  const monacoGlobal = globalThis as MonacoGlobal;
  monacoGlobal.MonacoEnvironment = {
    ...monacoGlobal.MonacoEnvironment,
    getWorker: () =>
      new Worker(
        new URL(
          'monaco-editor/esm/vs/editor/editor.worker.js',
          import.meta.url,
        ),
        { type: 'module' },
      ),
  };
};
