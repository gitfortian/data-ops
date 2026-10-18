import { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Empty, Space, Spin, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import {
  getAdsApplicationEvidence,
  type AdsApplicationEvidenceSnapshot,
  type AdsHandoffEvidenceItem,
} from '@/services/modeling/adsApplication';

interface Props {
  modelId: number;
  refreshKey?: number;
}

const DOMAIN_LABELS: Record<string, string> = {
  METRIC: '指标发布',
  DATADEV_RUNTIME: '数据开发 / 运行',
  QUALITY: '数据质量',
  DATASET: 'Dataset 交付',
  DATA_SERVICE: 'Data Service',
  CONSUMPTION_AUTHORIZATION: '消费授权',
};

const STATUS_LABELS: Record<string, string> = {
  VERIFIED: '已核验',
  NOT_CONFIGURED: '未配置',
  NOT_CONNECTED: '尚未接入',
  NO_RECORD: '无记录',
  PENDING: '等待中',
  RUNNING: '运行中',
  SUCCEEDED: '运行成功',
  FAILED: '运行失败',
  STALE: '版本/证据失效',
  FORBIDDEN: '无权读取',
  UNKNOWN: '未知 / 无法核验',
  NOT_APPLICABLE: '不适用',
};

const statusColor = (status: string) => {
  if (status === 'VERIFIED' || status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED' || status === 'FORBIDDEN' || status === 'STALE') return 'error';
  if (status === 'NOT_CONFIGURED' || status === 'NOT_CONNECTED' || status === 'NOT_APPLICABLE') return 'default';
  if (status === 'RUNNING' || status === 'PENDING') return 'processing';
  return 'warning';
};

const shortDigest = (digest?: string | null) => {
  if (!digest) return null;
  return digest.length > 16 ? `${digest.slice(0, 12)}…${digest.slice(-4)}` : digest;
};

export default function AdsHandoffEvidencePanel({ modelId, refreshKey = 0 }: Props) {
  const [snapshot, setSnapshot] = useState<AdsApplicationEvidenceSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    getAdsApplicationEvidence(modelId).then((result) => {
      if (active) setSnapshot(result);
    }).catch((reason: unknown) => {
      if (active) {
        setSnapshot(null);
        setError(reason instanceof Error ? reason.message : '读取交接证据失败');
      }
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [modelId, refreshKey, retryKey]);

  const retry = () => setRetryKey((value) => value + 1);

  return (
    <Card
      size="small"
      title="交接证据（只读）"
      extra={<Button size="small" icon={<ReloadOutlined />} onClick={retry} loading={loading}>刷新证据</Button>}
    >
      <Typography.Paragraph type="secondary" className="!mb-3">
        状态由当前项目内可证明的来源证据派生。合同保存不代表任务已运行、质量已通过或消费方已获授权。
      </Typography.Paragraph>
      {loading && <Spin tip="正在核验交接证据"><div className="h-10" /></Spin>}
      {!loading && error && <Alert
        type="error"
        showIcon
        message="交接证据读取失败"
        description={error + '。未将请求失败转换为空记录或成功状态。'}
        action={<Button size="small" onClick={retry}>重试</Button>}
      />}
      {!loading && !error && !snapshot && <Empty description="没有可展示的证据快照" />}
      {!loading && !error && snapshot && (
        <>
          <Space wrap className="!mb-3">
            <Tag>ADS 模型 #{snapshot.modelId}</Tag>
            {snapshot.contractRevision != null
              ? <Tag>合同 R{snapshot.contractRevision}</Tag>
              : <Tag>合同未配置</Tag>}
            {snapshot.contractSha256 && (
              <Typography.Text copyable={{ text: snapshot.contractSha256 }}>
                合同哈希 {shortDigest(snapshot.contractSha256)}
              </Typography.Text>
            )}
            <Typography.Text type="secondary">核验时间：{snapshot.checkedAt}</Typography.Text>
          </Space>
          <Descriptions bordered size="small" column={1}>
            {snapshot.domains.map((item: AdsHandoffEvidenceItem) => (
              <Descriptions.Item
                key={item.domainCode}
                label={DOMAIN_LABELS[item.domainCode] ?? item.domainCode}
              >
                <Space direction="vertical" size={2} className="w-full">
                  <Space wrap>
                    <Tag color={statusColor(item.status)}>{STATUS_LABELS[item.status] ?? item.status}</Tag>
                    {item.sourceStatus && item.sourceStatus !== item.status && (
                      <Tag>{item.sourceStatus}</Tag>
                    )}
                    <Typography.Text type="secondary">{item.sourceDomain}</Typography.Text>
                  </Space>
                  <Typography.Text>{item.summary}</Typography.Text>
                  {(item.evidenceId || item.version || item.digest) && (
                    <Space wrap>
                      {item.evidenceId && <Typography.Text type="secondary">证据 ID：{item.evidenceId}</Typography.Text>}
                      {item.version && <Typography.Text type="secondary">版本：{item.version}</Typography.Text>}
                      {item.digest && (
                        <Typography.Text type="secondary" copyable={{ text: item.digest }}>
                          摘要：{shortDigest(item.digest)}
                        </Typography.Text>
                      )}
                    </Space>
                  )}
                </Space>
              </Descriptions.Item>
            ))}
          </Descriptions>
        </>
      )}
    </Card>
  );
}
