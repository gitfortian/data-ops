import type {
  DevelopmentNode,
  DevelopmentNodeType,
  DevelopmentOutputNodeType,
  DevelopmentResourceNode,
  DevelopmentTaskType,
} from './types';

/** High-level responsibility of a standalone data-development node. */
export type NodeCategory = 'PROCESSING' | 'OUTPUT';
export type NodeType = DevelopmentNodeType;
export type { DevelopmentOutputNodeType };

export const NODE_CATEGORY_BY_TYPE = {
  SQL: 'PROCESSING',
  SHELL: 'PROCESSING',
  HTTP: 'PROCESSING',
  PYTHON: 'PROCESSING',
  JAVA: 'PROCESSING',
  DATASET: 'OUTPUT',
  DATA_SERVICE: 'OUTPUT',
} as const satisfies Record<NodeType, NodeCategory>;

const TASK_NODE_TYPES = new Set<NodeType>(['SQL', 'SHELL', 'HTTP', 'PYTHON', 'JAVA']);

export const getNodeCategory = (type: NodeType): NodeCategory => NODE_CATEGORY_BY_TYPE[type];

export const isDevelopmentTaskNodeType = (type: NodeType): type is DevelopmentTaskType =>
  TASK_NODE_TYPES.has(type);

export const isDevelopmentTaskNode = (
  node: DevelopmentResourceNode,
): node is DevelopmentNode => isDevelopmentTaskNodeType(node.type);
