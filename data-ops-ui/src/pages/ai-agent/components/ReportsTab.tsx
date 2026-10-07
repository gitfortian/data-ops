import { Alert, Button, Input, Modal, message, Space, Spin, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React from 'react';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { agentReportApi } from '@/services/agent';
import type { AgentReport, ReportDetail } from '@/services/agent/types';
import { reportExport, type ReportExportFormat } from '../report-export';
import MarkdownContent from './MarkdownContent';

async function readReport(id: number): Promise<ReportDetail> {
  const data = await agentReportApi.detail(id);
  if (!data || data.id !== id || typeof data.title !== 'string' || typeof data.content !== 'string'
    || typeof data.sessionId !== 'string') throw new Error('REPORT_MISMATCH');
  return data;
}

const ReportsTab: React.FC = () => {
  const { can } = usePermissionAccess();
  const canRead = can('agent:report:read');
  const canDelete = can('agent:report:delete');
  const readPermission = React.useRef(canRead);
  readPermission.current = canRead;
  const mounted = React.useRef(true);
  const listRequest = React.useRef(0);
  const detailRequest = React.useRef(0);
  const detailSelection = React.useRef<number | null>(null);
  const exportGeneration = React.useRef(0);
  const exportRequest = React.useRef<number | null>(null);
  const [rows, setRows] = React.useState<AgentReport[]>([]);
  const [total, setTotal] = React.useState(0);
  const [pageNo, setPageNo] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(10);
  const [loading, setLoading] = React.useState(false);
  const [listError, setListError] = React.useState(false);
  const [detail, setDetail] = React.useState<ReportDetail | null>(null);
  const [detailId, setDetailId] = React.useState<number | null>(null);
  const [detailOpen, setDetailOpen] = React.useState(false);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [detailError, setDetailError] = React.useState(false);
  const [keyword, setKeyword] = React.useState('');
  const [exporting, setExporting] = React.useState<number | null>(null);
  const deleting = React.useRef(new Set<number>());
  const closeDetail = () => {
    detailRequest.current += 1;
    detailSelection.current = null;
    setDetailOpen(false); setDetail(null); setDetailLoading(false); setDetailError(false);
  };
  const cancelExport = () => { exportRequest.current = null; setExporting(null); };

  React.useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false; listRequest.current += 1; detailRequest.current += 1;
      exportRequest.current = null;
    };
  }, []);
  React.useEffect(() => {
    if (!canRead) {
      listRequest.current += 1; setRows([]); setTotal(0); setLoading(false);
      closeDetail(); cancelExport();
    }
  }, [canRead]);

  const load = React.useCallback(async (page: number, size: number) => {
    if (!canRead) return;
    const request = ++listRequest.current;
    setLoading(true); setListError(false); setRows([]); setTotal(0);
    try {
      const data = await agentReportApi.page({ pageNo: page, pageSize: size, keyword: keyword || undefined });
      if (!mounted.current || !readPermission.current || request !== listRequest.current) return;
      setRows(data.bizData ?? []); setTotal(data.pagination?.total ?? 0);
    } catch {
      if (mounted.current && readPermission.current && request === listRequest.current) setListError(true);
    } finally {
      if (mounted.current && request === listRequest.current) setLoading(false);
    }
  }, [keyword, canRead]);
  React.useEffect(() => { void load(pageNo, pageSize); }, [pageNo, pageSize, load]);
  const reload = React.useRef(() => {});
  reload.current = () => { void load(pageNo, pageSize); };

  const openDetail = async (id: number) => {
    if (!readPermission.current) return;
    const request = ++detailRequest.current;
    detailSelection.current = id;
    setDetailId(id); setDetail(null); setDetailError(false); setDetailLoading(true); setDetailOpen(true);
    try {
      const data = await readReport(id);
      if (mounted.current && readPermission.current && request === detailRequest.current) setDetail(data);
    } catch {
      if (mounted.current && readPermission.current && request === detailRequest.current) setDetailError(true);
    } finally {
      if (mounted.current && request === detailRequest.current) setDetailLoading(false);
    }
  };

  const exportFile = async (id: number, format: ReportExportFormat) => {
    if (!readPermission.current || exportRequest.current !== null) return;
    const request = ++exportGeneration.current;
    exportRequest.current = request; setExporting(id);
    const current = () => mounted.current && readPermission.current && exportRequest.current === request;
    try {
      const data = await readReport(id);
      if (!current()) return;
      const file = reportExport(data, format);
      const url = URL.createObjectURL(new Blob([file.content], { type: file.mimeType }));
      const anchor = document.createElement('a');
      try {
        anchor.href = url; anchor.download = file.filename; anchor.hidden = true;
        document.body.appendChild(anchor); anchor.click();
      } finally { anchor.remove(); URL.revokeObjectURL(url); }
      if (current()) message.success('导出成功');
    } catch {
      if (current()) message.error('报告导出失败，请核对权限或重试。');
    } finally {
      if (exportRequest.current === request) { exportRequest.current = null; if (mounted.current) setExporting(null); }
    }
  };

  const columns: ColumnsType<AgentReport> = [
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '来源会话', dataIndex: 'sessionId', width: 180, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', width: 170,
      render: (value?: string) => value?.replace('T', ' ').slice(0, 23) ?? '-' },
    { title: '操作', key: 'actions', width: 300,
      render: (_: unknown, record: AgentReport) => <Space wrap>
        <Button size="small" onClick={() => void openDetail(record.id)}>查看</Button>
        <Button size="small" disabled={exporting !== null} onClick={() => void exportFile(record.id, 'html')}>导出 HTML</Button>
        <Button size="small" disabled={exporting !== null} onClick={() => void exportFile(record.id, 'markdown')}>导出 Markdown</Button>
        <Button size="small" danger disabled={!canDelete || exporting !== null} onClick={async () => {
          if (!canDelete || deleting.current.has(record.id)
            || !window.confirm(`删除报告「${record.title}」？`)) return;
          deleting.current.add(record.id);
          try {
            await agentReportApi.remove(record.id);
            if (!mounted.current || !readPermission.current) return;
            message.success('已删除');
            if (detailSelection.current === record.id) closeDetail();
            reload.current();
          } catch {
            if (mounted.current && readPermission.current) message.error('删除失败');
          } finally { deleting.current.delete(record.id); }
        }}>删除</Button>
      </Space> },
  ];

  if (!canRead) return <Alert type="info" showIcon message="当前没有分析报告读取权限。" />;
  return <>
    <Space wrap style={{ marginBottom: 12, width: '100%' }}>
      <Input.Search allowClear placeholder="按标题关键词搜索" style={{ width: 280 }}
        onSearch={(value) => { setKeyword(value.trim()); setPageNo(1); }} />
      {exporting !== null && <Button onClick={cancelExport}>取消导出</Button>}
    </Space>
    <p>HTML 为离线静态快照，图表保留 JSON 配置；Markdown 保留报告原文。</p>
    {listError && <Alert type="error" showIcon message="报告列表加载失败，请重试。"
      action={<Button onClick={() => void load(pageNo, pageSize)}>重试列表</Button>} style={{ marginBottom: 12 }} />}
    <Table<AgentReport> size="small" rowKey="id" loading={loading} columns={columns} dataSource={rows}
      scroll={{ x: 840 }} pagination={{ current: pageNo, pageSize, total, showSizeChanger: true,
        onChange: (page, size) => { setPageNo(page); setPageSize(size); } }} />
    <Modal open={detailOpen} title={detail?.title ?? (detailId === null ? '分析报告' : `报告 #${detailId}`)}
      footer={null} width={960} onCancel={closeDetail}>
      {detailLoading ? <div style={{ textAlign: 'center', padding: '48px 0' }}><Spin /></div>
        : detailError ? <Alert type="error" showIcon message="报告加载失败，请核对权限或重试。"
          action={<Button onClick={() => detailId !== null && void openDetail(detailId)}>重试报告</Button>} />
          : detail ? <div style={{ maxHeight: '70vh', overflowY: 'auto', paddingInlineEnd: 4 }}>
            <MarkdownContent md={detail.content} />
          </div> : null}
    </Modal>
  </>;
};

export default ReportsTab;
