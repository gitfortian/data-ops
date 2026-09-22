import { useModel } from '@umijs/max';
import {
  useCallback,
  useRef,
} from 'react';

import {
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import type { SystemUser } from '@/services/security/users';

import SystemManagementPage from '../components/SystemManagementPage';
import UserDetailDrawer, {
  type UserDetailDrawerRef,
} from './components/UserDetailDrawer';
import UserEditorModal, {
  type UserEditorModalRef,
} from './components/UserEditorModal';
import UserFilterBar from './components/UserFilterBar';
import UserResetPasswordModal, {
  type UserResetPasswordModalRef,
} from './components/UserResetPasswordModal';
import UserRoleAssignmentModal, {
  type UserRoleAssignmentModalRef,
} from './components/UserRoleAssignmentModal';
import { useRoleOptions } from './hooks/useRoleOptions';
import { useUserColumns } from './hooks/useUserColumns';
import { useUsers } from './hooks/useUsers';

export default function UsersPage() {
  const editorRef = useRef<UserEditorModalRef>(null);
  const detailRef = useRef<UserDetailDrawerRef>(null);
  const roleAssignmentRef =
    useRef<UserRoleAssignmentModalRef>(null);
  const resetPasswordRef =
    useRef<UserResetPasswordModalRef>(null);

  const { initialState } = useModel('@@initialState');
  const currentUserName = initialState?.currentUser?.userName;
  const roleOptions = useRoleOptions();
  const {
    users,
    isLoading,
    pagination,
    refreshUsers,
    searchUsers,
    changePage,
  } = useUsers();

  const showDetail = useCallback((user: SystemUser) => {
    void detailRef.current?.open(user);
  }, []);

  const showEdit = useCallback((user: SystemUser) => {
    void editorRef.current?.openEdit(user);
  }, []);

  const showRoleAssignment = useCallback((user: SystemUser) => {
    void roleAssignmentRef.current?.open(user);
  }, []);

  const showResetPassword = useCallback((user: SystemUser) => {
    resetPasswordRef.current?.open(user);
  }, []);

  const columns = useUserColumns({
    roleOptions,
    currentUserName,
    onDetail: showDetail,
    onEdit: showEdit,
    onAssignRole: showRoleAssignment,
    onResetPassword: showResetPassword,
    onDeleted: refreshUsers,
  });

  return (
    <SystemManagementPage
      title="用户管理"
      titleId="system-users-title"
      className="min-h-[calc(100vh-64px)] overflow-hidden"
    >
      <div className="shrink-0">
        <UserFilterBar
          roleOptions={roleOptions}
          onSearch={searchUsers}
          onRefresh={refreshUsers}
          onCreate={() => editorRef.current?.openCreate()}
        />

        <SecurityQueryTable<SystemUser>
          rowKey="id"
          columns={columns}
          dataSource={users}
          loading={isLoading}
          pagination={false}
          bordered
          scroll={{ x: 'max-content' }}
        />
      </div>

      <div className="min-h-6 flex-1" />

      <SecurityPagination
        current={pagination.current}
        pageSize={pagination.pageSize}
        total={pagination.total}
        disabled={isLoading}
        onChange={changePage}
      />

      <UserEditorModal
        ref={editorRef}
        roleOptions={roleOptions}
        onSuccess={refreshUsers}
      />
      <UserDetailDrawer ref={detailRef} />
      <UserRoleAssignmentModal
        ref={roleAssignmentRef}
        onSuccess={refreshUsers}
      />
      <UserResetPasswordModal ref={resetPasswordRef} />
    </SystemManagementPage>
  );
}
