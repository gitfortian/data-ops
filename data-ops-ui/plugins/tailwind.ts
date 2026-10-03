import { execFile, spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import type { IApi } from '@umijs/max';

/** Run the Tailwind JS CLI through Node on every platform, including Windows. */
export default (api: IApi) => {
  const output = 'plugin-tailwindcss/tailwind.css';
  api.onBeforeCompiler(async () => {
    if (process.env.IS_UMI_BUILD_WORKER) return;
    const generatedPath = path.join(api.paths.absTmpPath, output);
    const cli = require.resolve('tailwindcss/lib/cli.js');
    const args = [cli, '-c', path.join(api.cwd, 'tailwind.config.js'),
      '-i', path.join(api.cwd, 'tailwind.css'), '-o', generatedPath];
    await mkdir(path.dirname(generatedPath), { recursive: true });
    // Await a successful compile; a stale or empty file must never signal readiness.
    await promisify(execFile)(process.execPath, args, { cwd: api.cwd, windowsHide: true });
    if (api.env === 'development') {
      const watcher = spawn(process.execPath, [...args, '--watch'], {
        cwd: api.cwd, windowsHide: true, stdio: 'inherit',
      });
      watcher.on('error', (error) => api.logger.error(error.message));
      process.once('exit', () => watcher.kill());
    }
  });
  api.addEntryImports(() => [{
    source: path.join(api.paths.absTmpPath, output).replace(/\\/g, '/'),
  }]);
};
