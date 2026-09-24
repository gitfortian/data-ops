import { useIntl } from '@umijs/max';
import {
  Check,
  CircleDot,
  CloudUpload,
  FilePenLine,
  LoaderCircle,
  Play,
  Tag,
} from 'lucide-react';
import { useEffect, useState } from 'react';

import {
  getEditorSession,
  useEditorSessionVersion,
} from '../../editors/session/editorSessionStore';
import { listDevelopmentTaskRevisions } from '../../service';
import type { DevelopmentNode } from '../../types';
import { deriveAuthoringState } from './authoringState';
import { responseData } from './workbenchResponse';

interface AuthoringStatusBarProps {
  node: DevelopmentNode;
  saving?: boolean;
  publishing?: boolean;
}

type PublishedRevisionState =
  | { status: 'loading' }
  | { status: 'ready'; revision: number | null }
  | { status: 'unavailable' };

const AuthoringStatusBar = ({
  node,
  saving = false,
  publishing = false,
}: AuthoringStatusBarProps) => {
  const intl = useIntl();
  useEditorSessionVersion();
  const [publishedRevision, setPublishedRevision] =
    useState<PublishedRevisionState>({ status: 'loading' });
  const session = getEditorSession(node.id);
  const state = deriveAuthoringState({
    draftRevision: session?.draftRevision,
    dirty: session?.dirty,
    saving,
    publishing,
    pendingPublish: node.pendingPublish,
  });

  useEffect(() => {
    let active = true;
    setPublishedRevision({ status: 'loading' });
    listDevelopmentTaskRevisions(node.id)
      .then((response) => {
        if (!active) return;
        const revisions = responseData(
          response,
          intl.formatMessage({ id: 'pages.dataDevelopment.versions.queryFailed' }),
        );
        setPublishedRevision({
          status: 'ready',
          revision: revisions[0]?.revisionNo ?? null,
        });
      })
      .catch(() => {
        if (active) setPublishedRevision({ status: 'unavailable' });
      });
    return () => {
      active = false;
    };
  }, [intl, node.id, node.pendingPublish, publishing]);

  const saveLabel =
    state.saveState === 'saving'
      ? intl.formatMessage({ id: 'pages.dataDevelopment.authoring.saving' })
      : state.saveState === 'unsaved'
        ? intl.formatMessage({ id: 'pages.dataDevelopment.authoring.unsavedChanges' })
        : intl.formatMessage({ id: 'pages.dataDevelopment.authoring.saved' });

  const publishedLabel =
    publishedRevision.status === 'loading'
      ? intl.formatMessage({ id: 'pages.dataDevelopment.authoring.publishedLoading' })
      : publishedRevision.status === 'unavailable'
        ? intl.formatMessage({ id: 'pages.dataDevelopment.authoring.publishedUnknown' })
        : publishedRevision.revision === null
          ? intl.formatMessage({ id: 'pages.dataDevelopment.authoring.notPublished' })
          : intl.formatMessage(
              { id: 'pages.dataDevelopment.authoring.publishedRevision' },
              { revision: publishedRevision.revision },
            );

  return (
    <div className="flex h-8 shrink-0 items-center justify-between gap-4 border-b border-[#eef0f2] bg-[#fbfcfd] px-3 text-[11px] text-[#667085]">
      <div className="flex min-w-0 items-center gap-2 overflow-hidden">
        <span className="inline-flex shrink-0 items-center gap-1 rounded-[3px] border border-[#e4e7ec] bg-white px-1.5 py-0.5 font-medium text-[#475467]">
          <FilePenLine size={11} strokeWidth={1.8} />
          {state.draftRevision
            ? intl.formatMessage(
                { id: 'pages.dataDevelopment.authoring.draftRevision' },
                { revision: state.draftRevision },
              )
            : intl.formatMessage({ id: 'pages.dataDevelopment.authoring.draftNotSaved' })}
        </span>

        <span className="inline-flex shrink-0 items-center gap-1 rounded-[3px] border border-[#e4e7ec] bg-white px-1.5 py-0.5 text-[#667085]">
          {publishedRevision.status === 'loading' ? (
            <LoaderCircle size={11} className="animate-spin" />
          ) : (
            <Tag size={11} strokeWidth={1.8} />
          )}
          {publishedLabel}
        </span>

        <span
          className={[
            'inline-flex shrink-0 items-center gap-1 rounded-[3px] px-1.5 py-0.5',
            state.saveState === 'unsaved'
              ? 'bg-[#fff6ed] text-[#b54708]'
              : 'bg-[#f2f4f7] text-[#667085]',
          ].join(' ')}
        >
          {state.saveState === 'saving' ? (
            <LoaderCircle size={11} className="animate-spin" />
          ) : state.saveState === 'unsaved' ? (
            <CircleDot size={11} strokeWidth={1.8} />
          ) : (
            <Check size={11} strokeWidth={1.8} />
          )}
          {saveLabel}
        </span>

        {state.publishState === 'pending' || state.publishState === 'publishing' ? (
          <span className="inline-flex shrink-0 items-center gap-1 rounded-[3px] bg-[#eff8ff] px-1.5 py-0.5 text-[#175cd3]">
            {state.publishState === 'publishing' ? (
              <LoaderCircle size={11} className="animate-spin" />
            ) : (
              <CloudUpload size={11} strokeWidth={1.8} />
            )}
            {intl.formatMessage({
              id:
                state.publishState === 'publishing'
                  ? 'pages.dataDevelopment.authoring.publishing'
                  : 'pages.dataDevelopment.authoring.pendingPublish',
            })}
          </span>
        ) : null}
      </div>

      <span className="inline-flex shrink-0 items-center gap-1 text-[#98a2b3]">
        <Play size={10} strokeWidth={1.8} />
        {intl.formatMessage({ id: 'pages.dataDevelopment.authoring.runCurrentEditor' })}
      </span>
    </div>
  );
};

export default AuthoringStatusBar;
