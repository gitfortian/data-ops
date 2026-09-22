import { message } from 'antd';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  pageRoles,
  type SystemRole,
} from '@/services/security/roles';

import { getSystemErrorMessage } from '../../utils';
import { DEFAULT_ROLE_PAGINATION } from '../constants';
import type {
  RoleFilterValues,
  RolePaginationState,
} from '../types';

export function useRoles() {
  const requestSequenceRef = useRef(0);
  const [roles, setRoles] = useState<SystemRole[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [filters, setFilters] = useState<RoleFilterValues>({});
  const [pagination, setPagination] =
    useState<RolePaginationState>(DEFAULT_ROLE_PAGINATION);

  const loadRoles = useCallback(async () => {
    const sequence = ++requestSequenceRef.current;
    setIsLoading(true);

    try {
      const result = await pageRoles({
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        ...filters,
      });
      if (sequence !== requestSequenceRef.current) return;

      setRoles(result.records ?? []);
      setPagination((current) => ({
        ...current,
        total: result.total ?? 0,
      }));
    } catch (error) {
      if (sequence !== requestSequenceRef.current) return;

      setRoles([]);
      setPagination((current) => ({ ...current, total: 0 }));
      message.error(getSystemErrorMessage(error, '角色列表加载失败'));
    } finally {
      if (sequence === requestSequenceRef.current) {
        setIsLoading(false);
      }
    }
  }, [filters, pagination.current, pagination.pageSize]);

  useEffect(() => {
    void loadRoles();
  }, [loadRoles]);

  const refreshRoles = useCallback(() => {
    void loadRoles();
  }, [loadRoles]);

  const searchRoles = useCallback((values: RoleFilterValues) => {
    setFilters(values);
    setPagination((current) => ({ ...current, current: 1 }));
  }, []);

  const changePage = useCallback(
    (current: number, pageSize: number) => {
      setPagination((previous) => ({
        ...previous,
        current:
          previous.pageSize === pageSize ? current : 1,
        pageSize,
      }));
    },
    [],
  );

  return {
    roles,
    isLoading,
    pagination,
    refreshRoles,
    searchRoles,
    changePage,
  };
}
