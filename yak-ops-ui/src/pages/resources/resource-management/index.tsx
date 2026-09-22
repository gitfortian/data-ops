import YakButton from '@/components/YakButton';
import { YAK_OPS_PERMISSIONS } from '@/constants/yakOpsPermissions';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { API_SUCCESS_CODE } from '@/services/http/response';
import { BRAND_THEME } from '@/styles/brand';
import {
  Breadcrumb,
  ConfigProvider,
  Dropdown,
  Input,
  message,
  Modal,
  Spin,
  Table,
  Tag,
  Tooltip,
  Tree,
  type MenuProps,
  type TableColumnsType,
} from 'antd';
import dayjs from 'dayjs';
import {
  Database,
  Download,
  FilePlus2,
  Files,
  Folder,
  FolderPlus,
  FolderTree,
  HardDrive,
  MoreHorizontal,
  MoveRight,
  Pencil,
  RefreshCw,
  Search,
  Trash2,
  Upload,
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import CreateDirectoryModal from './components/CreateDirectoryModal';
import CreateTextResourceModal from './components/CreateTextResourceModal';
import { FileSuffixIcon } from './components/FileSuffixIcon';
import MoveResourceModal from './components/MoveResourceModal';
import ResourceDetailDrawer from './components/ResourceDetailDrawer';
import ResourceMetadataModal from './components/ResourceMetadataModal';
import StorageTypeLabel from './components/StorageTypeLabel';
import {
  createDirectory,
  createTextResource,
  deleteResource,
  downloadResource,
  fetchResourceList,
  fetchResourceTree,
  fetchStoragePlugins,
  moveResource,
  replaceResourceFile,
  updateResource,
  uploadResource,
} from './service';
import type {
  DirectoryFormValues,
  MoveResourceFormValues,
  ResourceId,
  ResourceItem,
  ResourceMetadataFormValues,
  ResourceStoragePlugin,
  TextResourceFormValues,
} from './types';
import {
  buildDirectoryTree,
  findResource,
  formatFileSize,
  getResourceBreadcrumbs,
  getResourceSummary,
  isDirectory,
  resourceKey,
  ROOT_RESOURCE_ID,
} from './utils';

const { confirm } = Modal;

const TREE_CLASS_NAME = [
  'min-h-[540px] bg-white p-2',
  'max-[860px]:min-h-[180px] max-[860px]:max-h-[260px] max-[860px]:overflow-auto',
  '[&_.ant-tree-node-content-wrapper]:min-h-8',
  '[&_.ant-tree-node-content-wrapper]:rounded-md',
  '[&_.ant-tree-node-content-wrapper]:px-[7px]',
  '[&_.ant-tree-node-content-wrapper]:py-[3px]',
  '[&_.ant-tree-node-content-wrapper]:leading-[26px]',
  '[&_.ant-tree-node-content-wrapper:hover]:bg-[#f5f6f7]',
  '[&_.ant-tree-node-selected]:!bg-[var(--ant-color-primary-bg)]',
  '[&_.ant-tree-node-selected]:!text-[var(--ant-color-primary)]',
  '[&_.ant-tree-title]:text-[13px]',
].join(' ');

const SEARCH_CLASS_NAME = [
  'w-[230px] max-[860px]:w-full',
  '!border-transparent !bg-[#f5f5f6] !shadow-none',
  'hover:!border-transparent hover:!bg-[#efeff0]',
  '[&.ant-input-affix-wrapper-focused]:!border-transparent',
  '[&.ant-input-affix-wrapper-focused]:!bg-[#efeff0]',
  '[&.ant-input-affix-wrapper-focused]:!shadow-none',
].join(' ');

const TABLE_CLASS_NAME = [
  'h-full w-full bg-white',
  '[&_.ant-table]:rounded-none',
  '[&_.ant-table]:bg-white',
  '[&_.ant-table]:text-xs',
  '[&_.ant-table-container]:bg-white',
  '[&_.ant-table-content]:bg-white',
  '[&_.ant-table-thead>tr>th]:h-10',
  '[&_.ant-table-thead>tr>th]:!border-[#eaecf0]',
  '[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb]',
  '[&_.ant-table-thead>tr>th]:!px-3.5',
  '[&_.ant-table-thead>tr>th]:!py-2',
  '[&_.ant-table-thead>tr>th]:!text-xs',
  '[&_.ant-table-thead>tr>th]:!font-medium',
  '[&_.ant-table-thead>tr>th]:!text-[#667085]',
  '[&_.ant-table-tbody>tr>td]:h-[58px]',
  '[&_.ant-table-tbody>tr>td]:!border-[#f0f2f5]',
  '[&_.ant-table-tbody>tr>td]:!bg-white',
  '[&_.ant-table-tbody>tr>td]:!px-3.5',
  '[&_.ant-table-tbody>tr>td]:!py-2',
  '[&_.ant-table-tbody>tr>td]:!text-[#667085]',
  '[&_.ant-table-tbody>tr:hover>td]:!bg-[#fafbfc]',
  '[&_.ant-table-cell-fix-right]:!bg-white',
  '[&_.ant-table-tbody>tr:hover_.ant-table-cell-fix-right]:!bg-[#fafbfc]',
  '[&_.ant-table-placeholder>td]:!h-[calc(100vh-327px)]',
  '[&_.ant-table-placeholder>td]:!min-h-[470px]',
  '[&_.ant-table-placeholder>td]:!border-b-0',
  '[&_.ant-table-placeholder>td]:!bg-white',
  '[&_.ant-table-placeholder>td]:!p-0',
  '[&_.ant-table-placeholder:hover>td]:!bg-white',
  'max-[860px]:[&_.ant-table-placeholder>td]:!h-auto',
  'max-[860px]:[&_.ant-table-placeholder>td]:!min-h-[360px]',
].join(' ');

const ResourceManagementPage = () => {
  const { can } = usePermissionAccess();
  const canCreate = can(YAK_OPS_PERMISSIONS.resource.create);
  const canUpdate = can(YAK_OPS_PERMISSIONS.resource.update);
  const canDelete = can(YAK_OPS_PERMISSIONS.resource.delete);
  const canDownload = can(YAK_OPS_PERMISSIONS.resource.download);

  const uploadInputRef = useRef<HTMLInputElement>(null);
  const replaceInputRef = useRef<HTMLInputElement>(null);
  const replacingResourceRef = useRef<ResourceItem>();
  const listRequestSeqRef = useRef(0);

  const [treeLoading, setTreeLoading] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [resourceTree, setResourceTree] = useState<ResourceItem[]>([]);
  const [resourceList, setResourceList] = useState<ResourceItem[]>([]);
  const [storagePlugins, setStoragePlugins] = useState<
    ResourceStoragePlugin[]
  >([]);
  const [selectedDirectoryId, setSelectedDirectoryId] =
    useState<ResourceId>(ROOT_RESOURCE_ID);
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [directoryModalOpen, setDirectoryModalOpen] = useState(false);
  const [textModalOpen, setTextModalOpen] = useState(false);
  const [metadataResource, setMetadataResource] = useState<ResourceItem>();
  const [movingResource, setMovingResource] = useState<ResourceItem>();
  const [detailResource, setDetailResource] = useState<ResourceItem>();

  const loadTree = useCallback(async () => {
    try {
      setTreeLoading(true);
      const response = await fetchResourceTree();
      if (response.code !== API_SUCCESS_CODE) return;
      setResourceTree(response.data || []);
    } finally {
      setTreeLoading(false);
    }
  }, []);

  const loadPlugins = useCallback(async () => {
    const response = await fetchStoragePlugins();
    if (response.code !== API_SUCCESS_CODE) return;
    setStoragePlugins(response.data || []);
  }, []);

  const loadList = useCallback(async () => {
    const requestSeq = listRequestSeqRef.current + 1;
    listRequestSeqRef.current = requestSeq;

    try {
      setListLoading(true);
      const response = await fetchResourceList(
        selectedDirectoryId,
        debouncedKeyword || undefined,
      );

      if (
        requestSeq !== listRequestSeqRef.current ||
        response.code !== API_SUCCESS_CODE
      ) {
        return;
      }

      setResourceList(response.data || []);
    } finally {
      if (requestSeq === listRequestSeqRef.current) {
        setListLoading(false);
      }
    }
  }, [debouncedKeyword, selectedDirectoryId]);

  useEffect(() => {
    const timer = window.setTimeout(
      () => setDebouncedKeyword(keyword.trim()),
      keyword.trim() ? 250 : 0,
    );

    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    void loadPlugins();
  }, [loadPlugins]);

  useEffect(() => {
    void loadTree();
  }, [loadTree, refreshVersion]);

  useEffect(() => {
    void loadList();
  }, [loadList, refreshVersion]);

  const refresh = useCallback(() => {
    setRefreshVersion((value) => value + 1);
  }, []);

  const selectedDirectory = useMemo(
    () => findResource(resourceTree, selectedDirectoryId),
    [resourceTree, selectedDirectoryId],
  );

  const selectedDirectoryName = selectedDirectory?.name || '全部资源';

  const breadcrumbs = useMemo(
    () => getResourceBreadcrumbs(resourceTree, selectedDirectoryId),
    [resourceTree, selectedDirectoryId],
  );

  const summary = useMemo(
    () => getResourceSummary(resourceTree),
    [resourceTree],
  );

  const directoryTree = useMemo(
    () => buildDirectoryTree(resourceTree),
    [resourceTree],
  );

  const moveDirectoryTree = useMemo(
    () => buildDirectoryTree(resourceTree, { movingResource }),
    [movingResource, resourceTree],
  );

  const navigateToDirectory = (id: ResourceId) => {
    setSelectedDirectoryId(id);
    setKeyword('');
  };

  const openResource = (resource: ResourceItem) => {
    if (isDirectory(resource)) {
      navigateToDirectory(resource.id);
      return;
    }

    setDetailResource(resource);
  };

  const handleCreateDirectory = async (values: DirectoryFormValues) => {
    try {
      setSaving(true);
      const response = await createDirectory({
        parentId: selectedDirectoryId,
        ...values,
      });

      if (response.code !== API_SUCCESS_CODE) return;

      message.success(response.message || '文件夹创建成功');
      setDirectoryModalOpen(false);
      refresh();
    } finally {
      setSaving(false);
    }
  };

  const handleCreateTextResource = async (
    values: TextResourceFormValues,
  ) => {
    try {
      setSaving(true);
      const response = await createTextResource({
        parentId: selectedDirectoryId,
        ...values,
      });

      if (response.code !== API_SUCCESS_CODE) return;

      message.success(response.message || '文件创建成功');
      setTextModalOpen(false);
      refresh();
    } finally {
      setSaving(false);
    }
  };

  const handleUpdateMetadata = async (
    values: ResourceMetadataFormValues,
  ) => {
    if (!metadataResource) return;

    try {
      setSaving(true);
      const response = await updateResource(metadataResource.id, values);

      if (response.code !== API_SUCCESS_CODE) return;

      message.success(response.message || '资源信息已更新');
      setMetadataResource(undefined);

      if (detailResource?.id === metadataResource.id) {
        setDetailResource(response.data);
      }

      refresh();
    } finally {
      setSaving(false);
    }
  };

  const handleMoveResource = async (values: MoveResourceFormValues) => {
    if (!movingResource) return;

    try {
      setSaving(true);
      const response = await moveResource(movingResource.id, values);

      if (response.code !== API_SUCCESS_CODE) return;

      message.success(response.message || '资源移动成功');
      setMovingResource(undefined);

      if (detailResource?.id === movingResource.id) {
        setDetailResource(response.data);
      }

      refresh();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = (resource: ResourceItem) => {
    if (!canDelete) return;

    confirm({
      title: `确认删除${isDirectory(resource) ? '文件夹' : '文件'}吗？`,
      centered: true,
      content: isDirectory(resource)
        ? `文件夹“${resource.name}”及其全部子资源都会被递归删除，删除后无法恢复。`
        : `文件“${resource.name}”删除后无法恢复。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        const response = await deleteResource(resource.id);
        if (response.code !== API_SUCCESS_CODE) return;

        message.success(response.message || '删除成功');

        if (detailResource?.id === resource.id) {
          setDetailResource(undefined);
        }

        refresh();
      },
    });
  };

  const handleDownload = async (resource: ResourceItem) => {
    try {
      await downloadResource(resource.id, resource.name);
      message.success('下载任务已开始');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下载失败');
    }
  };

  const handleUpload = async (file?: File) => {
    if (!file) return;

    try {
      setUploading(true);
      const response = await uploadResource(selectedDirectoryId, file);

      if (response.code !== API_SUCCESS_CODE) return;

      message.success(response.message || '文件上传成功');
      refresh();
    } finally {
      setUploading(false);

      if (uploadInputRef.current) {
        uploadInputRef.current.value = '';
      }
    }
  };

  const requestReplaceFile = (resource: ResourceItem) => {
    replacingResourceRef.current = resource;
    replaceInputRef.current?.click();
  };

  const handleReplaceFile = async (file?: File) => {
    const resource = replacingResourceRef.current;

    if (!resource || !file) return;

    try {
      setUploading(true);
      const response = await replaceResourceFile(resource.id, file);

      if (response.code !== API_SUCCESS_CODE) return;

      message.success(response.message || '文件替换成功');

      if (detailResource?.id === resource.id) {
        setDetailResource(response.data);
      }

      refresh();
    } finally {
      setUploading(false);
      replacingResourceRef.current = undefined;

      if (replaceInputRef.current) {
        replaceInputRef.current.value = '';
      }
    }
  };

  const getActionItems = (resource: ResourceItem): MenuProps['items'] =>
    [
      {
        key: 'open',
        icon: isDirectory(resource) ? (
          <Folder size={15} />
        ) : (
          <FileSuffixIcon suffix={resource.suffix} size={15} />
        ),
        label: isDirectory(resource) ? '打开文件夹' : '查看详情',
      },
      !isDirectory(resource) && canDownload
        ? {
            key: 'download',
            icon: <Download size={15} />,
            label: '下载文件',
          }
        : null,
      canUpdate
        ? {
            key: 'edit',
            icon: <Pencil size={15} />,
            label: '编辑信息',
          }
        : null,
      canUpdate && !isDirectory(resource)
        ? {
            key: 'replace',
            icon: <Upload size={15} />,
            label: '替换文件',
          }
        : null,
      canUpdate
        ? {
            key: 'move',
            icon: <MoveRight size={15} />,
            label: '移动到',
          }
        : null,
      canDelete ? { type: 'divider' as const } : null,
      canDelete
        ? {
            key: 'delete',
            danger: true,
            icon: <Trash2 size={15} />,
            label: '删除',
          }
        : null,
    ].filter(Boolean) as MenuProps['items'];

  const handleAction = (resource: ResourceItem, key: string) => {
    switch (key) {
      case 'open':
        openResource(resource);
        break;
      case 'download':
        void handleDownload(resource);
        break;
      case 'edit':
        setMetadataResource(resource);
        break;
      case 'replace':
        requestReplaceFile(resource);
        break;
      case 'move':
        setMovingResource(resource);
        break;
      case 'delete':
        handleDelete(resource);
        break;
      default:
        break;
    }
  };

  const columns: TableColumnsType<ResourceItem> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 320,
      render: (_, resource) => (
        <button
          type="button"
          className="flex w-full min-w-0 cursor-pointer items-center gap-2.5 border-0 bg-transparent p-0 text-left text-inherit"
          onClick={() => openResource(resource)}
        >
          <span
            className={[
              'inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-[7px]',
              isDirectory(resource)
                ? 'bg-[var(--ant-color-primary-bg)] text-[var(--ant-color-primary)]'
                : 'bg-[#f2f4f7] text-[#667085]',
            ].join(' ')}
          >
            {isDirectory(resource) ? (
              <Folder size={19} />
            ) : (
              <FileSuffixIcon suffix={resource.suffix} />
            )}
          </span>

          <span className="flex min-w-0 flex-col gap-0.5">
            <strong
              className="overflow-hidden text-ellipsis whitespace-nowrap text-[13px] font-medium text-[#344054]"
              title={resource.name}
            >
              {resource.name}
            </strong>
            <small
              className="overflow-hidden text-ellipsis whitespace-nowrap text-[11px] text-[#98a2b3]"
              title={resource.description || resource.fullPath}
            >
              {resource.description || resource.fullPath}
            </small>
          </span>
        </button>
      ),
    },
    {
      title: '类型',
      dataIndex: 'nodeType',
      key: 'nodeType',
      width: 110,
      render: (_, resource) =>
        isDirectory(resource) ? (
          <Tag bordered={false}>文件夹</Tag>
        ) : (
          <Tag bordered={false}>
            {resource.suffix?.toUpperCase() || 'FILE'}
          </Tag>
        ),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 120,
      render: (_, resource) =>
        isDirectory(resource) ? '-' : formatFileSize(resource.fileSize),
    },
    {
      title: '存储',
      dataIndex: 'storageType',
      key: 'storageType',
      width: 120,
      render: (value) => <StorageTypeLabel type={value} />,
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 90,
      render: (value, resource) =>
        isDirectory(resource) ? '-' : `v${value || 1}`,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (value) =>
        value ? dayjs(String(value)).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '',
      key: 'actions',
      fixed: 'right',
      width: 60,
      align: 'center',
      render: (_, resource) => (
        <Dropdown
          trigger={['click']}
          menu={{
            items: getActionItems(resource),
            onClick: ({ key, domEvent }) => {
              domEvent.stopPropagation();
              handleAction(resource, key);
            },
          }}
        >
          <YakButton
            type="text"
            size="small"
            aria-label={`操作 ${resource.name}`}
            icon={<MoreHorizontal size={17} />}
            onClick={(event) => event.stopPropagation()}
          />
        </Dropdown>
      ),
    },
  ];

  const breadcrumbItems = [
    {
      title: (
        <button
          type="button"
          className="max-w-[180px] cursor-pointer overflow-hidden text-ellipsis whitespace-nowrap border-0 bg-transparent p-0 text-[#475467] transition-colors hover:text-[#667085]"
          onClick={() => navigateToDirectory(ROOT_RESOURCE_ID)}
        >
          全部资源
        </button>
      ),
    },
    ...breadcrumbs.map((resource) => ({
      title: (
        <button
          type="button"
          className="max-w-[180px] cursor-pointer overflow-hidden text-ellipsis whitespace-nowrap border-0 bg-transparent p-0 text-[#475467] transition-colors hover:text-[#667085]"
          onClick={() => navigateToDirectory(resource.id)}
        >
          {resource.name}
        </button>
      ),
    })),
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-white px-5 pb-5 pt-4 text-[#101828] max-[860px]:p-3.5">
        <header className="mb-2 flex min-h-11 items-center justify-between gap-4 max-[860px]:items-stretch max-[860px]:flex-col">
          <div>
            <h1 className="m-0 text-[17px] font-semibold leading-6 text-[#161823]">
              资源管理
            </h1>
          </div>

          <div className="flex flex-wrap items-center gap-2 [&_.ant-btn]:h-8">
            {canCreate && (
              <YakButton
                icon={<FolderPlus size={16} />}
                onClick={() => setDirectoryModalOpen(true)}
              >
                新建文件夹
              </YakButton>
            )}

            {canCreate && (
              <YakButton
                icon={<FilePlus2 size={16} />}
                onClick={() => setTextModalOpen(true)}
              >
                在线创建
              </YakButton>
            )}

            {canCreate && (
              <YakButton
                type="primary"
                loading={uploading}
                icon={<Upload size={16} />}
                onClick={() => uploadInputRef.current?.click()}
              >
                上传文件
              </YakButton>
            )}
          </div>
        </header>

        <section className="mb-3 grid grid-cols-4 gap-2 max-[1180px]:grid-cols-2 max-[620px]:grid-cols-1">
          <div className="flex min-h-[70px] min-w-0 items-center gap-3 border border-[#f0f0f0] bg-[#fafbfc] px-4 py-3">
            <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[7px] bg-[#f3f6ff] text-[#6c7fd8]">
              <Files size={20} />
            </span>
            <div className="min-w-0">
              <small className="mb-0.5 block text-[11px] leading-[18px] text-[rgba(22,24,35,0.45)]">
                文件数量
              </small>
              <strong className="block overflow-hidden text-ellipsis whitespace-nowrap text-lg font-semibold leading-6 text-[#161823]">
                {summary.files}
              </strong>
            </div>
          </div>

          <div className="flex min-h-[70px] min-w-0 items-center gap-3 border border-[#f0f0f0] bg-[#fafbfc] px-4 py-3">
            <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[7px] bg-[#f4f6f8] text-[#7b8493]">
              <FolderTree size={20} />
            </span>
            <div className="min-w-0">
              <small className="mb-0.5 block text-[11px] leading-[18px] text-[rgba(22,24,35,0.45)]">
                文件夹
              </small>
              <strong className="block overflow-hidden text-ellipsis whitespace-nowrap text-lg font-semibold leading-6 text-[#161823]">
                {summary.directories}
              </strong>
            </div>
          </div>

          <div className="flex min-h-[70px] min-w-0 items-center gap-3 border border-[#f0f0f0] bg-[#fafbfc] px-4 py-3">
            <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[7px] bg-[#f4f2ff] text-[#7568ed]">
              <HardDrive size={20} />
            </span>
            <div className="min-w-0">
              <small className="mb-0.5 block text-[11px] leading-[18px] text-[rgba(22,24,35,0.45)]">
                资源容量
              </small>
              <strong className="block overflow-hidden text-ellipsis whitespace-nowrap text-lg font-semibold leading-6 text-[#161823]">
                {formatFileSize(summary.totalBytes)}
              </strong>
            </div>
          </div>

          <div className="flex min-h-[70px] min-w-0 items-center gap-3 border border-[#f0f0f0] bg-[#fafbfc] px-4 py-3">
            <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[7px] bg-[#f1f8f5] text-[#5f8f79]">
              <Database size={20} />
            </span>

            <div className="min-w-0">
              <small className="mb-0.5 block text-[11px] leading-[18px] text-[rgba(22,24,35,0.45)]">
                存储插件
              </small>

              <div className="flex min-w-0 flex-wrap gap-[5px]">
                {storagePlugins.length ? (
                  storagePlugins.map((plugin) => (
                    <Tag
                      key={plugin.type}
                      bordered={false}
                      className={[
                        '!m-0 !h-[22px] !px-[7px] !leading-[22px]',
                        plugin.active
                          ? '!bg-[#eef8f2] !text-[#4d8c6b]'
                          : '!bg-[#f2f3f5] !text-[rgba(22,24,35,0.58)]',
                      ].join(' ')}
                    >
                      {plugin.name || plugin.type}
                      {plugin.active ? ' · 当前' : ''}
                    </Tag>
                  ))
                ) : (
                  <strong className="text-lg font-semibold text-[#161823]">
                    -
                  </strong>
                )}
              </div>
            </div>
          </div>
        </section>

        <section className="grid h-[calc(100vh-237px)] min-h-[560px] grid-cols-[250px_minmax(0,1fr)] overflow-hidden border border-[#e4e7ec] bg-white max-[1180px]:grid-cols-[220px_minmax(0,1fr)] max-[860px]:h-auto max-[860px]:min-h-0 max-[860px]:grid-cols-1">
          <aside className="min-w-0 border-r border-[#e4e7ec] bg-white max-[860px]:border-b max-[860px]:border-r-0">
            <div className="flex h-[50px] items-center justify-between border-b border-[#eaecf0] bg-white pl-3.5 pr-2.5">
              <div className="flex items-center gap-[7px] text-[#344054]">
                <FolderTree size={17} />
                <strong className="text-[13px] font-semibold">目录</strong>
              </div>

              <Tooltip title="刷新目录">
                <YakButton
                  type="text"
                  size="small"
                  disabled={treeLoading}
                  icon={
                    <RefreshCw
                      size={15}
                      className={treeLoading ? 'animate-spin' : ''}
                    />
                  }
                  onClick={refresh}
                />
              </Tooltip>
            </div>

            <Spin spinning={treeLoading}>
              <Tree
                className={TREE_CLASS_NAME}
                treeData={directoryTree}
                selectedKeys={[resourceKey(selectedDirectoryId)]}
                defaultExpandAll
                blockNode
                showLine={{ showLeafIcon: false }}
                onSelect={(keys) => {
                  const selectedKey = keys[0];
                  if (selectedKey === undefined) return;

                  const resource = findResource(
                    resourceTree,
                    String(selectedKey),
                  );

                  navigateToDirectory(resource?.id ?? ROOT_RESOURCE_ID);
                }}
              />
            </Spin>
          </aside>

          <main className="flex min-h-0 min-w-0 flex-col bg-white">
            <div className="flex min-h-[50px] items-center justify-between gap-4 border-b border-[#eaecf0] py-[7px] pl-4 pr-3 max-[860px]:items-stretch max-[860px]:flex-col">
              <div className="flex min-w-0 items-center gap-2.5">
                <Breadcrumb items={breadcrumbItems} />
                <span className="shrink-0 border-l border-[#e4e7ec] pl-2.5 text-xs text-[#98a2b3]">
                  {resourceList.length} 项
                </span>
              </div>

              <div className="flex items-center gap-2 max-[860px]:w-full">
                <Input
                  className={SEARCH_CLASS_NAME}
                  allowClear
                  prefix={<Search size={15} />}
                  value={keyword}
                  placeholder="搜索当前目录"
                  onChange={(event) => setKeyword(event.target.value)}
                />

                <Tooltip title="刷新列表">
                  <YakButton
                    icon={
                      <RefreshCw
                        size={15}
                        className={listLoading ? 'animate-spin' : ''}
                      />
                    }
                    disabled={listLoading}
                    onClick={refresh}
                  />
                </Tooltip>
              </div>
            </div>

            <Spin
              spinning={listLoading}
              className="min-h-0 flex-1 bg-white [&_.ant-spin-container]:h-full [&_.ant-spin-container]:bg-white"
            >
              <Table<ResourceItem>
                className={TABLE_CLASS_NAME}
                rowKey={(record) => resourceKey(record.id)}
                columns={columns}
                dataSource={resourceList}
                pagination={false}
                scroll={{ x: 1080 }}
                locale={{
                  emptyText: (
                    <div className="flex min-h-[calc(100vh-327px)] w-full flex-col items-center justify-center bg-white px-6 pb-16 pt-10 text-center max-[860px]:min-h-[360px]">
                      <img
                        src="/image/add.png"
                        alt=""
                        className="block h-28 w-28 shrink-0 object-contain"
                      />

                      <div className="mt-3.5 text-[13px] font-medium leading-5 text-[#667085]">
                        {debouncedKeyword
                          ? '没有找到匹配的资源'
                          : '当前文件夹为空'}
                      </div>

                      {!debouncedKeyword && (
                        <div className="mt-1 text-[11px] leading-[18px] text-[#b0b6c0]">
                          上传文件或在线创建一个资源
                        </div>
                      )}

                      {canCreate && !debouncedKeyword && (
                        <YakButton
                          className="mt-4"
                          type="primary"
                          icon={<Upload size={15} />}
                          onClick={() => uploadInputRef.current?.click()}
                        >
                          上传第一个文件
                        </YakButton>
                      )}
                    </div>
                  ),
                }}
                onRow={(resource) => ({
                  onDoubleClick: () => openResource(resource),
                })}
              />
            </Spin>
          </main>
        </section>

        <input
          ref={uploadInputRef}
          hidden
          type="file"
          onChange={(event) => void handleUpload(event.target.files?.[0])}
        />

        <input
          ref={replaceInputRef}
          hidden
          type="file"
          onChange={(event) => void handleReplaceFile(event.target.files?.[0])}
        />

        <CreateDirectoryModal
          open={directoryModalOpen}
          parentName={selectedDirectoryName}
          saving={saving}
          onCancel={() => setDirectoryModalOpen(false)}
          onSubmit={handleCreateDirectory}
        />

        <CreateTextResourceModal
          open={textModalOpen}
          parentName={selectedDirectoryName}
          saving={saving}
          onCancel={() => setTextModalOpen(false)}
          onSubmit={handleCreateTextResource}
        />

        <ResourceMetadataModal
          open={Boolean(metadataResource)}
          resource={metadataResource}
          saving={saving}
          onCancel={() => setMetadataResource(undefined)}
          onSubmit={handleUpdateMetadata}
        />

        <MoveResourceModal
          open={Boolean(movingResource)}
          resource={movingResource}
          directories={moveDirectoryTree}
          saving={saving}
          onCancel={() => setMovingResource(undefined)}
          onSubmit={handleMoveResource}
        />

        <ResourceDetailDrawer
          open={Boolean(detailResource)}
          resource={detailResource}
          canUpdate={canUpdate}
          canDownload={canDownload}
          onClose={() => setDetailResource(undefined)}
          onDownload={handleDownload}
          onReplace={requestReplaceFile}
          onSaved={refresh}
        />
      </div>
    </ConfigProvider>
  );
};

export default ResourceManagementPage;