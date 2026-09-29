import type {
  DepartmentImportItem,
  DepartmentVO,
} from '@/services/security/departments';

export const getDepartmentName = (
  department?: DepartmentVO,
): string => department?.deptName || '未命名部门';

const isRecord = (
  value: unknown,
): value is Record<string, unknown> =>
  typeof value === 'object' &&
  value !== null &&
  !Array.isArray(value);

const parseDepartmentNode = (
  value: unknown,
  path: string,
): DepartmentImportItem => {
  if (!isRecord(value)) {
    throw new Error(`${path} 必须是 JSON 对象`);
  }

  const deptName =
    typeof value.deptName === 'string'
      ? value.deptName.trim()
      : '';
  if (!deptName) {
    throw new Error(`${path} 缺少部门名称 deptName`);
  }

  if (
    value.description !== undefined &&
    typeof value.description !== 'string'
  ) {
    throw new Error(`${path}.description 必须是字符串`);
  }

  const childValue = value.childDeptDTOList;
  if (childValue !== undefined && !Array.isArray(childValue)) {
    throw new Error(`${path}.childDeptDTOList 必须是数组`);
  }

  const children = (childValue ?? []).map((child, index) =>
    parseDepartmentNode(
      child,
      `${path}.childDeptDTOList[${index}]`,
    ),
  );

  return {
    deptName,
    ...(typeof value.description === 'string' &&
    value.description.trim()
      ? { description: value.description.trim() }
      : {}),
    ...(children.length > 0
      ? { childDeptDTOList: children }
      : {}),
  };
};

export const parseDepartmentImportJson = (
  source: string,
): DepartmentImportItem[] => {
  let value: unknown;

  try {
    value = JSON.parse(source);
  } catch {
    throw new Error('JSON 格式不正确，请检查逗号、引号和括号');
  }

  if (!Array.isArray(value) || value.length === 0) {
    throw new Error('顶层必须是非空 JSON 数组');
  }

  return value.map((node, index) =>
    parseDepartmentNode(node, `[${index}]`),
  );
};

export const countDepartmentImportNodes = (
  nodes: DepartmentImportItem[],
): number =>
  nodes.reduce(
    (total, node) =>
      total +
      1 +
      countDepartmentImportNodes(node.childDeptDTOList ?? []),
    0,
  );
