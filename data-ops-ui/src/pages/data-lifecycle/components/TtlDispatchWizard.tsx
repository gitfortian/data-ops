import { Alert, Checkbox, message, Modal, Spin, Tag, Tooltip } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { YakButton } from '@/components/ui';
import { confirmDispatch, previewDispatch } from '@/services/data-lifecycle/api';
import type { DispatchOutcome, ModelPreview, PreviewBatch } from '@/services/data-lifecycle/types';
import { formatRetention } from '../constants';

interface TtlDispatchWizardProps {
  open: boolean;
  modelIds: number[];
  onClose: () => void;
  onDone: () => void;
}

const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text);
    message.success('语句已复制');
  } catch {
    message.error('复制失败，请手动选择复制');
  }
};

const PreviewCard = ({
  preview,
  selected,
  onToggle,
}: {
  preview: ModelPreview;
  selected: boolean;
  onToggle: (id: number, checked: boolean) => void;
}) => {
  const { split } = preview;
  return (
    <div
      className={`mb-3 rounded-[10px] border p-4 ${
        preview.dispatchable ? 'border-[#e5e7eb] bg-white' : 'border-[#e5e7eb] bg-[#f9fafb]'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Checkbox
              disabled={!preview.dispatchable}
              checked={selected}
              onChange={(e) => onToggle(preview.modelId, e.target.checked)}
            />
            <span className="text-[14px] font-semibold text-[#101828]">{preview.modelName}</span>
            <Tag>{preview.tableName}</Tag>
            {split.estimated && (
              <Tooltip title={split.estimatedReason ?? '无法直查分区清单，按保留期估算'}>
                <Tag color="orange">估算值</Tag>
              </Tooltip>
            )}
            {!preview.dispatchable && (
              <Tooltip title={preview.notDispatchableReason ?? '不可下发'}>
                <Tag color="red">不可下发</Tag>
              </Tooltip>
            )}
          </div>
          <div className="mt-1 text-[12px] text-[#667085]">
            策略：{preview.policyName ?? '-'}（{preview.policySource}）·{' '}
            {formatRetention(preview.hotDays, null, preview.destroyDays)}
            {preview.nextCleanup ? ` · 预计最近清理：${preview.nextCleanup}` : ''}
          </div>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-4 text-[13px]">
        <span>
          <Tag color="blue">热 {split.hotCount}</Tag>
          {split.hotRange ?? '-'}
        </span>
        <span>
          <Tag color="default">冷 {split.coldCount}</Tag>
          {split.coldRange ?? '-'}
        </span>
        <span>
          <Tag color={split.deletedCount > 0 ? 'red' : 'green'}>将被删除 {split.deletedCount}</Tag>
        </span>
      </div>
      {split.deletedPartitions.length > 0 && (
        <div className="mt-2 max-h-[88px] overflow-auto">
          {split.deletedPartitions.map((p) => (
            <Tag key={p} color="red" className="!mb-1">
              {p}
            </Tag>
          ))}
          {split.deletedCount > split.deletedPartitions.length && (
            <span className="text-[12px] text-[#98a2b3]">… 仅展示前 {split.deletedPartitions.length} 个</span>
          )}
        </div>
      )}

      {preview.statement && (
        <div className="mt-3 rounded-lg bg-[#0b1021] p-3">
          <div className="mb-1 flex items-center justify-between">
            <span className="text-[12px] text-[#98a2b3]">将下发的 TTL 语句</span>
            <YakButton size="small" className="!h-6 !px-2 !text-[12px]" onClick={() => void copyText(preview.statement ?? '')}>
              复制
            </YakButton>
          </div>
          <pre className="m-0 overflow-auto whitespace-pre-wrap text-[12px] leading-5 text-[#d0d6e4]">
            {preview.statement}
          </pre>
        </div>
      )}
    </div>
  );
};

const TtlDispatchWizard = ({ open, modelIds, onClose, onDone }: TtlDispatchWizardProps) => {
  const [loading, setLoading] = useState(false);
  const [dispatching, setDispatching] = useState(false);
  const [batch, setBatch] = useState<PreviewBatch | null>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [consented, setConsented] = useState(false);
  const [outcomes, setOutcomes] = useState<DispatchOutcome[] | null>(null);
  const issuedAtRef = useRef(0);

  const runPreview = useCallback(async () => {
    setLoading(true);
    setBatch(null);
    setOutcomes(null);
    setConsented(false);
    try {
      const result = await previewDispatch(modelIds);
      setBatch(result);
      issuedAtRef.current = Date.now();
      setSelectedIds((result.previews ?? []).filter((p) => p.dispatchable).map((p) => p.modelId));
    } catch {
      message.error('预览失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, [modelIds]);

  useEffect(() => {
    if (open && modelIds.length > 0) void runPreview();
    if (!open) {
      setBatch(null);
      setOutcomes(null);
      setSelectedIds([]);
      setConsented(false);
    }
  }, [open, runPreview, modelIds.length]);

  const tokenExpired = useMemo(
    () => batch != null && Date.now() - issuedAtRef.current > batch.tokenExpiresInSeconds * 1000,
    [batch, outcomes],
  );

  const totalDeleted = (batch?.previews ?? []).reduce(
    (sum, p) => sum + (selectedIds.includes(p.modelId) ? p.split.deletedCount : 0),
    0,
  );
  const canDispatch =
    !tokenExpired && batch != null && selectedIds.length > 0 && (totalDeleted === 0 || consented);

  const handleDispatch = async () => {
    if (!batch) return;
    setDispatching(true);
    try {
      const result = await confirmDispatch(selectedIds, batch.confirmToken);
      setOutcomes(result ?? []);
      const failed = (result ?? []).filter((o) => o.attempted && !o.success).length;
      if (failed === 0) message.success('下发完成');
      onDone();
    } catch {
      // 全局错误提示已展示原因（如 47009 令牌过期需重新预览）
    } finally {
      setDispatching(false);
    }
  };

  const successCount = (outcomes ?? []).filter((o) => o.success).length;

  return (
    <Modal
      open={open}
      title={outcomes ? '下发结果' : 'TTL 预览与下发'}
      width={860}
      maskClosable={false}
      onCancel={() => {
        if (!dispatching) onClose();
      }}
      footer={
        outcomes ? (
          <YakButton type="primary" className="!rounded-lg !text-white" onClick={onClose}>
            完成
          </YakButton>
        ) : (
          <div className="flex items-center justify-between">
            <div className="text-[12px] text-[#667085]">
              {tokenExpired ? (
                <span className="text-[#d92d20]">预览已过期（超过有效期），请重新预览后再下发</span>
              ) : (
                batch && `已选 ${selectedIds.length}/${batch.previews.length} 个模型 · 预计删除 ${totalDeleted} 个分区`
              )}
            </div>
            <div className="flex gap-2">
              <YakButton disabled={dispatching} onClick={tokenExpired ? runPreview : onClose} className="!rounded-lg">
                {tokenExpired ? '重新预览' : '取消'}
              </YakButton>
              <YakButton
                type="primary"
                loading={dispatching}
                disabled={!canDispatch || loading}
                className="!rounded-lg !text-white"
                onClick={() => void handleDispatch()}
              >
                确认下发（{selectedIds.length}）
              </YakButton>
            </div>
          </div>
        )
      }
    >
      {outcomes ? (
        <div className="max-h-[60vh] overflow-auto py-2">
          {outcomes.map((o) => (
            <div key={o.modelId} className="mb-2 flex items-start justify-between gap-3 rounded-lg border border-[#e5e7eb] p-3">
              <div className="min-w-0">
                <span className="text-[13px] font-medium">{o.modelName}</span>
                {o.message && <div className="mt-1 break-all text-[12px] text-[#667085]">{o.message}</div>}
              </div>
              <Tag color={!o.attempted ? 'default' : o.success ? 'green' : 'red'}>
                {!o.attempted ? '未执行' : o.success ? '成功' : '失败'}
              </Tag>
            </div>
          ))}
          <div className="mt-2 text-[12px] text-[#98a2b3]">
            成功 {successCount}/{outcomes.length}；失败记录可在「TTL 监控 → 下发记录」中重试。
          </div>
        </div>
      ) : (
        <Spin spinning={loading}>
          <div className="max-h-[62vh] overflow-auto py-2">
            {(batch?.previews ?? []).map((p) => (
              <PreviewCard
                key={p.modelId}
                preview={p}
                selected={selectedIds.includes(p.modelId)}
                onToggle={(id, checked) =>
                  setSelectedIds((prev) => (checked ? [...prev, id] : prev.filter((x) => x !== id)))
                }
              />
            ))}
            {batch && batch.previews.length === 0 && (
              <Alert type="info" showIcon message="所选模型均不可预览（未配置分区或缺少分层配置）" />
            )}
          </div>
          {totalDeleted > 0 && (
            <Checkbox
              className="mt-2"
              checked={consented}
              onChange={(e) => setConsented(e.target.checked)}
            >
              <span className="text-[13px] text-[#d92d20]">
                我已知悉：本次将导致 {totalDeleted} 个过期分区在存储侧被自动清理，数据删除后不可恢复
              </span>
            </Checkbox>
          )}
        </Spin>
      )}
    </Modal>
  );
};

export default TtlDispatchWizard;
