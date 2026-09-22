import type { DevelopmentId } from '../../types';
import type { EditorTabAction } from './EditorTabs';

export const tabActionTargets = (
  action: EditorTabAction,
  openNodeIds: DevelopmentId[],
  activeNodeId?: DevelopmentId,
): DevelopmentId[] => {
  if (!activeNodeId) return [];
  const activeIndex = openNodeIds.indexOf(activeNodeId);
  if (activeIndex < 0) return [];

  if (action === 'close-current') return [activeNodeId];
  if (action === 'close-all') return [...openNodeIds];
  if (action === 'close-others') {
    return openNodeIds.filter((nodeId) => nodeId !== activeNodeId);
  }
  if (action === 'close-left') return openNodeIds.slice(0, activeIndex);
  if (action === 'close-right') return openNodeIds.slice(activeIndex + 1);
  return [];
};

export const closeTabs = (
  openNodeIds: DevelopmentId[],
  activeNodeId: DevelopmentId | undefined,
  nodeIds: DevelopmentId[],
) => {
  const closeSet = new Set(nodeIds);
  const currentIndex = activeNodeId ? openNodeIds.indexOf(activeNodeId) : -1;
  const nextOpenNodeIds = openNodeIds.filter((id) => !closeSet.has(id));

  if (!activeNodeId || !closeSet.has(activeNodeId)) {
    return { nextOpenNodeIds, nextActiveNodeId: activeNodeId };
  }

  const nextActiveNodeId =
    nextOpenNodeIds[Math.min(Math.max(currentIndex, 0), nextOpenNodeIds.length - 1)]
    || nextOpenNodeIds[nextOpenNodeIds.length - 1];
  return { nextOpenNodeIds, nextActiveNodeId };
};
