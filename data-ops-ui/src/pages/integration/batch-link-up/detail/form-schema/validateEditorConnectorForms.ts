import { getOfflineSyncConnectorRuntime } from '@/services/batch-link-up';

import { validateEditorCapabilities } from '../capabilities';
import type { SyncEditorState } from '../model';

/**
 * Guided offline-sync pages expose only Yak Ops product fields.
 * Connector task options stay internal and use connector/adapter defaults, so save-time validation
 * checks runtime availability and declared capabilities instead of requiring hidden schema fields.
 */
export default async function validateEditorConnectorForms(
  editor: SyncEditorState,
): Promise<string[]> {
  let runtime;

  try {
    runtime = await getOfflineSyncConnectorRuntime();
  } catch (error: any) {
    return [
      error?.message || '无法读取 Link-Up Connector 能力，请稍后重试',
    ];
  }

  return validateEditorCapabilities(editor, runtime);
}
