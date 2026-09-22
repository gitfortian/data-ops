import { useMemo } from 'react';

import type { DevelopmentId, DevelopmentResourceNode } from '../../types';
import DataServiceNodeEditor from '../data-service/DataServiceNodeEditor';
import DatasetNodeEditor from '../dataset/DatasetNodeEditor';

interface StandaloneWorkbenchEditorProps {
  node: DevelopmentResourceNode;
  active: boolean;
  onSaved?: () => void | Promise<void>;
  onDirtyChange: (dirty: boolean) => void;
}

interface DataServiceWorkbenchEditorProps extends StandaloneWorkbenchEditorProps {
  onOpenSourceNode: (nodeId: DevelopmentId) => void;
}

/** Keep standalone resource identity stable when the directory tree refreshes. */
export const DataServiceWorkbenchEditor = ({
  node,
  active,
  onSaved,
  onOpenSourceNode,
  onDirtyChange,
}: DataServiceWorkbenchEditorProps) => {
  const stableNode = useMemo(() => node, [node.id, node.name]);

  return (
    <div
      className={[
        'min-h-0 flex-1 overflow-hidden',
        active ? 'flex' : 'hidden',
      ].join(' ')}
    >
      <DataServiceNodeEditor
        node={stableNode}
        onSaved={onSaved}
        onOpenSourceNode={onOpenSourceNode}
        onDirtyChange={onDirtyChange}
      />
    </div>
  );
};

export const DatasetWorkbenchEditor = ({
  node,
  active,
  onSaved,
  onDirtyChange,
}: StandaloneWorkbenchEditorProps) => {
  const stableNode = useMemo(() => node, [node.id, node.name]);

  return (
    <div
      className={[
        'min-h-0 flex-1 overflow-hidden',
        active ? 'flex' : 'hidden',
      ].join(' ')}
    >
      <DatasetNodeEditor
        node={stableNode}
        onSaved={onSaved}
        onDirtyChange={onDirtyChange}
      />
    </div>
  );
};
