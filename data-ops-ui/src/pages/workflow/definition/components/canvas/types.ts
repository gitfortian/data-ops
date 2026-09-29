import type {
  WorkflowNodeFailurePolicy,
  WorkflowTriggerRule,
} from '@/services/workflow';

export interface WorkflowCanvasTaskOption {
  id: string;
  label: string;
  typeLabel: string;
  taskType?: string;
  meta?: string;
  taskAssetId?: string;
  taskRevisionId?: string;
  taskRevisionNo?: number;
}

/**
 * 测试运行时由 WorkflowInstance/SSE 派生的瞬时状态。
 * 该对象只挂在 ReactFlow 渲染节点上，不进入草稿、Undo/Redo 或发布版本。
 */
export interface WorkflowNodeRuntimeState {
  status: string;
  errorMessage?: string;
  failureReason?: string;
  attemptCount: number;
  currentAttemptNumber?: number;
  startedAt?: string;
  endedAt?: string;
  elapsedMillis?: number;
}

export interface WorkflowNodeData {
  label: string;
  taskId: string;
  taskType: string;
  typeLabel: string;
  taskAssetId?: string;
  taskRevisionId?: string;
  taskRevisionNo?: number;
  taskAssetStatus?: string;
  latestTaskRevisionId?: string;
  latestTaskRevisionNo?: number;
  taskRevisionUpdateAvailable?: boolean;
  triggerRule: WorkflowTriggerRule;
  failurePolicy: WorkflowNodeFailurePolicy;
  maxAttempts: number;
  retryDelaySeconds: number;
  dispatchTimeoutSeconds: number;
  executionTimeoutSeconds: number;
  inputMappingText: string;
  /** 仅用于画布实时运行态，不持久化。 */
  runtime?: WorkflowNodeRuntimeState;
  locked?: boolean;
  appendOptions?: WorkflowCanvasTaskOption[];
  onAppend?: (nodeId: string, taskId: string) => void;
  onDuplicate?: (nodeId: string) => void;
  onDelete?: (nodeId: string) => void;
}

export type WorkflowEdgeInsertOption = WorkflowCanvasTaskOption;

export interface WorkflowEdgeData {
  locked?: boolean;
  connectedNodeHovered?: boolean;
  /** 目标节点实时运行状态，用于测试运行时高亮执行路径。 */
  runtimeStatus?: string;
  insertOptions?: WorkflowCanvasTaskOption[];
  onInsert?: (
    edgeId: string,
    source: string,
    target: string,
    taskId: string,
  ) => void;
}
