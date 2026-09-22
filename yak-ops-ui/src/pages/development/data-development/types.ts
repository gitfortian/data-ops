import type { ApiResponse } from '@/services/http/response';
import type {
  DevelopmentId,
  DevelopmentNodeType,
  DevelopmentSqlDialect,
} from '@/services/data-development';
import type { DataNode } from 'antd/es/tree';

export type * from '@/services/data-development';
export type { ApiResponse };

export type DevelopmentTreeNodeKey =
  | `directory:${string}`
  | `node:${string}`;
export type DevelopmentTreeNodeType = 'directory' | 'node';
export type DevelopmentNodeCreateType = DevelopmentNodeType;

export type DevelopmentTreeAction =
  | 'create-directory'
  | 'create-sql'
  | `create-sql-dialect:${DevelopmentSqlDialect}`
  | 'create-shell'
  | 'create-python'
  | 'create-java'
  | 'create-dataset'
  | 'create-data-service'
  | 'copy-name'
  | 'copy-path'
  | 'rename'
  | 'move'
  | 'delete';

export interface DevelopmentTreeNode extends DataNode {
  key: DevelopmentTreeNodeKey;
  title: string;
  nodeType: DevelopmentTreeNodeType;
  resourceId: DevelopmentId;
  resourcePath: string;
  taskType?: DevelopmentNodeType;
  sqlDialect?: DevelopmentSqlDialect;
  searchText?: string;
  updatedBy?: string | null;
  updateTime?: string;
  pendingPublish?: boolean;
  children?: DevelopmentTreeNode[];
}
