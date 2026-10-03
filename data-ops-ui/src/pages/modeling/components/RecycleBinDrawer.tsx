import { Button, Drawer, Input, Modal, message, Table, type TableColumnsType } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { formatModelingTime, MODELING_DIALECT_LABELS } from '@/pages/modeling/constants';
import { pageDeletedModelingModels, listModelingDirectories, purgeModelingModel, restoreModelingModel } from '@/services/modeling/api';
import type { ModelingDirectoryRecord, ModelingModelRecord } from '@/services/modeling/types';

interface RecycleBinDrawerProps {
  open: boolean;
  onClose: () => void;
  /** 回收站发生变化（恢复/彻底删除）后由父组件刷新主列表。 */
  onChanged: () => void | Promise<void>;
}

/** 回收站：软删除模型列表，支持恢复与彻底删除。 */
const RecycleBinDrawer = ({ open, onClose, onChanged }: RecycleBinDrawerProps) => {
  const [records, setRecords] = useState<ModelingModelRecord[]>([]);
  const [directories, setDirectories] = useState<ModelingDirectoryRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [actionId, setActionId] = useState<ModelingModelRecord['id']>();

  const loadRecords = useCallback(async (page: number, size: number, search: string) => {
    setLoading(true);
    try {
      const data = await pageDeletedModelingModels({
        pageNo: page,
        pageSize: size,
        keyword: search.trim() || undefined,
      });
      setRecords(data?.bizData || []);
      setTotal(data?.pagination?.total || 0);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '回收站加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) void loadRecords(pageNo, pageSize, keyword);
  }, [open, loadRecords, pageNo, pageSize, keyword]);

  // 「原目录」列只要名称；拉不到就显示「-」，不影响恢复/删除主流程。
  useEffect(() => {
    if (!open) return;
    void listModelingDirectories()
      .then((list) => setDirectories(list || []))
      .catch(() => setDirectories([]));
  }, [open]);

  const handleRestore = async (record: ModelingModelRecord) => {
    if (actionId) return;
    setActionId(record.id);
    try {
      await restoreModelingModel(record.id!);
      message.success('模型已恢复到原目录');
      await loadRecords(pageNo, pageSize, keyword);
      await onChanged();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '恢复失败');
    } finally {
      setActionId(undefined);
    }
  };

  const handlePurge = (record: ModelingModelRecord) => {
    Modal.confirm({
      centered: true,
      title: '彻底删除模型',
      content: `彻底删除后「${record.name || record.code}」不可恢复，确定继续吗？`,
      okText: '彻底删除',
      cancelText: '取消',
      okType: 'primary',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      async onOk() {
        if (actionId) return;
        setActionId(record.id);
        try {
          await purgeModelingModel(record.id!);
          message.success('模型已彻底删除');
          await loadRecords(pageNo, pageSize, keyword);
          await onChanged();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '彻底删除失败');
        } finally {
          setActionId(undefined);
        }
      },
    });
  };

  const directoryNameById = useMemo(() => {
    const map = new Map<number, string>();
    directories.forEach((directory) => map.set(directory.id!, directory.name!));
    return map;
  }, [directories]);

  const columns: TableColumnsType<ModelingModelRecord> = [
    { title: '模型名称', dataIndex: 'name', width: 170 },
    { title: '模型编码', dataIndex: 'code', width: 150 },
    {
      title: '目标方言',
      dataIndex: 'dialect',
      width: 100,
      render: (value: string) => MODELING_DIALECT_LABELS[value] || value,
    },
    {
      title: '原目录',
      dataIndex: 'directoryId',
      width: 120,
      render: (value?: number) =>
        value ? directoryNameById.get(value) || '-' : <span className="text-[#667085]">未分类</span>,
    },
    {
      title: '更新人',
      dataIndex: 'updatedBy',
      width: 110,
      render: (value?: string) => value || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 165,
      render: (value?: string) => formatModelingTime(value),
    },
    {
      title: '删除人',
      dataIndex: 'deletedBy',
      width: 110,
      render: (value?: string) => value || '-',
    },
    {
      title: '删除时间',
      dataIndex: 'deletedTime',
      width: 165,
      render: (value?: string) => formatModelingTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <>
          <YakButton
            className="!h-7 !rounded-lg !px-2 !text-[13px]"
            disabled={Boolean(actionId && actionId !== record.id)}
            loading={actionId === record.id}
            onClick={() => void handleRestore(record)}
          >
            恢复
          </YakButton>
          <Button
            type="link"
            size="small"
            danger
            disabled={Boolean(actionId && actionId !== record.id)}
            onClick={() => handlePurge(record)}
          >
            彻底删除
          </Button>
        </>
      ),
    },
  ];

  return (
    <Drawer
      open={open}
      width={1280}
      title="回收站"
      onClose={() => {
        if (!loading) onClose();
      }}
      destroyOnClose
      styles={{ body: { paddingTop: 12 } }}
    >
      <div className="mb-3">
        <Input.Search
          allowClear
          placeholder="按名称或编码搜索"
          className="!w-[260px]"
          onSearch={(value) => {
            setKeyword(value);
            setPageNo(1);
          }}
        />
      </div>
      <Table<ModelingModelRecord>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        scroll={{ x: 'max-content' }}
        locale={{
          emptyText: <YakEmpty compact title="回收站是空的" />,
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (t, range) => `第 ${range[0]}-${range[1]} 条，共 ${t} 条`,
          onChange: (nextPage, nextPageSize) => {
            setPageNo(nextPageSize !== pageSize ? 1 : nextPage);
            setPageSize(nextPageSize);
          },
        }}
      />
    </Drawer>
  );
};

export default RecycleBinDrawer;
