// Generated from compiled Workflow HTTP DTOs. Run scripts/contracts/workflow-schema.mjs --write.
export type AttemptVO = { "attemptNumber"?: number; "availableAt"?: string; "endedAt"?: string; "errorMessage"?: string; "failureReason"?: string; "id"?: string; "pausedAt"?: string; "pausedMillis"?: number; "startedAt"?: string; "status"?: string; };

export type EdgeDTO = { "source": string; "target": string; };

export type NodeDTO = { "dispatchTimeoutSeconds"?: number; "executionTimeoutSeconds"?: number; "failurePolicy"?: string; "id": string; "inputMapping"?: Record<string, string>; "maxAttempts"?: number; "retryDelaySeconds"?: number; "taskId": string; "triggerRule"?: string; };

export type NodeInstanceVO = { "attemptCount"?: number; "attempts"?: Array<AttemptVO>; "continuedAfterFailure"?: boolean; "currentAttemptId"?: string; "currentAttemptNumber"?: number; "dispatchTimeoutSeconds"?: number; "errorMessage"?: string; "executionTimeoutSeconds"?: number; "failurePolicy"?: string; "failureReason"?: string; "id"?: string; "input"?: Record<string, unknown>; "inputMapping"?: Record<string, string>; "name"?: string; "output"?: Record<string, unknown>; "predecessorOutputs"?: Record<string, Record<string, unknown>>; "retryDelaySeconds"?: number; "retryMaxAttempts"?: number; "status"?: string; "taskId"?: string; "triggerRule"?: string; "type"?: string; };

export type WorkflowInstanceVO = { "creatorName"?: string; "definitionId"?: string; "edgeCount"?: number; "endedAt"?: string; "failureStrategy"?: string; "id"?: string; "input"?: Record<string, unknown>; "name"?: string; "nodeCount"?: number; "nodes"?: Array<NodeInstanceVO>; "runStartedAt"?: string; "sourceExecutionId"?: string; "startedAt"?: string; "status"?: string; "testRun"?: boolean; "workflowTimeoutSeconds"?: number; "workflowVersionId"?: string; "workflowVersionNo"?: number; };

export type WorkflowRunDTO = { "edges"?: Array<EdgeDTO>; "failureStrategy"?: string; "input"?: Record<string, unknown>; "name": string; "nodes": Array<NodeDTO>; "workflowTimeoutSeconds"?: number; };
