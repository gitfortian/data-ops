import { useMemo, useState } from 'react';

import type { DevelopmentId, DevelopmentResourceNode } from '../../types';
import DataServiceDeliveryTruthBar from '../data-service/DataServiceDeliveryTruthBar';
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
  const [deliveryRefreshKey, setDeliveryRefreshKey] = useState(0);

  const handleSaved = async () => {
    setDeliveryRefreshKey((value) => value + 1);
    await onSaved?.();
  };

  return (
    <div
      className={[
        'min-h-0 flex-1 flex-col overflow-hidden',
        active ? 'flex' : 'hidden',
      ].join(' ')}
    >
      <DataServiceDeliveryTruthBar
        nodeId={stableNode.id}
        refreshKey={deliveryRefreshKey}
      />
      <div className="flex min-h-0 flex-1 overflow-hidden">
        <DataServiceNodeEditor
          node={stableNode}
          onSaved={handleSaved}
          onOpenSourceNode={onOpenSourceNode}
          onDirtyChange={onDirtyChange}
        />
      </div>
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