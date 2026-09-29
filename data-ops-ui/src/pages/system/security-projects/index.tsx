import { useModel } from '@umijs/max';
import {
  Alert,
  Descriptions,
  Drawer,
  Modal,
  Space,
  Switch,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  AssignmentDrawer,
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  getDepartmentTree,
  type DepartmentVO,
} from '@/services/security/departments';
import {
  assignSecurityProjectMembers,
  assignSecurityProjectOwner,
  checkSecurityProjectDeletion,
  createSecurityProject,
  deleteSecurityProject,
  getSecurityProject,
  getSecurityProjectMemberCandidates,
  pageSecurityProjects,
  type SecurityProjectDetail,
  type SecurityProjectInput,
  type SecurityProjectStatus,
  type SecurityProjectSummary,
  type SecurityProjectUser,
  updateSecurityProject,
  updateSecurityProjectStatus,
} from '@/services/security/projects';
import { hasPermission } from '@/utils/security/permission';

import SystemManagementPage from '../components/SystemManagementPage';
import {
  formatSystemDateTime,
  getSystemErrorMessage,
} from '../utils';
import WorkspaceEditorDrawer from './components/WorkspaceEditorDrawer';
import WorkspaceFilterBar, {
  type WorkspaceFilters,
} from './components/WorkspaceFilterBar';
import WorkspaceRowActions from './components/WorkspaceRowActions';
import {
  buildWorkspaceCreateInput,
  filterWorkspaceAssignmentCandidates,
  normalizeWorkspaceInput,
  toWorkspaceDepartmentTreeData,
  type WorkspaceAssignmentMode,
} from './workspace';

const ROOT_PERMISSION = 'security:root';
const DEFAULT_PAGE_SIZE = 10;

interface AssignmentState {
  project: SecurityProjectSummary;
  mode: WorkspaceAssignmentMode;
  value: number[];
}

const userDisplayName = (user?: SecurityProjectUser): string =>
  user?.realName || user?.nickName || user?.userName || '-';

const cleanFilter = (value?: string): string | undefined => {
  const normalized = value?.trim();
  return normalized || undefined;
};

export default function SecurityProjectsPage() {
  const { initialState } = useModel('@@initialState');
  const { currentProject, refreshProjects } = useSecurityProject();
  const permissionCodes = initialState?.currentUser?.permissionCodes ?? [];
  const canManage = hasPermission(permissionCodes, ROOT_PERMISSION);
  const currentUserId = Number(initialState?.currentUser?.id ?? 0);

  const [filters, setFilters] = useState<WorkspaceFilters>({});
  const [rows, setRows] = useState<SecurityProjectSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SecurityProjectSummary>();
  const [saving, setSaving] = useState(false);
  const [departmentRoot, setDepartmentRoot] = useState<DepartmentVO>();
  const [departmentLoading, setDepartmentLoading] = useState(false);

  const [detail, setDetail] = useState<SecurityProjectDetail>();
  const [detailLoading, setDetailLoading] = useState(false);

  const [assigning, setAssigning] = useState<AssignmentState>();
  const [candidates, setCandidates] = useState<SecurityProjectUser[]>([]);
  const [candidateLoading, setCandidateLoading] = useState(false);

  const loadProjects = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageSecurityProjects({
        pageNum,
        pageSize,
        projectCode: cleanFilter(filters.projectCode),
        projectName: cleanFilter(filters.projectName),
        ownerName: cleanFilter(filters.ownerName),
        status: filters.status,
      });
      setRows(result.records);
      setTotal(result.total);
    } catch (error) {
      setRows([]);
      setTotal(0);
      message.error(
        getSystemErrorMessage(error, '工作空间列表加载失败'),
      );
    } finally {
      setLoading(false);
    }
  }, [filters, pageNum, pageSize]);

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  const loadDepartments = useCallback(async () => {
    if (departmentRoot) return departmentRoot;

    setDepartmentLoading(true);
    try {
      const root = await getDepartmentTree();
      setDepartmentRoot(root);
      return root;
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '部门列表加载失败'),
      );
      return undefined;
    } finally {
      setDepartmentLoading(false);
    }
  }, [departmentRoot]);

  const departmentTreeData = useMemo(
    () => toWorkspaceDepartmentTreeData(departmentRoot),
    [departmentRoot],
  );

  const closeEditor = () => {
    if (saving) return;
    setEditorOpen(false);
    setEditing(undefined);
  };

  const openEditor = async (project?: SecurityProjectSummary) => {
    if (!canManage) return;

    setEditing(project);
    setEditorOpen(true);
    await loadDepartments();
  };

  const saveWorkspace = async (values: SecurityProjectInput) => {
    if (saving) return;

    setSaving(true);
    try {
      if (editing) {
        await updateSecurityProject(
          editing.id,
          normalizeWorkspaceInput(values),
        );
        message.success('工作空间已更新');
      } else {
        if (!currentUserId) {
          throw new Error('无法识别当前用户，不能创建工作空间');
        }
        await createSecurityProject(
          buildWorkspaceCreateInput(values, currentUserId),
        );
        message.success('工作空间已创建，你已成为负责人');
      }

      setEditorOpen(false);
      setEditing(undefined);
      await refreshProjects();
      await loadProjects();
    } catch (error) {
      message.error(
        getSystemErrorMessage(
          error,
          editing ? '工作空间更新失败' : '工作空间创建失败',
        ),
      );
    } finally {
      setSaving(false);
    }
  };

  const showDetail = async (project: SecurityProjectSummary) => {
    setDetailLoading(true);
    try {
      setDetail(await getSecurityProject(project.id));
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '工作空间详情加载失败'),
      );
    } finally {
      setDetailLoading(false);
    }
  };

  const openAssignment = async (
    project: SecurityProjectSummary,
    mode: WorkspaceAssignmentMode,
  ) => {
    if (!canManage) return;

    setCandidateLoading(true);
    try {
      const [projectDetail, allCandidates] = await Promise.all([
        getSecurityProject(project.id),
        getSecurityProjectMemberCandidates(project.id),
      ]);
      const visibleCandidates = filterWorkspaceAssignmentCandidates(
        mode,
        projectDetail,
        allCandidates,
      );

      setCandidates(visibleCandidates);
      setAssigning({
        project,
        mode,
        value:
          mode === 'owner'
            ? projectDetail.owners.slice(0, 1).map((user) => user.id)
            : projectDetail.members.map((user) => user.id),
      });
    } catch (error) {
      setCandidates([]);
      message.error(
        getSystemErrorMessage(error, '可分配用户加载失败'),
      );
    } finally {
      setCandidateLoading(false);
    }
  };

  const submitAssignment = async (ids: number[]) => {
    if (!assigning) return;

    try {
      if (assigning.mode === 'owner') {
        if (!ids.length) {
          throw new Error('工作空间至少需要一名负责人');
        }
        await assignSecurityProjectOwner(
          assigning.project.id,
          ids.slice(0, 1),
        );
      } else {
        await assignSecurityProjectMembers(assigning.project.id, ids);
      }

      message.success(
        assigning.mode === 'owner' ? '负责人已更新' : '成员已更新',
      );
      setAssigning(undefined);
      setCandidates([]);
      await refreshProjects();
      await loadProjects();
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '成员关系更新失败'),
      );
      throw error;
    }
  };

  const changeStatus = (
    project: SecurityProjectSummary,
    checked: boolean,
  ) => {
    if (!canManage) return;

    const status: SecurityProjectStatus = checked
      ? 'ENABLED'
      : 'DISABLED';

    Modal.confirm({
      title: `确认${checked ? '启用' : '停用'}工作空间“${project.projectName}”？`,
      content: !checked
        ? '停用后，该工作空间将从成员的工作空间切换列表中移除。'
        : undefined,
      onOk: async () => {
        try {
          await updateSecurityProjectStatus(project.id, status);
          await refreshProjects();
          await loadProjects();
          message.success('工作空间状态已更新');
        } catch (error) {
          message.error(
            getSystemErrorMessage(error, '工作空间状态更新失败'),
          );
          throw error;
        }
      },
    });
  };

  const removeWorkspace = async (project: SecurityProjectSummary) => {
    if (!canManage) return;

    try {
      const check = await checkSecurityProjectDeletion(project.id);
      Modal.confirm({
        title: `删除工作空间“${project.projectName}”？`,
        okText: '删除',
        okButtonProps: {
          danger: true,
          disabled: !check.deletable,
        },
        content: check.deletable ? (
          <Alert
            showIcon
            type="warning"
            message="检查通过。删除后无法恢复，请确认该工作空间确实不再使用。"
          />
        ) : (
          <div className="space-y-3">
            <Alert
              showIcon
              type="error"
              message={
                check.reason ?? '工作空间仍有关联资源，无法删除。'
              }
            />
            <Space size={[4, 6]} wrap>
              {check.resourceNameList.map((name) => (
                <Tag key={name}>{name}</Tag>
              ))}
            </Space>
          </div>
        ),
        onOk: async () => {
          try {
            await deleteSecurityProject(project.id);
            await refreshProjects();
            await loadProjects();
            message.success('工作空间已删除');
          } catch (error) {
            message.error(
              getSystemErrorMessage(error, '工作空间删除失败'),
            );
            throw error;
          }
        },
      });
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '删除前检查失败'),
      );
    }
  };

  const columns = useMemo<TableColumnsType<SecurityProjectSummary>>(
    () => [
      {
        title: '工作空间',
        dataIndex: 'projectName',
        width: 240,
        render: (_, row) => (
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="truncate font-medium text-slate-800">
                {row.projectName}
              </span>
              {currentProject?.id === row.id ? (
                <Tag color="processing">当前</Tag>
              ) : null}
            </div>
            <div className="mt-1 text-xs text-slate-400">
              {row.projectCode || '-'}
            </div>
          </div>
        ),
      },
      {
        title: '所属部门',
        dataIndex: 'deptPath',
        width: 220,
        render: (_, row) => row.deptPath?.join(' / ') || '-',
      },
      {
        title: '负责人',
        dataIndex: 'owners',
        width: 180,
        render: (_, row) =>
          row.owners.length > 0 ? (
            <Space size={[4, 4]} wrap>
              {row.owners.map((owner) => (
                <Tag key={owner.id}>{userDisplayName(owner)}</Tag>
              ))}
            </Space>
          ) : (
            <span className="text-amber-600">未分配</span>
          ),
      },
      {
        title: '普通成员',
        dataIndex: 'memberCount',
        width: 100,
        align: 'center',
        render: (value) => Number(value ?? 0),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (_, row) =>
          canManage ? (
            <Switch
              size="small"
              checked={row.status === 'ENABLED'}
              checkedChildren="启用"
              unCheckedChildren="停用"
              onChange={(checked) => changeStatus(row, checked)}
            />
          ) : (
            <Tag color={row.status === 'ENABLED' ? 'success' : 'default'}>
              {row.status === 'ENABLED' ? '启用' : '停用'}
            </Tag>
          ),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        width: 168,
        render: (value) =>
          formatSystemDateTime(value as string | undefined),
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: canManage ? 190 : 88,
        render: (_, row) => (
          <WorkspaceRowActions
            project={row}
            canManage={canManage}
            onDetail={(project) => void showDetail(project)}
            onEdit={(project) => void openEditor(project)}
            onAssignOwner={(project) =>
              void openAssignment(project, 'owner')
            }
            onAssignMembers={(project) =>
              void openAssignment(project, 'member')
            }
            onDelete={(project) => void removeWorkspace(project)}
          />
        ),
      },
    ],
    [canManage, currentProject?.id],
  );

  return (
    <SystemManagementPage
      title="工作空间"
      titleId="system-workspaces-title"
      className="min-h-[calc(100vh-64px)] overflow-hidden"
    >
      <div className="shrink-0">
        <WorkspaceFilterBar
          total={total}
          loading={loading}
          canManage={canManage}
          onSearch={(nextFilters) => {
            setPageNum(1);
            setFilters(nextFilters);
          }}
          onRefresh={() => void loadProjects()}
          onCreate={() => void openEditor()}
        />

        <SecurityQueryTable<SecurityProjectSummary>
          rowKey="id"
          dataSource={rows}
          columns={columns}
          loading={loading}
          pagination={false}
          bordered
          scroll={{ x: 'max-content' }}
        />
      </div>

      <div className="min-h-6 flex-1" />

      <SecurityPagination
        current={pageNum}
        pageSize={pageSize}
        total={total}
        disabled={loading}
        onChange={(nextPage, nextPageSize) => {
          setPageNum(nextPageSize === pageSize ? nextPage : 1);
          setPageSize(nextPageSize);
        }}
      />

      <WorkspaceEditorDrawer
        open={editorOpen}
        editing={editing}
        saving={saving}
        departmentLoading={departmentLoading}
        departmentTreeData={departmentTreeData}
        onClose={closeEditor}
        onSave={(values) => void saveWorkspace(values)}
      />

      <AssignmentDrawer
        open={Boolean(assigning)}
        title={
          assigning?.mode === 'owner' ? '分配负责人' : '分配普通成员'
        }
        mode={assigning?.mode === 'owner' ? 'single' : 'multiple'}
        options={candidates.map((user) => ({
          id: user.id,
          label: userDisplayName(user),
          description:
            user.realName && user.realName !== user.userName
              ? user.userName
              : undefined,
        }))}
        value={assigning?.value ?? []}
        loading={candidateLoading}
        allowEmpty={assigning?.mode === 'member'}
        onClose={() => {
          setAssigning(undefined);
          setCandidates([]);
        }}
        onSubmit={submitAssignment}
      />

      <Drawer
        open={Boolean(detail) || detailLoading}
        title="工作空间详情"
        width={600}
        loading={detailLoading}
        onClose={() => setDetail(undefined)}
      >
        {detail ? (
          <div className="space-y-6">
            <Descriptions
              column={1}
              bordered
              items={[
                {
                  key: 'code',
                  label: '编码',
                  children: detail.projectCode || '-',
                },
                {
                  key: 'name',
                  label: '名称',
                  children: detail.projectName,
                },
                {
                  key: 'dept',
                  label: '所属部门',
                  children: detail.deptPath?.join(' / ') || '-',
                },
                {
                  key: 'description',
                  label: '描述',
                  children: detail.description || '-',
                },
                {
                  key: 'status',
                  label: '状态',
                  children: (
                    <Tag
                      color={
                        detail.status === 'ENABLED' ? 'success' : 'default'
                      }
                    >
                      {detail.status === 'ENABLED' ? '启用' : '停用'}
                    </Tag>
                  ),
                },
                {
                  key: 'created',
                  label: '创建时间',
                  children: formatSystemDateTime(detail.createTime),
                },
              ]}
            />

            <div>
              <Typography.Title level={5}>负责人</Typography.Title>
              <Space wrap>
                {detail.owners.length > 0 ? (
                  detail.owners.map((owner) => (
                    <Tag key={owner.id}>{userDisplayName(owner)}</Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">
                    暂未分配负责人
                  </Typography.Text>
                )}
              </Space>
            </div>

            <div>
              <Typography.Title level={5}>普通成员</Typography.Title>
              <Space wrap>
                {detail.members.length > 0 ? (
                  detail.members.map((member) => (
                    <Tag key={member.id}>{userDisplayName(member)}</Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">
                    暂无普通成员
                  </Typography.Text>
                )}
              </Space>
            </div>
          </div>
        ) : null}
      </Drawer>
    </SystemManagementPage>
  );
}
