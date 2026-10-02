import { Alert, Badge, Button, Drawer, Input, Modal, message, Segmented, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { history } from '@umijs/max';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApprovalStatusTag } from '@/components/ApprovalStatusTag';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { YakButton, YakEmpty } from '@/components/ui';
import { findByBiz, getApprovalDetail } from '@/services/approval/api';
import type { ApprovalInstance } from '@/services/approval/types';
import {
  formatSemanticTime,
  SEMANTIC_STANDARD_KIND_COLORS,
  SEMANTIC_STANDARD_KIND_LABELS,
  SEMANTIC_STANDARD_KINDS,
  SEMANTIC_STATUS_COLORS,
  SEMANTIC_STATUS_LABELS,
} from '@/pages/semantic/constants';
import CodeSetEditModal from '@/pages/semantic/standards/components/CodeSetEditModal';
import StandardDetailDrawer from '@/pages/semantic/standards/components/StandardDetailDrawer';
import StandardEditModal from '@/pages/semantic/standards/components/StandardEditModal';
import StandardVersionsDrawer from '@/pages/semantic/standards/components/StandardVersionsDrawer';
import {
  changeCodeSetStatus,
  changeSemanticStandardStatus,
  deleteCodeSet,
  deleteSemanticStandard,
  getCodeSet,
  getCodeSetVersions,
  getSemanticStandard,
  getSemanticStandardUsage,
  isStandardPublishFlowEnabled,
  initializeSemanticPresets,
  listSemanticStandardVersions,
  pageCodeSets,
  pageSemanticStandards,
  submitStandardPublishApproval,
} from '@/services/semantic/api';
import type {
  CodeSetDetailRecord,
  CodeSetRecord,
  SemanticStandardKind,
  SemanticStandardRecord,
  SemanticStandardUsageSummary,
  SemanticStandardVersionRecord,
} from '@/services/semantic/types';

const KIND_OPTIONS = [
  { label: '全部', value: '' as const },
  ...SEMANTIC_STANDARD_KINDS.map((kind) => ({ label: kind.label, value: kind.value })),
];

const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

// 发布审批开关(缺口单03):flowCode/bizType 与后端 STANDARD_PUBLISH 链路同源。
const STANDARD_PUBLISH_FLOW_CODE = 'STANDARD_PUBLISH';
const STANDARD_PUBLISH_BIZ_TYPE = 'STANDARD';
const PUBLISH_APPROVAL_POLL_MS = 15_000;

/** 驳回原因 = 最近一次 REJECTED 级次的 comment;查询失败静默降级为无原因。 */
async function findRejectReason(instanceId: number): Promise<string | undefined> {
  try {
    const detail = await getApprovalDetail(instanceId);
    return detail.steps?.find((step) => step.status === 'REJECTED')?.comment || undefined;
  } catch {
    return undefined;
  }
}

const SemanticStandardsPage = () => {
  const [codeSetDetail, setCodeSetDetail] = useState<CodeSetDetailRecord | null>(null);
  const [records, setRecords] = useState<SemanticStandardRecord[]>([]);
  const [codeSetRecords, setCodeSetRecords] = useState<CodeSetRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [kind, setKind] = useState<SemanticStandardKind | ''>('');
  const [status, setStatus] = useState<'ENABLED' | 'DISABLED' | ''>('');
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [detail, setDetail] = useState<SemanticStandardRecord | null>(null);
  const [usageSummary, setUsageSummary] = useState<SemanticStandardUsageSummary | null>(null);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editing, setEditing] = useState<SemanticStandardRecord | null>(null);
  const [codeSetEditModalOpen, setCodeSetEditModalOpen] = useState(false);
  const [codeSetEditing, setCodeSetEditing] = useState<CodeSetDetailRecord | null>(null);
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [versionsStandard, setVersionsStandard] = useState<SemanticStandardRecord | null>(null);
  const [versions, setVersions] = useState<SemanticStandardVersionRecord[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [publishFlowEnabled, setPublishFlowEnabled] = useState(false);
  const [approvals, setApprovals] = useState<Record<string, ApprovalInstance>>({});
  const { can } = usePermissionAccess();

  const isCodeSetView = kind === 'CODE';
  const hasFilter = Boolean(keyword || kind || status);

  // 发布审批开关(缺口单03):STANDARD_PUBLISH 流未配置/未启用时按关闭处理,维持现状直发。
  useEffect(() => {
    let alive = true;
    isStandardPublishFlowEnabled()
      .then((enabled) => {
        if (alive) {
          setPublishFlowEnabled(enabled);
        }
      })
      .catch(() => {
        // 配置查询不可用时按审批开启处理,避免 UI 展示可绕过的直启用入口。
        if (alive) setPublishFlowEnabled(true);
      });
    return () => {
      alive = false;
    };
  }, []);

  /** 当前页非 CODE 行逐个查在途/最近审批单(每页 ≤10,查询失败按无单处理不阻塞列表)。 */
  const refreshApprovals = useCallback(async (rows: SemanticStandardRecord[]) => {
    const ids = rows.filter((row) => row.kind !== 'CODE' && row.id != null).map((row) => Number(row.id));
    if (!ids.length) {
      setApprovals({});
      return;
    }
    const entries = await Promise.all(
      ids.map(async (id) => {
        try {
          const instance = await findByBiz(STANDARD_PUBLISH_FLOW_CODE, STANDARD_PUBLISH_BIZ_TYPE, id);
          return [id, instance] as const;
        } catch {
          return [id, null] as const;
        }
      }),
    );
    setApprovals(Object.fromEntries(entries.filter((entry): entry is [number, ApprovalInstance] => Boolean(entry[1]))));
  }, []);

  const loadStandards = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      setLoading(true);
      setLoadError(false);
      try {
        if (isCodeSetView) {
          const result = await pageCodeSets({
            pageNo: targetPageNo,
            pageSize: targetPageSize,
            keyword: keyword || undefined,
            status: status || undefined,
          });
          setCodeSetRecords(result.bizData ?? []);
          setTotal(result.pagination?.total ?? 0);
          setRecords([]);
          setApprovals({});
        } else {
          const result = await pageSemanticStandards({
            pageNo: targetPageNo,
            pageSize: targetPageSize,
            kind: kind || undefined,
            keyword: keyword || undefined,
            status: status || undefined,
          });
          setRecords(result.bizData ?? []);
          setTotal(result.pagination?.total ?? 0);
          setCodeSetRecords([]);
          if (publishFlowEnabled) {
            void refreshApprovals(result.bizData ?? []);
          } else {
            setApprovals({});
          }
        }
      } catch {
        setRecords([]);
        setCodeSetRecords([]);
        setTotal(0);
        setApprovals({});
        setLoadError(true);
      } finally {
        setLoading(false);
      }
    },
    [isCodeSetView, kind, keyword, publishFlowEnabled, refreshApprovals, status],
  );

  useEffect(() => {
    void loadStandards(pageNo, pageSize);
  }, [pageNo, pageSize, isCodeSetView, status, loadStandards]);

  const handleSearch = (value: string) => {
    setKeyword(value.trim());
    setPageNo(1);
  };

  const pendingApprovalIds = useMemo(
    () =>
      Object.entries(approvals)
        .filter(([, instance]) => instance.status === 'PENDING')
        .map(([key]) => key),
    [approvals],
  );

  // 在途单低频轮询终态:批准回调与启用同事务,翻到 APPROVED 即刷新列表;驳回取审批单提示原因。
  useEffect(() => {
    if (!pendingApprovalIds.length) {
      return undefined;
    }
    const timer = setInterval(async () => {
      const results = await Promise.all(
        pendingApprovalIds.map(async (id) => {
          try {
            return await findByBiz(STANDARD_PUBLISH_FLOW_CODE, STANDARD_PUBLISH_BIZ_TYPE, id);
          } catch {
            return null; // 轮询失败等下一轮
          }
        }),
      );
      const terminals = results.filter(
        (instance): instance is ApprovalInstance => Boolean(instance) && instance?.status !== 'PENDING',
      );
      if (!terminals.length) {
        return;
      }
      await loadStandards(pageNo, pageSize);
      for (const instance of terminals) {
        if (instance.status === 'APPROVED') {
          message.info(`${instance.title}已通过，标准已自动启用`);
        } else if (instance.status === 'REJECTED') {
          const reason = await findRejectReason(instance.id);
          message.warning(`${instance.title}被驳回${reason ? `：${reason}` : '，标准未启用'}`);
        } else {
          message.info(`${instance.title}已撤销`);
        }
      }
    }, PUBLISH_APPROVAL_POLL_MS);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingApprovalIds.join(','), pageNo, pageSize, loadStandards]);

  const submitPublish = (record: SemanticStandardRecord) => {
    if (record.id == null) {
      message.error('标准 ID 缺失，无法提交发布审批');
      return;
    }
    const id = Number(record.id);
    Modal.confirm({
      title: '提交发布审批',
      content: `确定提交标准「${record.name}」的生效审批？批准后自动启用；在途期间不可重复提交或直接改状态。`,
      okText: '提交',
      cancelText: '取消',
      onOk: async () => {
        try {
          const instance = await submitStandardPublishApproval(id);
          message.success(`已提交发布审批（单 #${instance.id}），批准后自动启用`);
          setApprovals((prev) => ({ ...prev, [String(id)]: instance }));
        } catch (error) {
          message.error(error instanceof Error ? error.message : '提交发布审批失败');
        }
      },
    });
  };

  const openDetail = async (record: SemanticStandardRecord) => {
    if (record.kind === 'CODE') {
      try { setCodeSetDetail(await getCodeSet(record.codeSetCode || record.code)); }
      catch { message.error('码集详情暂不可读取，请重试'); }
      return;
    }
    setDetail(record);
    setUsageSummary(null);
    try {
      const fresh = await getSemanticStandard(record.id);
      setDetail(fresh);
    } catch {
      // 详情刷新失败时保留列表行数据展示
    }
    try {
      setUsageSummary(await getSemanticStandardUsage(record.id));
    } catch {
      // 统计加载失败不影响详情展示
    }
  };

  const openCodeSetEdit = async (codeSetCode?: string) => {
    // 无组键 = 新建;存量空码集行以其 std_code 为组键(后端兼容解析)
    if (!codeSetCode) {
      setCodeSetEditing(null);
      setCodeSetEditModalOpen(true);
      return;
    }
    try {
      const data = await getCodeSet(codeSetCode);
      setCodeSetEditing(data);
      setCodeSetEditModalOpen(true);
    } catch {
      message.error('加载码集详情失败');
    }
  };

  const toggleCodeSetStatus = (record: CodeSetRecord) => {
    const next = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    Modal.confirm({
      title: next === 'DISABLED' ? '停用码集' : '启用码集',
      content:
        next === 'DISABLED'
          ? `确定停用码集「${record.name}」？该码集下所有码值将被停用。`
          : `确定启用码集「${record.name}」？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await changeCodeSetStatus(record.codeSetCode, next);
          message.success(next === 'DISABLED' ? '已停用' : '已启用');
          await loadStandards(pageNo, pageSize);
        } catch {
          message.error('操作失败，请稍后重试');
        }
      },
    });
  };

  const removeCodeSet = (record: CodeSetRecord) => {
    Modal.confirm({
      title: '删除码集',
      content: `确定删除码集「${record.name}（${record.codeSetCode}）」？该码集下所有码值将被删除，不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteCodeSet(record.codeSetCode);
          message.success('已删除');
          await loadStandards(pageNo, pageSize);
        } catch {
          message.error('删除失败（码集可能已被字段引用），请稍后重试');
        }
      },
    });
  };

  const openEdit = (record: SemanticStandardRecord | null) => {
    if (record && record.kind === 'CODE') {
      // 码值标准以码集为整体编辑;存量空码集行回退用 std_code 作组键
      void openCodeSetEdit(record.codeSetCode || record.code);
      return;
    }
    setEditing(record);
    setEditModalOpen(true);
  };

  const openVersions = async (record: SemanticStandardRecord) => {
    // 码值标准以码集为整体回溯(32.1):合并组内各行快照
    if (record.kind === 'CODE') {
      await openCodeSetVersions(record.codeSetCode || record.code, record.name);
      return;
    }
    setVersionsStandard(record);
    setVersionsOpen(true);
    setVersionsLoading(true);
    try {
      const list = await listSemanticStandardVersions(record.id);
      setVersions(list ?? []);
    } catch {
      setVersions([]);
    } finally {
      setVersionsLoading(false);
    }
  };

  /** 码集级版本(32.1):合并组内各行修改前快照。 */
  const openCodeSetVersions = async (codeSetCode: string, name: string) => {
    setVersionsStandard({
      id: 0,
      kind: 'CODE',
      code: codeSetCode,
      name,
      status: 'ENABLED',
      version: 0,
      sortOrder: 0,
      preset: false,
    });
    setVersionsOpen(true);
    setVersionsLoading(true);
    try {
      setVersions((await getCodeSetVersions(codeSetCode)) ?? []);
    } catch {
      setVersions([]);
    } finally {
      setVersionsLoading(false);
    }
  };

  const toggleStatus = (record: SemanticStandardRecord) => {
    if (record.kind === 'CODE') {
      toggleCodeSetStatusByStandard(record);
      return;
    }
    const next = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    Modal.confirm({
      title: next === 'DISABLED' ? '停用数据标准' : '启用数据标准',
      content:
        next === 'DISABLED'
          ? `确定停用「${record.name}」？被引用的标准停用后相关推荐将不可用。`
          : `确定启用「${record.name}」？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await changeSemanticStandardStatus(record.id, next);
          message.success(next === 'DISABLED' ? '已停用' : '已启用');
          await loadStandards(pageNo, pageSize);
        } catch {
          message.error('操作失败，请稍后重试');
        }
      },
    });
  };

  /** 从全部视图操作 CODE 行时,按码集整组启停。 */
  const toggleCodeSetStatusByStandard = (record: SemanticStandardRecord) => {
    const codeSetCode = record.codeSetCode || record.code;
    if (!codeSetCode) {
      message.error('码集编码缺失');
      return;
    }
    const next = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    Modal.confirm({
      title: next === 'DISABLED' ? '停用码集' : '启用码集',
      content:
        next === 'DISABLED'
          ? `确定停用码集「${record.name}」？该码集下所有码值将被停用。`
          : `确定启用码集「${record.name}」？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await changeCodeSetStatus(codeSetCode, next);
          message.success(next === 'DISABLED' ? '已停用' : '已启用');
          await loadStandards(pageNo, pageSize);
        } catch {
          message.error('操作失败，请稍后重试');
        }
      },
    });
  };

  const removeStandard = (record: SemanticStandardRecord) => {
    if (record.kind === 'CODE') {
      removeCodeSetByStandard(record);
      return;
    }
    Modal.confirm({
      title: '删除数据标准',
      content: `确定删除「${record.name}（${record.code}）」？删除不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteSemanticStandard(record.id);
          message.success('已删除');
          await loadStandards(pageNo, pageSize);
        } catch {
          message.error('删除失败（可能已被引用），请稍后重试');
        }
      },
    });
  };

  /** 从全部视图操作 CODE 行时,按码集整组删除。 */
  const removeCodeSetByStandard = (record: SemanticStandardRecord) => {
    const codeSetCode = record.codeSetCode || record.code;
    if (!codeSetCode) {
      message.error('码集编码缺失');
      return;
    }
    Modal.confirm({
      title: '删除码集',
      content: `确定删除码集「${record.name}（${codeSetCode}）」？该码集下所有码值将被删除，不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteCodeSet(codeSetCode);
          message.success('已删除');
          await loadStandards(pageNo, pageSize);
        } catch {
          message.error('删除失败（码集可能已被字段引用），请稍后重试');
        }
      },
    });
  };

  const handleInitializePresets = async () => {
    try {
      const created = await initializeSemanticPresets();
      message.success(`已初始化 ${created} 条预置标准`);
      setPageNo(1);
      await loadStandards(1, pageSize);
    } catch {
      message.error('初始化预置标准失败，请稍后重试');
    }
  };

  const codeSetColumns = useMemo(
    () => [
      {
        title: '编码',
        dataIndex: 'codeSetCode',
        width: 200,
        render: (value: string) => <Typography.Link onClick={async () => {
          try { setCodeSetDetail(await getCodeSet(value)); }
          catch { message.error('码集详情暂不可读取，请重试'); }
        }}>{value}</Typography.Link>,
      },
      { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
      {
        title: '类别',
        dataIndex: 'kind',
        width: 110,
        render: () => (
          <Tag color={SEMANTIC_STANDARD_KIND_COLORS['CODE']}>{SEMANTIC_STANDARD_KIND_LABELS['CODE']}</Tag>
        ),
      },
      { title: '码值数', dataIndex: 'valueCount', width: 90, align: 'right' as const },
      {
        title: '状态',
        dataIndex: 'status',
        width: 90,
        render: (value: keyof typeof SEMANTIC_STATUS_LABELS) => (
          <Tag color={SEMANTIC_STATUS_COLORS[value]}>{SEMANTIC_STATUS_LABELS[value] ?? value}</Tag>
        ),
      },
      { title: '版本', dataIndex: 'version', width: 70, align: 'right' as const },
      {
        title: '预置',
        dataIndex: 'preset',
        width: 80,
        render: (value: boolean) => (value ? <Badge status="processing" text="预置" /> : '-'),
      },
      { title: '描述', dataIndex: 'description', ellipsis: true },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 170,
        render: (value?: string) => formatSemanticTime(value),
      },
      {
        title: '操作',
        key: 'actions',
        // 宽表在 1280px 下会把操作列挤出可视区,钉在右侧保证始终可操作。
        fixed: 'right' as const,
        width: 180,
        render: (_: unknown, record: CodeSetRecord) => (
          <Space size={0}>
            {can('semantic:update') ? <>
              <Button type="link" size="small" onClick={() => void openCodeSetEdit(record.codeSetCode)}>
                编辑
              </Button>
              <Button type="link" size="small" onClick={() => toggleCodeSetStatus(record)}>
                {record.status === 'ENABLED' ? '停用' : '启用'}
              </Button>
            </> : null}
            {/* 码集也有版本历史;此前只有标准行有入口,v1 码集完全看不到初始版本。 */}
            <Button type="link" size="small" onClick={() => void openCodeSetVersions(record.codeSetCode, record.name)}>
              版本
            </Button>
            {can('semantic:delete') && record.preset ? (
              <Tooltip title="平台预置码集不可删除">
                <span>
                  <Button type="link" size="small" danger disabled>
                    删除
                  </Button>
                </span>
              </Tooltip>
            ) : can('semantic:delete') ? (
              <Button type="link" size="small" danger onClick={() => removeCodeSet(record)}>
                删除
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [can, pageNo, pageSize, keyword, kind, status, loadStandards],
  );

  const columns = useMemo(
    () => [
      {
        title: '编码',
        dataIndex: 'code',
        width: 180,
        render: (value: string, record: SemanticStandardRecord) => (
          // I-5:点编码打开详情抽屉(含引用/绕过用度统计)
          <Typography.Link onClick={() => void openDetail(record)}>
            <Typography.Text code>{value}</Typography.Text>
          </Typography.Link>
        ),
      },
      { title: '名称', dataIndex: 'name', width: 200, ellipsis: true },
      {
        title: '类别',
        dataIndex: 'kind',
        width: 110,
        render: (value: SemanticStandardKind) => (
          <Tag color={SEMANTIC_STANDARD_KIND_COLORS[value]}>{SEMANTIC_STANDARD_KIND_LABELS[value] ?? value}</Tag>
        ),
      },
      {
        title: '码值数',
        dataIndex: 'codeValueCount',
        width: 80,
        align: 'right' as const,
        render: (value?: number) => (value == null ? '-' : value),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 150,
        render: (value: keyof typeof SEMANTIC_STATUS_LABELS, record: SemanticStandardRecord) => {
          const instance = record.id == null ? undefined : approvals[String(record.id)];
          return (
            <Space size={4}>
              <Tag color={SEMANTIC_STATUS_COLORS[value]}>{SEMANTIC_STATUS_LABELS[value] ?? value}</Tag>
              {instance ? <ApprovalStatusTag status={instance.status} /> : null}
            </Space>
          );
        },
      },
      {
        title: '预置',
        dataIndex: 'preset',
        width: 80,
        render: (value: boolean) => (value ? <Badge status="processing" text="预置" /> : '-'),
      },
      { title: '版本', dataIndex: 'version', width: 70, align: 'right' as const },
      { title: '描述', dataIndex: 'description', ellipsis: true },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 170,
        render: (value?: string) => formatSemanticTime(value),
      },
      {
        title: '操作',
        key: 'actions',
        // 宽表在 1280px 下会把操作列挤出可视区,钉在右侧保证始终可操作。
        fixed: 'right' as const,
        width: 260,
        render: (_: unknown, record: SemanticStandardRecord) => {
          const instance = record.id == null ? undefined : approvals[String(record.id)];
          const approvalPending = instance?.status === 'PENDING';
          // 开闸后非 CODE 行的"启用"改走提交发布;CODE 行(码集整组)与开关关闭时维持直发。
          const gated = publishFlowEnabled && record.kind !== 'CODE';
          return (
            <Space size={0}>
              {can('semantic:update') ? <Button type="link" size="small" disabled={approvalPending} onClick={() => openEdit(record)}>
                编辑
              </Button> : null}
              {can('semantic:update') && gated && record.status === 'DISABLED' ? (
                <Tooltip
                  title={approvalPending ? '已有在途生效审批单，禁止重复提交' : '启用需经审批，批准后自动生效'}
                >
                  <Button type="link" size="small" disabled={approvalPending} onClick={() => submitPublish(record)}>
                    提交发布
                  </Button>
                </Tooltip>
              ) : can('semantic:update') ? (
                <Button type="link" size="small" disabled={approvalPending} onClick={() => toggleStatus(record)}>
                  {record.status === 'ENABLED' ? '停用' : '启用'}
                </Button>
              ) : null}
              {instance ? (
                <Button
                  type="link"
                  size="small"
                  onClick={() => history.push(`/approval/instance/${instance.id}`)}
                >
                  审批单
                </Button>
              ) : null}
              <Button type="link" size="small" onClick={() => void openVersions(record)}>
                版本
              </Button>
              {can('semantic:delete') && record.preset ? (
                <Tooltip title="平台预置标准不可删除">
                  <span>
                    <Button type="link" size="small" danger disabled>
                      删除
                    </Button>
                  </span>
                </Tooltip>
              ) : can('semantic:delete') ? (
                <Tooltip title={approvalPending ? '发布审批在途，行操作已冻结' : undefined}>
                  <span>
                    <Button
                      type="link"
                      size="small"
                      danger
                      disabled={approvalPending}
                      onClick={() => removeStandard(record)}
                    >
                      删除
                    </Button>
                  </span>
                </Tooltip>
              ) : null}
            </Space>
          );
        },
      },
    ],
    // 行内操作为按行回调;仅审批开关与在途快照参与渲染,需随其重建列。
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [approvals, can, publishFlowEnabled],
  );

  return (
    <div className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">数据标准</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            管理项目空间内的命名、类型、码值、单位、口径、安全六类标准，为业务过程与建模提供约束
          </div>
        </div>
        <Space wrap>
          <Segmented
            value={kind}
            onChange={(value) => {
              setKind(value as SemanticStandardKind | '');
              setPageNo(1);
            }}
            options={KIND_OPTIONS}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            value={status || undefined}
            onChange={(value) => {
              setStatus((value ?? '') as 'ENABLED' | 'DISABLED' | '');
              setPageNo(1);
            }}
            options={STATUS_OPTIONS}
          />
          <Input.Search allowClear placeholder="按编码或名称搜索" className="!w-[240px]" onSearch={handleSearch} />
          {can('semantic:create') && <YakButton
            className="!h-9 !rounded-lg !px-4"
            onClick={() => void handleInitializePresets()}
          >
            补齐预置标准
          </YakButton>}
          {can('semantic:create') ? <YakButton
            type="primary"
            className="!h-9 !rounded-lg !px-4 !text-white"
            onClick={() => {
              if (isCodeSetView) {
                void openCodeSetEdit();
              } else {
                openEdit(null);
              }
            }}
          >
            {isCodeSetView ? '新建码集' : '新建标准'}
          </YakButton> : null}
        </Space>
      </div>

      {loadError ? (
        <Alert
          className="mt-4"
          type="error"
          showIcon
          message="数据标准加载失败"
          description="请检查网络连接后重试。"
          action={<Button size="small" onClick={() => void loadStandards(pageNo, pageSize)}>重试</Button>}
        />
      ) : null}

      {isCodeSetView ? (
        <Table<CodeSetRecord>
          className="mt-4"
          rowKey="codeSetCode"
          loading={loading}
          // 让宽表在表格内部横向滚动,而不是溢出后被外层 overflow-hidden 裁掉。
          scroll={{ x: 'max-content' }}
          columns={codeSetColumns}
          dataSource={codeSetRecords}
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title={loadError ? '码集加载失败' : hasFilter ? '没有符合筛选条件的码集' : '还没有码值标准'}
                description={loadError ? '请点击上方“重试”重新加载。' : hasFilter ? '调整筛选条件或重置后再试' : '点击"新建码集"创建码值标准'}
              />
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 个码集`,
            onChange: (page, size) => {
              setPageNo(page);
              setPageSize(size);
            },
          }}
        />
      ) : (
        <Table<SemanticStandardRecord>
          className="mt-4"
          rowKey={(row) => (row.id == null ? `set:${row.code}` : String(row.id))}
          loading={loading}
          // 让宽表在表格内部横向滚动,而不是溢出后被外层 overflow-hidden 裁掉。
          scroll={{ x: 'max-content' }}
          columns={columns}
          dataSource={records}
          locale={{
            emptyText: (
              <div>
                <YakEmpty
                  compact
                  title={loadError ? '数据标准加载失败' : hasFilter ? '没有符合筛选条件的标准' : '还没有数据标准'}
                  description={
                    loadError
                      ? '请点击上方“重试”重新加载。'
                      : hasFilter
                      ? '调整筛选条件或重置后再试'
                      : '可补齐缺少的平台预置标准；已有手工定义会保留。'
                  }
                />
              </div>
            ),
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (page, size) => {
              setPageNo(page);
              setPageSize(size);
            },
          }}
        />
      )}

      <StandardDetailDrawer
        open={Boolean(detail)}
        standard={detail}
        usageSummary={usageSummary}
        publishApproval={detail?.id == null ? null : (approvals[String(detail.id)] ?? null)}
        publishFlowEnabled={publishFlowEnabled}
        onSubmitPublish={submitPublish}
        onClose={() => setDetail(null)}
      />

      <StandardEditModal
        open={editModalOpen}
        editing={editing}
        onClose={() => setEditModalOpen(false)}
        onSaved={() => {
          void loadStandards(pageNo, pageSize);
        }}
        onSwitchToCodeSet={() => {
          setCodeSetEditing(null);
          setCodeSetEditModalOpen(true);
        }}
      />

      <Drawer open={Boolean(codeSetDetail)} onClose={() => setCodeSetDetail(null)}
        width="min(720px, 100vw)" title={codeSetDetail ? `码集：${codeSetDetail.name}（${codeSetDetail.codeSetCode}）` : '码集详情'}>
        {codeSetDetail ? <>
          <Typography.Paragraph>整组状态：{SEMANTIC_STATUS_LABELS[codeSetDetail.status]}</Typography.Paragraph>
          <Typography.Paragraph>{codeSetDetail.description || '暂无描述'}</Typography.Paragraph>
          <Table rowKey="id" dataSource={codeSetDetail.values} pagination={false} columns={[
            { title: '码值', dataIndex: 'codeValue' }, { title: '标签', dataIndex: 'codeLabel' }, { title: '排序', dataIndex: 'sortOrder' },
          ]} />
          <Button onClick={() => void openCodeSetVersions(codeSetDetail.codeSetCode, codeSetDetail.name)}>查看码值行修改历史</Button>
        </> : null}
      </Drawer>
      <CodeSetEditModal
        open={codeSetEditModalOpen}
        detail={codeSetEditing}
        onClose={() => {
          setCodeSetEditModalOpen(false);
          setCodeSetEditing(null);
        }}
        onSaved={() => {
          void loadStandards(pageNo, pageSize);
        }}
        onExists={(codeSetCode) => {
          // 新建时码集已存在 → 转入编辑既有码集
          if (can('semantic:update')) void openCodeSetEdit(codeSetCode);
        }}
      />

      <StandardVersionsDrawer
        open={versionsOpen}
        standard={versionsStandard}
        versions={versions}
        loading={versionsLoading}
        onClose={() => setVersionsOpen(false)}
      />
    </div>
  );
};

export default SemanticStandardsPage;
