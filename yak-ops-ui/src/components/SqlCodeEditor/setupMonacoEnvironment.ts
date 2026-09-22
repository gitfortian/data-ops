type MonacoEnvironment = {
  getWorker?: (_moduleId: string, label: string) => Worker;
};

type MonacoGlobal = typeof globalThis & {
  MonacoEnvironment?: MonacoEnvironment;
};

let initialized = false;

/** Configure Monaco's generic editor worker without overriding an existing setup. */
export const setupMonacoEnvironment = () => {
  if (initialized || typeof window === 'undefined') return;
  initialized = true;

  const monacoGlobal = globalThis as MonacoGlobal;
  if (monacoGlobal.MonacoEnvironment?.getWorker) return;

  monacoGlobal.MonacoEnvironment = {
    ...monacoGlobal.MonacoEnvironment,
    getWorker: () =>
      new Worker(
        new URL('monaco-editor/esm/vs/editor/editor.worker.js', import.meta.url),
        { type: 'module' },
      ),
  };
};
