import { Button, message } from 'antd';
import { ChevronDown, Copy, Download, FileCode2, RefreshCw } from 'lucide-react';
import { useState } from 'react';
import SqlCodeEditor from '@/components/SqlCodeEditor';
import { useLatestOperation } from '@/hooks/useLatestOperation';
import { generateModelingDdl, getModelingModel } from '@/services/modeling/api';
import type { ModelingDdlRecord, ModelingModelRecord } from '@/services/modeling/types';

interface Props { model: ModelingModelRecord; scope: string }

export default function ModelDdlPanel({ model, scope }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [ddl, setDdl] = useState<ModelingDdlRecord>();
  const [error, setError] = useState('');
  const beginRead = useLatestOperation(scope);
  const hasVersion = Boolean(model.publishedVersionId && Number(model.latestVersionNo) > 0);

  const load = async () => {
    if (!model.id || !hasVersion) return;
    const current = beginRead();
    setLoading(true); setError(''); setDdl(undefined);
    try {
      const script = await generateModelingDdl(model.id);
      const latest = await getModelingModel(model.id);
      if (!current()) return;
      if (latest.publishedVersionId !== model.publishedVersionId || latest.latestVersionNo !== model.latestVersionNo) {
        throw new Error('发布版本已变化，请刷新模型后重新查看脚本');
      }
      setDdl(script);
    } catch (failure) {
      if (current()) setError(failure instanceof Error ? failure.message : '脚本暂不可读取');
    } finally { if (current()) setLoading(false); }
  };

  const copy = async () => {
    if (!ddl?.script) return;
    try { await navigator.clipboard.writeText(ddl.script); message.success('脚本已复制'); }
    catch { message.error('复制失败，请手动选择脚本复制'); }
  };
  const download = () => {
    if (!ddl?.script) return;
    const url = URL.createObjectURL(new Blob([ddl.script], { type: 'text/plain;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = `${model.code || 'model'}.sql`; anchor.click();
    URL.revokeObjectURL(url);
  };

  return <section className="model-physical-card model-physical-ddl">
    <div className="model-physical-card-heading">
      <button type="button" className="model-physical-fold" aria-expanded={expanded}
        aria-controls="model-physical-ddl-content" onClick={() => {
          setExpanded(!expanded);
          if (!expanded && !ddl && !loading && hasVersion) void load();
        }}>
        <FileCode2 size={16} aria-hidden /><h3>建表脚本</h3>
        <ChevronDown size={15} className={expanded ? 'is-expanded' : ''} aria-hidden />
      </button>
      <span className="model-physical-muted">{hasVersion ? `发布版本 V${model.latestVersionNo}` : '尚未发布'} · 仅生成</span>
    </div>
    {expanded && <div id="model-physical-ddl-content" className="model-physical-ddl-content">
      <div className="model-physical-ddl-tools">
        <span>{ddl?.dialect || model.dialect || 'SQL'}</span>
        <Button size="small" icon={<RefreshCw size={13} />} loading={loading} disabled={!hasVersion} onClick={() => void load()}>重新生成</Button>
        <Button size="small" icon={<Copy size={13} />} disabled={!ddl?.script || loading} onClick={() => void copy()}>复制</Button>
        <Button size="small" icon={<Download size={13} />} disabled={!ddl?.script || loading} onClick={download}>下载 .sql</Button>
      </div>
      {loading ? <div className="model-physical-empty">正在生成脚本…</div>
        : error ? <div className="model-physical-error" role="alert">{error}</div>
        : !hasVersion ? <div className="model-physical-empty">发布模型后可查看建表脚本</div>
        : ddl?.script ? <SqlCodeEditor value={ddl.script} onChange={() => undefined} dbType={ddl.dialect}
          readOnly wordWrap="off" minRows={10} maxRows={20} fullscreenTitle="建表脚本" />
        : <div className="model-physical-empty">当前版本暂无脚本</div>}
    </div>}
  </section>;
}
