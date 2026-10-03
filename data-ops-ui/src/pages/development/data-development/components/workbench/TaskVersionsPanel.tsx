import { API_SUCCESS_CODE } from '@/services/http/response';
import { history, useIntl } from '@umijs/max';
import { Button, Spin, message } from 'antd';
import { FileCode2, GitBranch } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import {
  canOpenLineageEvidence,
  getDevelopmentLineageEvidence,
  lineageEvidenceStatusLabel,
  lineageEvidenceUrl,
  type DevelopmentLineageEvidence,
} from '../../governanceEvidence';
import {
  getDevelopmentTaskRevision,
  listDevelopmentTaskRevisions,
} from '../../service';
import type {
  DevelopmentNode,
  DevelopmentTaskRevision,
  DevelopmentTaskRevisionSummary,
} from '../../types';

interface TaskVersionsPanelProps {
  node: DevelopmentNode;
  refreshKey: number;
}

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const TaskVersionsPanel = ({ node, refreshKey }: TaskVersionsPanelProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const [versions, setVersions] = useState<DevelopmentTaskRevisionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<DevelopmentTaskRevision>();
  const [lineageLoading, setLineageLoading] = useState(false);
  const [lineageEvidence, setLineageEvidence] = useState<DevelopmentLineageEvidence>();
  const [lineageFailure, setLineageFailure] = useState<string>();

  const text = (id: string, values?: Record<string, string | number>) =>
    intlRef.current.formatMessage({ id }, values);
  const formatTime = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? value
      : date.toLocaleString(intl.locale, { hour12: false });
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setDetail(undefined);
    setLineageEvidence(undefined);
    setLineageFailure(undefined);
    listDevelopmentTaskRevisions(node.id)
      .then((response) => {
        if (!active) return;
        setVersions(
          responseData(
            response,
            text('pages.dataDevelopment.versions.queryFailed'),
          ) || [],
        );
      })
      .catch((error) => {
        if (active) {
          message.error(
            error instanceof Error
              ? error.message
              : text('pages.dataDevelopment.versions.queryFailed'),
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [node.id, refreshKey]);

  const loadLineageEvidence = async (revisionNo: number) => {
    setLineageLoading(true);
    setLineageEvidence(undefined);
    setLineageFailure(undefined);
    try {
      setLineageEvidence(
        responseData(
          await getDevelopmentLineageEvidence(node.id, revisionNo),
          '读取 Lineage evidence 失败',
        ),
      );
    } catch (error) {
      setLineageFailure(
        error instanceof Error && error.message
          ? error.message
          : 'Lineage evidence 暂不可用',
      );
    } finally {
      setLineageLoading(false);
    }
  };

  const openDetail = async (revisionNo: number) => {
    setDetailLoading(true);
    setLineageEvidence(undefined);
    setLineageFailure(undefined);
    try {
      setDetail(
        responseData(
          await getDevelopmentTaskRevision(node.id, revisionNo),
          text('pages.dataDevelopment.versions.detailFailed'),
        ),
      );
      void loadLineageEvidence(revisionNo);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : text('pages.dataDevelopment.versions.detailFailed'),
      );
    } finally {
      setDetailLoading(false);
    }
  };

  const openLineage = () => {
    if (!lineageEvidence || !canOpenLineageEvidence(lineageEvidence)) return;
    const url = lineageEvidenceUrl(lineageEvidence, node.id);
    if (url) history.push(url);
  };

  if (loading) {
    return (
      <div className="flex h-24 items-center justify-center">
        <Spin size="small" />
      </div>
    );
  }

  if (!versions.length) {
    return (
      <div className="py-8 text-center text-[12px] leading-5 text-[#667085]">
        {intl.formatMessage({ id: 'pages.dataDevelopment.versions.empty' })}
        <div className="mt-1">
          {intl.formatMessage({ id: 'pages.dataDevelopment.versions.emptyHint' })}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-3 text-[12px]">
      <div className="space-y-1.5">
        {versions.map((version, index) => (
          <button
            key={version.id}
            type="button"
            onClick={() => void openDetail(version.revisionNo)}
            className="flex w-full items-center gap-2 rounded-[3px] border border-[#eaecf0] px-2.5 py-2 text-left transition-colors hover:bg-[#f8f9fa]"
          >
            <FileCode2 size={14} className="shrink-0 text-[#667085]" strokeWidth={1.7} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-[#344054]">v{version.revisionNo}</span>
                {index === 0 ? (
                  <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[12px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.dataDevelopment.versions.latest' })}
                  </span>
                ) : null}
              </div>
              <div className="mt-0.5 truncate text-[12px] text-[#667085]">
                {formatTime(version.createTime)} · {version.checksum.slice(0, 10)}
              </div>
            </div>
          </button>
        ))}
      </div>

      {detailLoading ? (
        <div className="flex h-16 items-center justify-center border-t border-[#eef0f2]">
          <Spin size="small" />
        </div>
      ) : detail ? (
        <div className="border-t border-[#eef0f2] pt-3">
          <div className="flex items-center justify-between gap-3">
            <span className="font-medium text-[#344054]">
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.versions.content' },
                { revision: detail.revisionNo },
              )}
            </span>
            <span className="text-[12px] text-[#667085]">
              Draft #{detail.sourceDraftRevision}
            </span>
          </div>
          <pre className="mt-2 max-h-[300px] overflow-auto whitespace-pre-wrap break-words rounded-[3px] bg-[#f8f9fa] p-2.5 font-mono text-[12px] leading-5 text-[#475467]">
            {detail.definition.content ||
              intl.formatMessage({ id: 'pages.dataDevelopment.common.emptyContent' })}
          </pre>
          <div className="mt-2 break-all font-mono text-[9px] leading-4 text-[#b0b7c3]">
            SHA-256 {detail.checksum}
          </div>

          <div className="mt-3 border-t border-[#eef0f2] pt-3">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-1.5 font-medium text-[#344054]">
                <GitBranch size={13} />
                Governance / Lineage Evidence
              </div>
              {lineageEvidence && canOpenLineageEvidence(lineageEvidence) ? (
                <Button size="small" type="link" className="!h-6 !px-0" onClick={openLineage}>
                  打开 Lineage
                </Button>
              ) : null}
            </div>

            {lineageLoading ? (
              <div className="flex h-14 items-center justify-center">
                <Spin size="small" />
              </div>
            ) : lineageFailure ? (
              <div className="mt-2 rounded-[3px] bg-[#fff6ed] px-2.5 py-2 text-[12px] leading-5 text-[#b54708]">
                <div className="font-medium">Lineage Evidence 不可用</div>
                <div>{lineageFailure}</div>
                <div className="mt-1 text-[#667085]">读取失败不能解释为“没有血缘”。</div>
              </div>
            ) : lineageEvidence ? (
              <div className="mt-2 space-y-1.5 rounded-[3px] bg-[#f8f9fa] px-2.5 py-2 text-[12px] leading-5 text-[#475467]">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-[#344054]">
                    {lineageEvidenceStatusLabel(lineageEvidence.status)}
                  </span>
                  <span className="font-mono text-[12px] text-[#667085]">
                    Node {lineageEvidence.nodeId} · Revision {lineageEvidence.revisionNo}
                  </span>
                </div>
                <div>{lineageEvidence.reason || '-'}</div>
                <div className="text-[#667085]">
                  Publish {formatTime(lineageEvidence.publishTime)} · Attempts {lineageEvidence.attempts}
                </div>
                {lineageEvidence.deliveryUpdateTime ? (
                  <div className="text-[#667085]">
                    Evidence updated {formatTime(lineageEvidence.deliveryUpdateTime)}
                  </div>
                ) : null}
                {lineageEvidence.status === 'FAILED' && lineageEvidence.nextAttemptTime ? (
                  <div className="text-[#b54708]">
                    Next retry {formatTime(lineageEvidence.nextAttemptTime)}
                  </div>
                ) : null}
                {lineageEvidence.lastError ? (
                  <div className="break-words rounded bg-white px-2 py-1 font-mono text-[12px] text-[#b42318]">
                    {lineageEvidence.lastError}
                  </div>
                ) : null}
                {lineageEvidence.lineageAssetKey ? (
                  <div className="break-all font-mono text-[9px] text-[#667085]">
                    {lineageEvidence.lineageAssetKey}
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default TaskVersionsPanel;
