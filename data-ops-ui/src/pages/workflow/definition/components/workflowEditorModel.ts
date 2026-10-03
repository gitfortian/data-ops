import type {
  WorkflowFailureStrategy,
  WorkflowTaskDefinition,
} from "@/services/workflow";
import type { Edge, Node } from "reactflow";
import type {
  WorkflowNoteData,
  WorkflowNoteSnapshot,
} from "./canvas/note/types";
import type { WorkflowStartConfig } from "./canvas/start/types";
import type { WorkflowEdgeData, WorkflowNodeData } from "./canvas/types";

export const START_EDGE_PREFIX = "edge-start-";
export const RUNNING_NODE_STATUSES = new Set([
  "SUBMITTED",
  "RUNNING",
  "RESUMING",
]);

export const parseObject = <T extends Record<string, unknown>>(
  raw: string,
  label: string
): T => {
  const value = raw.trim() ? JSON.parse(raw) : {};
  if (!value || Array.isArray(value) || typeof value !== "object") {
    throw new Error(`${label}必须是 JSON 对象`);
  }
  return value as T;
};

export const taskTypeLabel = (taskType?: string) => {
  if (!taskType || taskType === "SYNC") return "数据同步";
  if (taskType === "SQL") return "SQL";
  return taskType;
};

export const createNodeData = (
  task: WorkflowTaskDefinition
): WorkflowNodeData => ({
  label: task.name,
  taskId: task.id,
  taskType: task.type,
  typeLabel: taskTypeLabel(task.type),
  triggerRule: "ALL_SUCCESS",
  failurePolicy: "FAIL_WORKFLOW",
  maxAttempts: 1,
  retryDelaySeconds: 0,
  dispatchTimeoutSeconds: 0,
  executionTimeoutSeconds: 0,
  inputMappingText: "{}",
});

export const DEFAULT_START_CONFIG: WorkflowStartConfig = {
  position: { x: 80, y: 160 },
  inputs: [],
  variables: [],
  nextNodeIds: [],
};

export const createNoteNode = (
  snapshot: WorkflowNoteSnapshot
): Node<WorkflowNoteData> => ({
  id: snapshot.id,
  type: "note",
  position: { ...snapshot.position },
  selected: false,
  style: { width: snapshot.width, height: snapshot.height },
  data: { text: snapshot.text, theme: snapshot.theme },
});

export const numericSize = (value: unknown, fallback: number) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export const toNoteSnapshot = (
  node: Node<WorkflowNoteData>
): WorkflowNoteSnapshot => ({
  id: node.id,
  position: { ...node.position },
  width: Math.max(240, numericSize(node.width ?? node.style?.width, 240)),
  height: Math.max(88, numericSize(node.height ?? node.style?.height, 88)),
  text: node.data.text,
  theme: node.data.theme,
});

export interface WorkflowEditorHistorySnapshot {
  nodes: Array<Node<WorkflowNodeData>>;
  noteNodes: Array<Node<WorkflowNoteData>>;
  edges: Array<Edge<WorkflowEdgeData>>;
  startConfig: WorkflowStartConfig;
  workflowName: string;
  workflowDescription: string;
  workflowTimeoutSeconds: number;
  failureStrategy: WorkflowFailureStrategy;
}
