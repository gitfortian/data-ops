import { useCallback, useState } from 'react';

import {
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import type { SystemRole } from '@/services/security/roles';

import SystemManagementPage from '../components/SystemManagementPage';
import RoleDetailDrawer from './components/RoleDetailDrawer';
import RoleEditorDrawer from './components/RoleEditorDrawer';
import RoleFilterBar from './components/RoleFilterBar';
import RoleUserAssignmentDrawer from './components/RoleUserAssignmentDrawer';
import { useRoleColumns } from './hooks/useRoleColumns';
import { useRoleDelete } from './hooks/useRoleDelete';
import { useRoles } from './hooks/useRoles';

export default function RolesPage() {
  const {
    roles,
    isLoading,
    pagination,
    refreshRoles,
    searchRoles,
    changePage,
  } = useRoles();

  const [editorOpen, setEditorOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<SystemRole>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRole, setDetailRole] = useState<SystemRole>();
  const [assignmentOpen, setAssignmentOpen] = useState(false);
  const [assignmentRole, setAssignmentRole] =
    useState<SystemRole>();

  const showDetail = useCallback((role: SystemRole) => {
    setDetailRole(role);
    setDetailOpen(true);
  }, []);

  const showEdit = useCallback((role: SystemRole) => {
    setEditingRole(role);
    setEditorOpen(true);
  }, []);

  const showAssignment = useCallback((role: SystemRole) => {
    setAssignmentRole(role);
    setAssignmentOpen(true);
  }, []);

  const confirmDelete = useRoleDelete(refreshRoles);
  const columns = useRoleColumns({
    onDetail: showDetail,
    onEdit: showEdit,
    onAssignUsers: showAssignment,
    onDelete: (role) => void confirmDelete(role),
  });

  return (
    <SystemManagementPage
      title="角色与权限"
      titleId="system-roles-title"
      className="min-h-[calc(100vh-64px)] overflow-hidden"
    >
      <div className="shrink-0">
        <RoleFilterBar
          total={pagination.total}
          loading={isLoading}
          onSearch={searchRoles}
          onRefresh={refreshRoles}
          onCreate={() => {
            setEditingRole(undefined);
            setEditorOpen(true);
          }}
        />

        <SecurityQueryTable<SystemRole>
          rowKey="id"
          columns={columns}
          dataSource={roles}
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

      <RoleEditorDrawer
        open={editorOpen}
        role={editingRole}
        onClose={() => {
          setEditorOpen(false);
          setEditingRole(undefined);
        }}
        onSuccess={refreshRoles}
      />

      <RoleDetailDrawer
        open={detailOpen}
        role={detailRole}
        onClose={() => {
          setDetailOpen(false);
          setDetailRole(undefined);
        }}
      />

      <RoleUserAssignmentDrawer
        open={assignmentOpen}
        role={assignmentRole}
        onClose={() => {
          setAssignmentOpen(false);
          setAssignmentRole(undefined);
        }}
        onSuccess={refreshRoles}
      />
    </SystemManagementPage>
  );
}
