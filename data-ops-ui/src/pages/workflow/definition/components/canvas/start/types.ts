import type { WorkflowCanvasTaskOption } from '../types';

export const WORKFLOW_START_NODE_ID = '__yak_workflow_start__';
export const WORKFLOW_START_META_KEY = '__yak_start__';

export type WorkflowStartValueType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'FILE' | 'ARRAY_STRING';

export interface WorkflowStartInputField {
  id: string;
  name: string;
  label: string;
  type: WorkflowStartValueType;
  required: boolean;
  description?: string;
  defaultValue?: unknown;
}

export interface WorkflowStartVariable {
  id: string;
  name: string;
  type: Exclude<WorkflowStartValueType, 'FILE'>;
  value?: unknown;
}

export interface WorkflowStartConfig {
  position: { x: number; y: number };
  inputs: WorkflowStartInputField[];
  variables: WorkflowStartVariable[];
  /** Start 的显式后继节点，是编辑器历史状态的一部分。 */
  nextNodeIds: string[];
  /**
   * 编辑器不识别的顶层元数据需要原样透传，避免保存画布时误删图标等公共 UI 元数据。
   */
  editorMetaExtras?: Record<string, unknown>;
}

export interface WorkflowStartNodeData {
  label: string;
  locked?: boolean;
  inputs: WorkflowStartInputField[];
  /** Start 是虚拟编辑器节点，该状态只由当前测试运行前端派生，不进入后端 DAG。 */
  runtimeStatus?: 'RUNNING' | 'SUCCESS';
  appendOptions?: WorkflowCanvasTaskOption[];
  onAppend?: (nodeId: string, taskId: string) => void;
}
