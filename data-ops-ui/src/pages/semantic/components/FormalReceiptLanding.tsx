import React from 'react';
import { useSearchParams } from '@umijs/max';
import { Alert, Typography } from 'antd';
import { getSemanticField, getSemanticStandard } from '@/services/semantic/api';

/**
 * Exact formal ID is reloaded at the destination (not copied from Agent state).
 * Missing IDs, deleted objects, changed project and revoked permissions must
 * never silently fall back to another project's list or a same-name candidate.
 */
export const parseFormalReceiptId = (raw: string | null): number | null => {
  if (!raw || !/^[1-9][0-9]*$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
};

const FormalReceiptLanding: React.FC<{ kind: 'FIELD' | 'STANDARD' }> = ({ kind }) => {
  const [searchParams] = useSearchParams();
  const parameter = kind === 'FIELD' ? 'fieldId' : 'standardId';
  const rawId = searchParams.get(parameter);
  const id = parseFormalReceiptId(rawId);
  const [loaded, setLoaded] = React.useState<{
    id: number; code: string; name: string; status: string; kind: string; version?: number;
  } | null>(null);
  const [error, setError] = React.useState('');
  React.useEffect(() => {
    let current = true;
    setLoaded(null);setError('');
    if (rawId === null) return () => { current = false; };
    if (id === null) {
      setError('正式对象 ID 格式不正确；不能根据同名候选推定已有对象。');
      return () => { current = false; };
    }
    const get = kind === 'FIELD' ? getSemanticField(id) : getSemanticStandard(id);
    get.then((actual) => {
      if (!current) return;
      if (!actual || actual.id !== id) {
        setError('原 Semantic 对象不存在或不属于当前项目。');
        return;
      }
      setLoaded({
        id: actual.id, code: actual.code, name: actual.name,
        status: actual.status ?? 'UNKNOWN', kind, version: actual.version,
      });
    }).catch(() => {
      if (current) setError('无法从原 Semantic 域回读此 ID，可能已切换项目、撤销权限或对象已删除。');
    });
    return () => { current = false; };
  }, [id, kind, rawId]);
  if (rawId === null) return null;
  return <div style={{ margin: '10px 0' }} aria-label="F-039 正式回执原域核验">
    {loaded && <Alert type="success" showIcon message="正式对象已在当前项目重读成功"
      description={<>
        <Typography.Text strong>{loaded.name} · {loaded.code}</Typography.Text>
        <div>原 Semantic ID {loaded.id} · {loaded.kind} · {loaded.status}
          {loaded.version != null ? ` · v${loaded.version}` : ''}
        </div>
        <Typography.Text type="secondary">
          来源候选的确认不代表标准启用、模型创建或指标发布；请在此专业页面继续工作。
        </Typography.Text>
      </>} />}
    {error && <Alert type="warning" showIcon message={error} />}
    {!loaded && !error && <Alert type="info" showIcon message="正在按正式 ID 核对原 Semantic 对象" />}
  </div>;
};
export default FormalReceiptLanding;
