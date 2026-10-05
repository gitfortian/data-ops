/** 审批中心前端类型,与 /api/v1/approvals 后端视图一一对应。 */

export type InstanceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED';

export type StepStatus = 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';

export interface ApprovalPage<T> {
  bizData: T[];
  pagination: { pageNo: number; pageSize: number; total: number; pages?: number };
}

export interface ApprovalInstance {
  id: number;
  flowCode: string;
  flowName: string;
  bizType: string;
  bizId: string;
  title: string;
  payloadJson?: string | null;
  applicant: string;
  status: InstanceStatus;
  currentLevel: number;
  createTime: string;
  finishTime?: string | null;
}

export interface ApprovalTodoRow {
  stepId: number;
  levelNo: number;
  instance: ApprovalInstance;
}

export interface ApprovalHandledRow {
  stepId: number;
  levelNo: number;
  stepStatus: StepStatus;
  comment?: string | null;
  handledTime?: string | null;
  instance: ApprovalInstance;
}

export interface ApprovalStep {
  id: number;
  levelNo: number;
  approver: string;
  status: StepStatus;
  comment?: string | null;
  handledTime?: string | null;
}

export interface ApprovalDetail {
  instance: ApprovalInstance;
  steps: ApprovalStep[];
  cancelReason?: string | null;
}

export interface FlowStepConfig {
  level: number;
  approvers: string[];
}

export interface ApprovalFlow {
  id: number;
  flowCode: string;
  flowName: string;
  description?: string | null;
  steps: FlowStepConfig[];
  enabled: boolean;
  createTime: string;
  updateTime: string;
}

export interface FlowPayload {
  flowCode?: string;
  flowName: string;
  description?: string;
  steps: { approvers: string[] }[];
}
