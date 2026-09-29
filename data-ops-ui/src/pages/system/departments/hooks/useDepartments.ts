import { message } from 'antd';
import type { Key } from 'react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import {
  getDepartmentDetail,
  getDepartmentTree,
  type DepartmentVO,
} from '@/services/security/departments';

import { getSystemErrorMessage } from '../../utils';
import {
  collectDepartmentIds,
  filterDepartmentTree,
  findDepartmentById,
  findDepartmentPath,
  getDepartmentForest,
  getDepartmentTreeStats,
  getDirectChildren,
} from '../tree';
import type { DepartmentScope } from '../types';

export function useDepartments() {
  const requestSequenceRef = useRef(0);
  const detailSequenceRef = useRef(0);
  const [root, setRoot] = useState<DepartmentVO>();
  const [selectedId, setSelectedId] = useState<number>();
  const [detail, setDetail] = useState<DepartmentVO>();
  const [keyword, setKeyword] = useState('');
  const [scope, setScope] = useState<DepartmentScope>('all');
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isDetailLoading, setIsDetailLoading] = useState(false);

  const reloadDepartments = useCallback(async () => {
    const sequence = ++requestSequenceRef.current;
    setIsLoading(true);

    try {
      const value = await getDepartmentTree();
      if (sequence !== requestSequenceRef.current) return;
      setRoot(value);
    } catch (error) {
      if (sequence !== requestSequenceRef.current) return;
      setRoot(undefined);
      message.error(
        getSystemErrorMessage(error, '部门树加载失败'),
      );
    } finally {
      if (sequence === requestSequenceRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void reloadDepartments();
  }, [reloadDepartments]);

  const departmentForest = useMemo(
    () => getDepartmentForest(root),
    [root],
  );
  const stats = useMemo(
    () => getDepartmentTreeStats(departmentForest),
    [departmentForest],
  );
  const visibleDepartments = useMemo(
    () => filterDepartmentTree(departmentForest, keyword, scope),
    [departmentForest, keyword, scope],
  );
  const selectedTreeDepartment = useMemo(
    () => findDepartmentById(departmentForest, selectedId),
    [departmentForest, selectedId],
  );
  const selectedDepartment = useMemo(() => {
    if (!selectedTreeDepartment) return undefined;
    if (!detail || detail.id !== selectedTreeDepartment.id) {
      return selectedTreeDepartment;
    }

    return {
      ...selectedTreeDepartment,
      ...detail,
      childList: selectedTreeDepartment.childList,
    };
  }, [detail, selectedTreeDepartment]);
  const selectedPath = useMemo(
    () => findDepartmentPath(departmentForest, selectedId),
    [departmentForest, selectedId],
  );
  const selectedChildren = useMemo(
    () => getDirectChildren(selectedTreeDepartment),
    [selectedTreeDepartment],
  );
  const descendantCount = useMemo(
    () => collectDepartmentIds(selectedChildren).length,
    [selectedChildren],
  );
  const isFiltered = Boolean(keyword.trim() || scope !== 'all');

  useEffect(() => {
    if (isLoading) return;
    if (findDepartmentById(visibleDepartments, selectedId)) return;
    setSelectedId(visibleDepartments[0]?.id);
  }, [isLoading, selectedId, visibleDepartments]);

  useEffect(() => {
    if (isFiltered) {
      setExpandedKeys(collectDepartmentIds(visibleDepartments));
      return;
    }
    setExpandedKeys(
      departmentForest.map((department) => department.id),
    );
  }, [departmentForest, isFiltered, visibleDepartments]);

  useEffect(() => {
    if (selectedId === undefined) {
      setDetail(undefined);
      return;
    }

    const sequence = ++detailSequenceRef.current;
    setIsDetailLoading(true);

    void getDepartmentDetail(selectedId)
      .then((value) => {
        if (sequence === detailSequenceRef.current) {
          setDetail(value);
        }
      })
      .catch((error) => {
        if (sequence === detailSequenceRef.current) {
          setDetail(undefined);
          message.error(
            getSystemErrorMessage(error, '部门详情加载失败'),
          );
        }
      })
      .finally(() => {
        if (sequence === detailSequenceRef.current) {
          setIsDetailLoading(false);
        }
      });
  }, [selectedId]);

  return {
    root,
    keyword,
    setKeyword,
    scope,
    setScope,
    stats,
    visibleDepartments,
    selectedId,
    setSelectedId,
    selectedTreeDepartment,
    selectedDepartment,
    selectedPath,
    selectedChildren,
    descendantCount,
    expandedKeys,
    setExpandedKeys,
    isFiltered,
    isLoading,
    isDetailLoading,
    reloadDepartments,
  };
}
