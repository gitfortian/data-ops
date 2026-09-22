import { useCallback, useState } from 'react';

import type { DepartmentVO } from '@/services/security/departments';

import SystemManagementPage from '../components/SystemManagementPage';
import DepartmentDetailPane from './components/DepartmentDetailPane';
import DepartmentEditorDrawer from './components/DepartmentEditorDrawer';
import DepartmentFilterBar from './components/DepartmentFilterBar';
import DepartmentImportModal from './components/DepartmentImportModal';
import DepartmentTreePane from './components/DepartmentTreePane';
import { useDepartmentDelete } from './hooks/useDepartmentDelete';
import { useDepartments } from './hooks/useDepartments';

export default function DepartmentsPage() {
  const {
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
  } = useDepartments();

  const [importOpen, setImportOpen] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingDepartment, setEditingDepartment] =
    useState<DepartmentVO>();
  const [defaultParentId, setDefaultParentId] = useState(0);

  const openCreate = useCallback((parentId = 0) => {
    setEditingDepartment(undefined);
    setDefaultParentId(parentId);
    setEditorOpen(true);
  }, []);

  const openEdit = useCallback((department: DepartmentVO) => {
    setEditingDepartment(department);
    setDefaultParentId(department.parentId ?? 0);
    setEditorOpen(true);
  }, []);

  const handleDeleted = useCallback(
    async (department: DepartmentVO) => {
      setSelectedId(
        department.parentId && department.parentId !== 0
          ? department.parentId
          : undefined,
      );
      await reloadDepartments();
    },
    [reloadDepartments, setSelectedId],
  );

  const confirmDelete = useDepartmentDelete({
    onDeleted: handleDeleted,
  });

  return (
    <SystemManagementPage
      title="部门管理"
      titleId="system-departments-title"
      className="h-[calc(100vh-64px)] min-h-[640px] overflow-hidden"
    >
      <DepartmentFilterBar
        scope={scope}
        stats={stats}
        keyword={keyword}
        loading={isLoading}
        onScopeChange={setScope}
        onKeywordChange={setKeyword}
        onRefresh={() => void reloadDepartments()}
        onImport={() => setImportOpen(true)}
        onCreate={() => openCreate(0)}
      />

      <div className="grid min-h-0 flex-1 overflow-hidden rounded-lg border border-slate-200 bg-white lg:grid-cols-[390px_minmax(0,1fr)]">
        <DepartmentTreePane
          departments={visibleDepartments}
          selectedId={selectedId}
          expandedKeys={expandedKeys}
          loading={isLoading}
          filtered={isFiltered}
          onSelect={setSelectedId}
          onExpandedKeysChange={setExpandedKeys}
        />

        <DepartmentDetailPane
          department={selectedDepartment}
          treeDepartment={selectedTreeDepartment}
          path={selectedPath}
          children={selectedChildren}
          descendantCount={descendantCount}
          loading={isDetailLoading}
          filtered={isFiltered}
          onSelect={setSelectedId}
          onCreateChild={openCreate}
          onEdit={openEdit}
          onDelete={(department) => void confirmDelete(department)}
        />
      </div>

      <DepartmentEditorDrawer
        open={editorOpen}
        root={root}
        department={editingDepartment}
        defaultParentId={defaultParentId}
        onClose={() => {
          setEditorOpen(false);
          setEditingDepartment(undefined);
        }}
        onSuccess={() => void reloadDepartments()}
      />

      <DepartmentImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        onImported={() => void reloadDepartments()}
      />
    </SystemManagementPage>
  );
}
