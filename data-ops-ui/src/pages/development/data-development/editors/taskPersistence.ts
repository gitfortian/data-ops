import type {
  DevelopmentNode,
  DevelopmentTaskDefinition,
  DevelopmentTaskDraft,
} from '../types';
import {
  ensureEditorSession,
  getEditorSession,
  hydrateEditorSession,
  restoreEditorSessionOriginal,
  updateEditorSessionConfig,
} from './session/editorSessionStore';
import { getEditorMode } from './session/editorModeStore';
import {
  getSqlTaskConfigJson,
  hydrateSqlTaskConfig,
} from './sql/metadata/sqlMetadataContextStore';

/** Fields that represent a resource reference and must be stripped in inline mode. */
const RESOURCE_FIELDS = ['resourceId', 'resourceName', 'resourceVersion', 'checksum'] as const;

/** Returns a configJson with resource-reference fields removed. */
const stripResourceFields = (configJson: string): string => {
  try {
    const obj = JSON.parse(configJson) as Record<string, unknown>;
    let changed = false;
    for (const field of RESOURCE_FIELDS) {
      if (field in obj) { delete obj[field]; changed = true; }
    }
    return changed ? JSON.stringify(obj) : configJson;
  } catch {
    return configJson;
  }
};

/** Maps editor-local state into the plugin-neutral TaskDefinition envelope. */
export const prepareDevelopmentTaskDefinition = (
  node: DevelopmentNode,
): DevelopmentTaskDefinition => {
  ensureEditorSession(node.id, node.type);

  if (node.type === 'SQL') {
    updateEditorSessionConfig(node.id, getSqlTaskConfigJson(node.id));
  }

  const session = getEditorSession(node.id) || ensureEditorSession(node.id, node.type);

  let content = session.content;
  let configJson = session.configJson || '{}';

  // Script editors (Shell/Python) register their mode so we can apply
  // mutual exclusion between inline content and resource reference.
  const mode = getEditorMode(node.id);
  if (mode === 'resource') {
    content = '';
  } else if (mode === 'inline') {
    configJson = stripResourceFields(configJson);
  }

  return {
    taskType: node.type,
    schemaVersion: session.schemaVersion || 1,
    content,
    configJson,
  };
};

/** Hydrates a server draft unless a local unsaved session must be preserved. */
export const hydrateDevelopmentTaskDraft = (
  node: DevelopmentNode,
  draft: DevelopmentTaskDraft,
) => {
  const current = getEditorSession(node.id);
  if (current?.dirty) return false;

  hydrateEditorSession(
    node.id,
    node.type,
    draft.definition.schemaVersion,
    draft.definition.content || '',
    draft.definition.configJson || '{}',
    draft.draftRevision,
  );

  if (node.type === 'SQL') {
    hydrateSqlTaskConfig(node.id, draft.definition.configJson || '{}');
  }
  return true;
};

export const restoreDevelopmentTaskOriginal = (node: DevelopmentNode) => {
  const restored = restoreEditorSessionOriginal(node.id);
  if (node.type === 'SQL' && restored) {
    hydrateSqlTaskConfig(node.id, restored.originalConfigJson || '{}');
  }
};
