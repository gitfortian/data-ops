import { Alert, Button, Input, message, Modal, Space, Table, Tag, Tooltip, TreeSelect } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';

import UserSelect from '@/components/UserSelect';
import {
  batchMoveAssetsDirectory,
  changeAssetOwner,
  getAssetDetail,
  getDirectoryTree,
  precheckAssets,
  publishAssets,
  updateAssetSnapshot,
} from '@/services/data-asset/api';
import type { AssetRecord, DirNode, PrecheckResult } from '@/services/data-asset/types';
import { GAP_LABELS } from '../constants';
import AssetStatusTag from './AssetStatusTag';

interface RowRepair {
  owner?: string;
  description?: string;
  directoryId?: number;
}

interface DirTreeOption {
  title: string;
  value: number;
  children?: DirTreeOption[];
}

const toDirOptions = (nodes: DirNode[]): DirTreeOption[] =>
  nodes.map((node) => ({
    title: node.dirName,
    value: node.id,
    children: node.children?.length ? toDirOptions(node.children) : undefined,
  }));

const DESCRIPTION_PREFETCH_LIMIT = 20;

/**
 * 上架向导(design §6.2):预检缺口清单 → 逐项补齐(负责人默认=预检人,描述预取源域,
 * 目录必选) → 重新预检 → 上架;仍有阻断缺口时须勾选"带风险上架"留痕(48004)。
 */
const PublishPrecheckModal = ({
  open,
  assets,
  onClose,
  onDone,
}: {
  open: boolean;
  assets: AssetRecord[];
  onClose: () => void;
  onDone: () => void;
}) => {
  const [precheck, setPrecheck] = useState<PrecheckResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [acceptRisk, setAcceptRisk] = useState(false);
  const [repairs, setRepairs] = useState<Record<number, RowRepair>>({});
  const [dirOptions, setDirOptions] = useState<DirTreeOption[]>([]);

  const assetIds = useMemo(() => assets.map((asset) => asset.id), [assets]);

  const runPrecheck = useCallback(async () => {
    setLoading(true);
    try {
      const result = await precheckAssets(assetIds);
      setPrecheck(result);
      setAcceptRisk(false);
      // 缺描述的资产预取源域描述作默认值(能默认就不留空;超过上限的手动填)
      const needDesc = result.items.filter(
        (item) => item.gaps.includes('DESCRIPTION_MISSING'),
      );
      const defaultRepairs: Record<number, RowRepair> = {};
      await Promise.all(
        needDesc.slice(0, DESCRIPTION_PREFETCH_LIMIT).map(async (item) => {
          try {
            const detail = await getAssetDetail(item.assetId);
            const sourceDesc = detail.sections.sourceAttrs?.data?.description;
            if (sourceDesc) defaultRepairs[item.assetId] = { description: sourceDesc };
          } catch {
            // 源域不可用时留空,由用户填写
          }
        }),
      );
      setRepairs(defaultRepairs);
    } catch {
      setPrecheck(null);
    } finally {
      setLoading(false);
    }
  }, [assetIds]);

  useEffect(() => {
    if (!open) return;
    void runPrecheck();
    getDirectoryTree()
      .then((tree) => setDirOptions(toDirOptions(tree)))
      .catch(() => setDirOptions([]));
  }, [open, runPrecheck]);

  const patchRepair = (assetId: number, patch: RowRepair) =>
    setRepairs((prev) => ({ ...prev, [assetId]: { ...prev[assetId], ...patch } }));

  const applyRepairsAndRecheck = async () => {
    setApplying(true);
    try {
      const failures: string[] = [];
      for (const item of precheck?.items ?? []) {
        const repair = repairs[item.assetId];
        if (!repair) continue;
        try {
          if (repair.owner) await changeAssetOwner(item.assetId, repair.owner);
          if (repair.description)
            await updateAssetSnapshot(item.assetId, { description: repair.description });
        } catch {
          failures.push(item.name);
        }
      }
      const dirIds = Object.entries(repairs)
        .filter(([, repair]) => repair.directoryId != null)
        .reduce<Record<string, number[]>>((acc, [assetId, repair]) => {
          const key = String(repair.directoryId);
          acc[key] = [...(acc[key] ?? []), Number(assetId)];
          return acc;
        }, {});
      for (const [dirId, ids] of Object.entries(dirIds)) {
        try {
          await batchMoveAssetsDirectory(ids, Number(dirId));
        } catch {
          failures.push(`移目录 ${dirId}`);
        }
      }
      if (failures.length > 0) message.warning(`部分补齐失败：${failures.join('、')}`);
      await runPrecheck();
      if (failures.length === 0) message.success('已应用补齐并重新预检');
    } finally {
      setApplying(false);
    }
  };

  const doPublish = async () => {
    if (!precheck) return;
    setPublishing(true);
    try {
      const count = await publishAssets(assetIds, precheck.token, acceptRisk);
      message.success(`已上架 ${count} 个资产`);
      onDone();
    } catch {
      // 全局错误提示已展示原因(token 过期 48005 / 未接受风险 48004)
    } finally {
      setPublishing(false);
    }
  };

  const gapCount = precheck?.items.filter((item) => item.gaps.length > 0).length ?? 0;

  const columns: ColumnsType<PrecheckResult['items'][number]> = [
    {
      title: '资产',
      dataIndex: 'name',
      width: 200,
      render: (name: string, record) => (
        <div>
          <div className="truncate">{name}</div>
          <div className="text-[12px] text-[#98a2b3]">{record.assetKey}</div>
        </div>
      ),
    },
    { title: '状态', dataIndex: 'status', width: 90, render: (status) => <AssetStatusTag status={status} /> },
    {
      title: '缺口',
      dataIndex: 'gaps',
      width: 170,
      render: (gaps: string[], record) => (
        <Space size={4} wrap>
          {gaps.length === 0 && record.advisories.length === 0 ? (
            <Tag color="green">检查通过</Tag>
          ) : (
            <>
              {gaps.map((gap) => (
                <Tag key={gap} color="red">{GAP_LABELS[gap as keyof typeof GAP_LABELS] ?? gap}</Tag>
              ))}
              {record.advisories.map((gap) => (
                <Tooltip key={gap} title="定级仅对物理对象类资产适用,未定级不阻断上架">
                  <Tag color="orange">{GAP_LABELS[gap as keyof typeof GAP_LABELS] ?? gap}</Tag>
                </Tooltip>
              ))}
            </>
          )}
        </Space>
      ),
    },
    {
      title: '补齐(能选择就不填)',
      key: 'repair',
      render: (_, record) => {
        if (record.gaps.length === 0) return <span className="text-[#98a2b3]">无需补齐</span>;
        const repair = repairs[record.assetId] ?? {};
        return (
          <Space direction="vertical" size={6} className="w-full">
            {record.gaps.includes('OWNER_MISSING') && (
              <UserSelect
                value={repair.owner ? [repair.owner] : []}
                onChange={(value) => patchRepair(record.assetId, { owner: value[value.length - 1] })}
                placeholder={`默认当前用户 ${record.defaults.owner ?? ''}`}
                max={1}
              />
            )}
            {record.gaps.includes('DESCRIPTION_MISSING') && (
              <Input
                value={repair.description}
                onChange={(event) => patchRepair(record.assetId, { description: event.target.value })}
                placeholder="描述(已尽量从源域带出)"
                maxLength={1024}
              />
            )}
            {record.gaps.includes('DIRECTORY_MISSING') && (
              <TreeSelect
                treeDefaultExpandAll
                className="w-full"
                placeholder="选择归入目录"
                value={repair.directoryId}
                treeData={dirOptions}
                onChange={(value) => patchRepair(record.assetId, { directoryId: value })}
              />
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <Modal
      open={open}
      title="上架预检"
      width={920}
      onCancel={onClose}
      destroyOnClose
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button loading={applying} disabled={loading || !precheck} onClick={applyRepairsAndRecheck}>
            应用补齐并重检
          </Button>
          <Button
            type="primary"
            danger={!precheck?.allClear}
            loading={publishing}
            disabled={!precheck || (gapCount > 0 && !acceptRisk)}
            onClick={doPublish}
            className="!text-white"
          >
            {gapCount > 0 ? `带风险上架(${gapCount})` : '确认上架'}
          </Button>
        </Space>
      }
    >
      {precheck && !precheck.allClear && (
        <Alert
          className="!mb-3"
          type="warning"
          showIcon
          message={`有 ${gapCount} 个资产存在阻断缺口`}
          description={
            <Space direction="vertical" size={4}>
              <span>
                建议先"应用补齐并重检"；负责人默认当前用户、描述从源域带出、目录需选择。
              </span>
              <label className="cursor-pointer select-none">
                <input
                  type="checkbox"
                  className="mr-1 accent-[#FE2C55]"
                  checked={acceptRisk}
                  onChange={(event) => setAcceptRisk(event.target.checked)}
                />
                我确认带风险上架(缺口清单将写入审计留痕)
              </label>
            </Space>
          }
        />
      )}
      {precheck?.allClear && (
        <Alert className="!mb-3" type="success" showIcon message="全部资产预检通过，可直接上架" />
      )}
      <div className="mb-2 text-[12px] text-[#667085]">
        预检令牌 {precheck ? `${Math.round(precheck.expiresIn / 60)} 分钟` : '-'}
        内有效；过期后点"应用补齐并重检"重新获取。
      </div>
      <Table
        rowKey="assetId"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={precheck?.items ?? []}
        pagination={false}
        scroll={{ y: 380 }}
      />
    </Modal>
  );
};

export default PublishPrecheckModal;
