export type DepartmentScope = 'all' | 'group' | 'leaf';

export interface DepartmentTreeStats {
  total: number;
  groups: number;
  leaves: number;
}
