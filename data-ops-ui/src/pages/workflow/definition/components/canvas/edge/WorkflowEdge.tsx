import { useCallback, useMemo, useState } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  Position,
  getBezierPath,
  type EdgeProps,
} from 'reactflow';
import { WORKFLOW_EDGE_HANDLE_OVERLAP } from '../constants';
import { WORKFLOW_START_NODE_ID } from '../start/types';
import type { WorkflowEdgeData } from '../types';
import WorkflowEdgeInsert from './WorkflowEdgeInsert';

const runtimeStroke = (status?: string) => {
  switch (status) {
    case 'READY':
    case 'SUBMITTED':
    case 'PAUSING':
    case 'PAUSED':
    case 'SUCCESS_WITH_WARNINGS':
    case 'WARNING':
      return '#f79009';
    case 'RUNNING':
    case 'RESUMING':
      return '#6172f3';
    case 'SUCCESS':
      return '#12b76a';
    case 'FAILED':
    case 'UPSTREAM_FAILED':
    case 'TIMED_OUT':
      return '#f04438';
    case 'CANCELED':
    case 'SKIPPED':
      return '#98a2b3';
    default:
      return undefined;
  }
};

const WorkflowEdge = ({
  id,
  source,
  target,
  sourceX,
  sourceY,
  targetX,
  targetY,
  selected,
  data,
}: EdgeProps<WorkflowEdgeData>) => {
  const [hovered, setHovered] = useState(false);
  const [insertOpen, setInsertOpen] = useState(false);
  const isStartEdge = source === WORKFLOW_START_NODE_ID;

  const [edgePath, labelX, labelY] = useMemo(
    () => getBezierPath({
      sourceX: sourceX - WORKFLOW_EDGE_HANDLE_OVERLAP,
      sourceY,
      sourcePosition: Position.Right,
      targetX: targetX + WORKFLOW_EDGE_HANDLE_OVERLAP,
      targetY,
      targetPosition: Position.Left,
      curvature: 0.16,
    }),
    [sourceX, sourceY, targetX, targetY],
  );

  const connectedNodeHovered = Boolean(data?.connectedNodeHovered);
  const highlighted = selected || connectedNodeHovered;
  const executionStroke = runtimeStroke(data?.runtimeStatus);
  const stroke = executionStroke
    || (highlighted
      ? '#6172f3'
      : hovered || insertOpen
        ? '#8a8f99'
        : '#cfd2d7');
  const insertOptions = data?.insertOptions || [];
  const canInsert = !isStartEdge && !data?.locked && insertOptions.length > 0 && Boolean(data?.onInsert);
  const insertVisible = canInsert && (hovered || selected || insertOpen);

  const handleSelect = useCallback((taskId: string) => {
    data?.onInsert?.(id, source, target, taskId);
    setInsertOpen(false);
  }, [data, id, source, target]);

  return (
    <>
      <path
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={18}
        style={{ cursor: canInsert ? 'pointer' : 'default', pointerEvents: 'stroke' }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      />

      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke,
          strokeWidth: executionStroke || highlighted ? 2.2 : 2,
          transition: 'stroke 180ms ease, stroke-width 180ms ease',
        }}
      />

      {canInsert ? (
        <EdgeLabelRenderer>
          <div
            className="nodrag nopan"
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
              pointerEvents: insertVisible ? 'all' : 'none',
              zIndex: 8,
            }}
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => {
              if (!insertOpen) setHovered(false);
            }}
          >
            <WorkflowEdgeInsert
              open={insertOpen}
              visible={insertVisible}
              options={insertOptions}
              onOpenChange={setInsertOpen}
              onSelect={handleSelect}
            />
          </div>
        </EdgeLabelRenderer>
      ) : null}
    </>
  );
};

export default WorkflowEdge;
