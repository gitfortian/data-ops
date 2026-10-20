import { history } from '@umijs/max';
import { Alert, Button, Select, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { listAllDataSources } from '@/services/data-source/api';
import LiveSchemaExplorer from '@/pages/data-source/components/LiveSchemaExplorer';
import type { LiveSourceCoordinate } from '@/utils/liveSourceOdsJourney';
import '@/pages/data-source/index.less';
import { buildLiveSourceOdsPath } from '@/utils/liveSourceOdsJourney';

/**
 * Modeling-home entry to the SAME ODS design workspace as the live DataSource entry.
 * The source picker only supplies coordinates, not trusted column JSON or access.
 * Modeling/DataSource recheck every identity and column on the destination.
 */
export default function OdsSourceSelector() {
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const projectId = currentProject?.id;
  const allowed = can('modeling:read') && can('resource:data-source:read');
  const [sources, setSources] = useState<{ id: number; name: string; dbType?: string }[]>([]);
  const [selectedId, setSelectedId] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setSources([]);
    setSelectedId(undefined);
    setError('');
    if (!allowed || !projectId) return () => { active = false; };
    setLoading(true);
    void listAllDataSources().then(response => {
      if (!active) return;
      const available = (response.bizData ?? []).filter(item => Number.isSafeInteger(Number(item.id))
        && Number(item.id) > 0).map(item => ({
        id: Number(item.id), name: String(item.name || '未命名数据源'),
        dbType: String(item.dbType || ''),
      }));
      setSources(available);
    }).catch(e => {
      if (active) setError(e instanceof Error ? e.message : '数据源读取失败，请稍后重试');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [allowed, projectId]);

  const openSource = (source: LiveSourceCoordinate) => {
    const route = buildLiveSourceOdsPath(source);
    if (!route || Number(source.dataSourceId) !== selectedId
        || !sources.some(item => item.id === selectedId)) {
      setError('当前项目无法核验选中的来源表，请重新探查后再进入。');
      return;
    }
    const search = new URLSearchParams(route.split('?')[1]);
    // The two entries share one editor, but their return destinations differ.
    search.set('origin', 'modeling-home');
    history.push('/modeling?' + search.toString());
  };

  return (
    <div className="ods-workbench" data-testid="ods-source-workbench">
      <header className="ods-workbench-head">
        <div>
          <h1>新建 ODS · 选择来源表</h1>
          <p>先确定真实来源，再自动带入字段、主键和初始映射；不要求先建立业务过程或逻辑模型。</p>
        </div>
        <Button onClick={() => history.push('/modeling?view=physical')}>返回物理模型</Button>
      </header>
      <main className="ods-workbench-main" aria-label="ODS 来源选择">
        <section className="ods-workbench-card">
          <div className="ods-workbench-section-title">
            <div><h2>选择当前项目的数据源</h2><p>选择数据源后按 Database / Schema / 表进行精确探查，仅真实 TABLE 可用于 ODS 首建。</p></div>
          </div>
          {!allowed ? <Alert type="warning" showIcon message="需要数据源读取和建模读取权限才能选择来源" /> : null}
          {!projectId ? <Alert type="warning" showIcon message="请先选择项目" /> : null}
          {error ? <Alert className="ods-workbench-alert" type="error" showIcon message={error} /> : null}
          <Spin spinning={loading}>
            <Select showSearch optionFilterProp="label" className="!w-full max-w-[460px]"
              aria-label="ODS 来源数据源"
              placeholder="选择已授权的数据源"
              disabled={!allowed || !projectId || loading}
              value={selectedId}
              onChange={setSelectedId}
              options={sources.map(item => ({ value: item.id, label: item.name + (item.dbType ? ' · ' + item.dbType : '') }))} />
          </Spin>
          {allowed && projectId && !loading && sources.length === 0 && !error ? (
            <Alert className="!mt-3" type="info" showIcon message="当前项目没有可读取的数据源，请先到数据源模块创建连接。" />
          ) : null}
        </section>
        {selectedId && allowed && projectId && sources.some(item => item.id === selectedId) ? (
          <section className="ods-workbench-card" aria-label="浏览来源库表">
            <div className="ods-workbench-section-title">
              <div><h2>选择真实物理表</h2><p>展开字段核验后选择“打开或创建 ODS”，进入同一 ODS 模型设计页面。</p></div>
            </div>
            <LiveSchemaExplorer key={String(projectId) + '-' + selectedId}
              sourceId={String(selectedId)} canRead onOpenOds={openSource} />
          </section>
        ) : null}
      </main>
    </div>
  );
}
