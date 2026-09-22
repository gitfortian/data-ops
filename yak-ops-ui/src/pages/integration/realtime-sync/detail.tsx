import { history, useParams } from '@umijs/max';
import { message, Spin } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { realtimeApi } from './api';
import RealtimeExecutionPanel from './RealtimeExecutionPanel';
import WizardJobEditor from './WizardJobEditor';
import YamlJobEditor from './YamlJobEditor';
import {
  type RealtimeEditorMode,
  wizardCompatibility,
} from './realtimeEditorMode';
import type { CdcPipelineSpec, DataSourceOption, RealtimeJob } from './types';

const editorFromSearch = (): RealtimeEditorMode | undefined => {
  const value = new URLSearchParams(history.location.search).get('editor');
  return value === 'wizard' || value === 'yaml' ? value : undefined;
};

const replaceEditorQuery = (mode: RealtimeEditorMode) => {
  const params = new URLSearchParams(history.location.search);
  params.set('editor', mode);
  history.replace(`${history.location.pathname}?${params.toString()}`);
};

export default function RealtimeSyncDetail() {
  const { id } = useParams<{ id: string }>();
  const [job, setJob] = useState<RealtimeJob>();
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [editorMode, setEditorMode] = useState<RealtimeEditorMode>();
  const [draftSpec, setDraftSpec] = useState<CdcPipelineSpec>();
  const [executionReady, setExecutionReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([realtimeApi.detail(Number(id)), realtimeApi.dataSources()])
      .then(([detail, sources]) => {
        if (cancelled) return;
        const nextJob = detail.data;
        const requestedMode = editorFromSearch();
        const compatibility = wizardCompatibility(nextJob.spec);
        const resolvedMode: RealtimeEditorMode =
          requestedMode === 'yaml'
            ? 'yaml'
            : requestedMode === 'wizard' && compatibility.supported
              ? 'wizard'
              : compatibility.supported
                ? 'wizard'
                : 'yaml';

        if (requestedMode === 'wizard' && !compatibility.supported && compatibility.reason) {
          message.warning(compatibility.reason);
        }

        setJob(nextJob);
        setDataSources(sources.data || []);
        setEditorMode(resolvedMode);
        setDraftSpec(undefined);
        replaceEditorQuery(resolvedMode);
      })
      .catch((error) => message.error(error?.message || '加载实时同步配置失败'));
    return () => {
      cancelled = true;
    };
  }, [id]);

  const editorJob = useMemo(() => {
    if (!job || !draftSpec) return job;
    return { ...job, spec: draftSpec };
  }, [draftSpec, job]);

  const handleSaved = async () => {
    if (!job) return;
    try {
      const refreshed = await realtimeApi.detail(job.id);
      setJob(refreshed.data);
      setDraftSpec(undefined);
      setExecutionReady(true);
    } catch (error: any) {
      message.error(error?.message || '配置已保存，但刷新执行状态失败');
      history.push('/sync/realtime');
    }
  };

  const handleSwitchMode = (nextMode: RealtimeEditorMode, spec: CdcPipelineSpec) => {
    if (nextMode === 'wizard') {
      const compatibility = wizardCompatibility(spec);
      if (!compatibility.supported) {
        message.warning(compatibility.reason || '当前配置无法切换到向导模式');
        return;
      }
    }

    setDraftSpec(spec);
    setEditorMode(nextMode);
    replaceEditorQuery(nextMode);
  };

  if (!job || !editorJob || !editorMode) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spin />
      </div>
    );
  }

  if (executionReady) {
    return (
      <RealtimeExecutionPanel
        job={job}
        onEdit={() => setExecutionReady(false)}
        onBack={() => history.push('/sync/realtime')}
      />
    );
  }

  if (editorMode === 'yaml') {
    return (
      <YamlJobEditor
        key={`yaml-${job.id}-${draftSpec ? 'draft' : 'saved'}`}
        job={editorJob}
        onClose={() => history.push('/sync/realtime')}
        onSaved={() => void handleSaved()}
        onSwitchMode={handleSwitchMode}
      />
    );
  }

  return (
    <WizardJobEditor
      key={`wizard-${job.id}-${draftSpec ? 'draft' : 'saved'}`}
      job={editorJob}
      dataSources={dataSources}
      onClose={() => history.push('/sync/realtime')}
      onSaved={() => void handleSaved()}
      onSwitchMode={handleSwitchMode}
    />
  );
}
