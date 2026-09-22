import type { DevelopmentId } from '../../../types';

export type SqlEditorCommand =
  | 'undo'
  | 'redo'
  | 'find'
  | 'format'
  | 'suggest'
  | 'toggle-word-wrap'
  | 'toggle-minimap';

type SqlEditorCommandHandler = (
  command: SqlEditorCommand,
) => void | Promise<void>;

const commandHandlers = new Map<DevelopmentId, SqlEditorCommandHandler>();

export const registerSqlEditorCommandHandler = (
  nodeId: DevelopmentId,
  handler: SqlEditorCommandHandler,
) => {
  commandHandlers.set(nodeId, handler);

  return {
    dispose: () => {
      if (commandHandlers.get(nodeId) === handler) {
        commandHandlers.delete(nodeId);
      }
    },
  };
};

export const executeSqlEditorCommand = (
  nodeId: DevelopmentId,
  command: SqlEditorCommand,
) => {
  const handler = commandHandlers.get(nodeId);
  if (!handler) return false;

  void handler(command);
  return true;
};
