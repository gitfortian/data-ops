import { message } from 'antd';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  pageUsers,
  type SystemUser,
} from '@/services/security/users';

import { getSystemErrorMessage } from '../../utils';
import { DEFAULT_USER_PAGINATION } from '../constants';
import type {
  UserFilterValues,
  UserPaginationState,
} from '../types';

export function useUsers() {
  const requestSequenceRef = useRef(0);
  const [users, setUsers] = useState<SystemUser[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [filters, setFilters] = useState<UserFilterValues>({});
  const [pagination, setPagination] =
    useState<UserPaginationState>(DEFAULT_USER_PAGINATION);

  const loadUsers = useCallback(async () => {
    const requestSequence = ++requestSequenceRef.current;
    setIsLoading(true);

    try {
      const result = await pageUsers({
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        ...filters,
      });

      if (requestSequence !== requestSequenceRef.current) return;

      setUsers(result.records ?? []);
      setPagination((current) => ({
        ...current,
        total: result.total ?? 0,
      }));
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return;

      setUsers([]);
      setPagination((current) => ({ ...current, total: 0 }));
      message.error(getSystemErrorMessage(error, '用户列表加载失败'));
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setIsLoading(false);
      }
    }
  }, [filters, pagination.current, pagination.pageSize]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  const refreshUsers = useCallback(() => {
    void loadUsers();
  }, [loadUsers]);

  const searchUsers = useCallback((values: UserFilterValues) => {
    setFilters(values);
    setPagination((current) => ({ ...current, current: 1 }));
  }, []);

  const changePage = useCallback(
    (nextCurrent: number, nextPageSize: number) => {
      setPagination((current) => ({
        ...current,
        current:
          current.pageSize === nextPageSize ? nextCurrent : 1,
        pageSize: nextPageSize,
      }));
    },
    [],
  );

  return {
    users,
    isLoading,
    pagination,
    refreshUsers,
    searchUsers,
    changePage,
  };
}
