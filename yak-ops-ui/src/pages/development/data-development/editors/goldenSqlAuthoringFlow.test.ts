import type { DevelopmentNode, DevelopmentTaskDraft } from '../types';
import { deriveAuthoringState } from '../components/workbench/authoringState';
import {
  getEditorSession,
  markEditorSessionSaved,
  updateEditorSessionContent,
} from './session/editorSessionStore';
import {
  getSqlMetadataContext,
  selectSqlDatabaseContext,
  selectSqlDataSourceContext,
  selectSqlSchemaContext,
} from './sql/metadata/sqlMetadataContextStore';
import {
  hydrateDevelopmentTaskDraft,
  prepareDevelopmentTaskDefinition,
  restoreDevelopmentTaskOriginal,
} from './taskPersistence';

describe('F-002-A Golden SQL authoring flow', () => {
  it('keeps Draft, current editor, Run definition, save state and publish state coherent', () => {
    window.localStorage.clear();

    const node: DevelopmentNode = {
      id: 'golden-sql-authoring-node',
      name: 'Golden SQL Authoring',
      type: 'SQL',
      configured: true,
      pendingPublish: true,
    };
    const originalConfigJson = JSON.stringify({
      dataSourceId: 'source-1',
      database: 'warehouse',
      schema: 'public',
      dbType: 'postgres',
      maxRows: 500,
      timeoutSeconds: 30,
      customRuntimeFlag: true,
    });
    const serverDraft: DevelopmentTaskDraft = {
      nodeId: node.id,
      draftRevision: 7,
      definition: {
        taskType: 'SQL',
        schemaVersion: 1,
        content: 'select * from customer',
        configJson: originalConfigJson,
      },
    };

    // Open an existing Draft. Passive hydration must not manufacture a dirty editor.
    expect(hydrateDevelopmentTaskDraft(node, serverDraft)).toBe(true);
    expect(getEditorSession(node.id)).toMatchObject({
      content: serverDraft.definition.content,
      configJson: originalConfigJson,
      draftRevision: 7,
      dirty: false,
    });

    // Run/prepare without editing must preserve the server Draft byte-for-byte.
    expect(prepareDevelopmentTaskDefinition(node)).toEqual(serverDraft.definition);
    expect(getEditorSession(node.id)?.dirty).toBe(false);
    expect(
      deriveAuthoringState({
        draftRevision: getEditorSession(node.id)?.draftRevision,
        dirty: getEditorSession(node.id)?.dirty,
        pendingPublish: node.pendingPublish,
      }),
    ).toEqual({
      draftRevision: 7,
      saveState: 'saved',
      publishState: 'pending',
    });

    // Current editor content is the Run source of truth even before Draft save.
    const editedSql = 'select id, name from customer where enabled = 1';
    updateEditorSessionContent(node.id, editedSql);
    expect(getEditorSession(node.id)?.dirty).toBe(true);
    expect(prepareDevelopmentTaskDefinition(node).content).toBe(editedSql);
    expect(getEditorSession(node.id)?.draftRevision).toBe(7);

    // A background/re-entry Draft read must never overwrite unsaved local work.
    expect(hydrateDevelopmentTaskDraft(node, serverDraft)).toBe(false);
    expect(getEditorSession(node.id)?.content).toBe(editedSql);

    // Explicit context changes update the same TaskDefinition used by Run and Save,
    // while unrelated runtime options survive.
    selectSqlDataSourceContext(node.id, {
      id: 'source-2',
      name: 'Analytics PostgreSQL',
      dbType: 'postgres',
    });
    selectSqlDatabaseContext(node.id, 'analytics');
    selectSqlSchemaContext(node.id, 'reporting');

    const currentDefinition = prepareDevelopmentTaskDefinition(node);
    expect(currentDefinition.content).toBe(editedSql);
    expect(JSON.parse(currentDefinition.configJson)).toEqual({
      dataSourceId: 'source-2',
      databaseName: 'analytics',
      schemaName: 'reporting',
      dialect: 'POSTGRE_SQL',
      maxRows: 500,
      timeoutSeconds: 30,
      customRuntimeFlag: true,
    });
    expect(getSqlMetadataContext(node.id)).toMatchObject({
      dataSourceId: 'source-2',
      dataSourceName: 'Analytics PostgreSQL',
      database: 'analytics',
      schema: 'reporting',
      dialect: 'POSTGRE_SQL',
    });

    // A successful Draft save advances only Draft truth. Published state stays
    // independent until an explicit Publish action occurs.
    markEditorSessionSaved(node.id, 8);
    expect(getEditorSession(node.id)).toMatchObject({
      draftRevision: 8,
      dirty: false,
      content: editedSql,
      configJson: currentDefinition.configJson,
    });
    expect(
      deriveAuthoringState({
        draftRevision: getEditorSession(node.id)?.draftRevision,
        dirty: getEditorSession(node.id)?.dirty,
        pendingPublish: node.pendingPublish,
      }),
    ).toEqual({
      draftRevision: 8,
      saveState: 'saved',
      publishState: 'pending',
    });

    // Leaving and returning can hydrate the saved Draft without losing SQL context.
    const savedDraft: DevelopmentTaskDraft = {
      nodeId: node.id,
      draftRevision: 8,
      definition: currentDefinition,
    };
    expect(hydrateDevelopmentTaskDraft(node, savedDraft)).toBe(true);
    expect(prepareDevelopmentTaskDefinition(node)).toEqual(currentDefinition);
    expect(getEditorSession(node.id)?.dirty).toBe(false);

    // Discarding a later local edit restores the last saved Draft and its context.
    updateEditorSessionContent(node.id, 'select broken local edit');
    selectSqlDatabaseContext(node.id, 'scratch');
    expect(getEditorSession(node.id)?.dirty).toBe(true);
    restoreDevelopmentTaskOriginal(node);

    expect(getEditorSession(node.id)).toMatchObject({
      draftRevision: 8,
      dirty: false,
      content: editedSql,
      configJson: currentDefinition.configJson,
    });
    expect(getSqlMetadataContext(node.id)).toMatchObject({
      dataSourceId: 'source-2',
      database: 'analytics',
      schema: 'reporting',
      dialect: 'POSTGRE_SQL',
    });
  });
});
