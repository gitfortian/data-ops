import { Button, Input, Modal, message, Space, Spin, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React from 'react';
import { agentReportApi } from '@/services/agent';
import type { AgentReport } from '../types';
import MarkdownContent from './MarkdownContent';

const utf8ToBase64 = (value: string) => {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
};

/** 导出离线自包含 HTML：Markdown 渲染 + echarts 围栏转图表（CDN 加载依赖）。 */
const buildExportHtml = (title: string, markdown: string) => {
  const body = /```echarts/.test(markdown)
    ? `<p style="color:#888">图表需联网加载 CDN 依赖后显示。</p>` +
      `<div id="content"></div>
<script>
const md = decodeURIComponent(escape(atob("__MD__")));
const el = document.getElementById('content');
el.innerHTML = marked.parse(md);
el.querySelectorAll('pre > code.language-echarts').forEach(code => {
  const box = document.createElement('div'); box.className='chart';
  code.parentElement.replaceWith(box);
  try { echarts.init(box).setOption(JSON.parse(code.textContent)); }
  catch(e){ box.innerText = '图表配置解析失败'; }
});
</script>`
    : `<div id="content"></div>
<script>
const md = decodeURIComponent(escape(atob("__MD__")));
document.getElementById('content').innerHTML = marked.parse(md);
</script>`;
  return `<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>${title}</title>
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js"></script>
<style>body{font-family:-apple-system,sans-serif;max-width:920px;margin:24px auto;padding:0 16px;line-height:1.8;color:#222}
h1,h2,h3{line-height:1.4} table{border-collapse:collapse} th,td{border:1px solid #ddd;padding:6px 10px}
code{background:#f5f5f5;padding:2px 4px;border-radius:4px}
.chart{width:100%;height:360px;margin:12px 0}</style></head><body>
<h1>${title}</h1>${body.replace('__MD__', utf8ToBase64(markdown))}</body></html>`;
};

const ReportsTab: React.FC = () => {
  const [rows, setRows] = React.useState<AgentReport[]>([]);
  const [total, setTotal] = React.useState(0);
  const [pageNo, setPageNo] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(10);
  const [loading, setLoading] = React.useState(false);
  const [detail, setDetail] = React.useState<(AgentReport & { content: string }) | null>(null);
  const [detailOpen, setDetailOpen] = React.useState(false);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [keyword, setKeyword] = React.useState('');

  const load = React.useCallback(
    async (page: number, size: number) => {
      setLoading(true);
      try {
        const data = await agentReportApi.page({
          pageNo: page,
          pageSize: size,
          keyword: keyword || undefined,
        });
        setRows(data.bizData ?? []);
        setTotal(data.pagination?.total ?? 0);
      } finally {
        setLoading(false);
      }
    },
    [keyword],
  );

  React.useEffect(() => {
    load(pageNo, pageSize);
  }, [pageNo, pageSize, load]);

  const openDetail = async (id: number) => {
    setDetailLoading(true);
    setDetailOpen(true);
    try {
      setDetail(await agentReportApi.detail(id));
    } catch {
      message.error('报告加载失败');
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const exportHtml = async (id: number, title: string) => {
    try {
      const detailData = await agentReportApi.detail(id);
      const blob = new Blob([buildExportHtml(title, detailData.content)], {
        type: 'text/html;charset=utf-8',
      });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${title || 'report'}.html`;
      anchor.click();
      URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch {
      message.error('导出失败');
    }
  };

  const columns: ColumnsType<AgentReport> = [
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '来源会话', dataIndex: 'sessionId', width: 180, ellipsis: true },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value?: string) => value?.replace('T', ' ').slice(0, 23) ?? '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_: unknown, record: AgentReport) => (
        <Space>
          <Button size="small" onClick={() => openDetail(record.id)}>
            查看
          </Button>
          <Button size="small" onClick={() => exportHtml(record.id, record.title)}>
            导出
          </Button>
          <Button
            size="small"
            danger
            onClick={async () => {
              if (!window.confirm(`删除报告「${record.title}」？`)) {
                return;
              }
              try {
                await agentReportApi.remove(record.id);
                message.success('已删除');
                load(pageNo, pageSize);
              } catch {
                message.error('删除失败');
              }
            }}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 12, width: '100%' }}>
        <Input.Search
          allowClear
          placeholder="按标题关键词搜索"
          style={{ width: 280 }}
          onSearch={(value) => {
            setKeyword(value.trim());
            setPageNo(1);
          }}
        />
      </Space>
      <Table<AgentReport>
        size="small"
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (page, size) => {
            setPageNo(page);
            setPageSize(size);
          },
        }}
      />
      <Modal open={detailOpen} title={detail?.title} footer={null} width={960} onCancel={() => setDetailOpen(false)}>
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: '48px 0' }}>
            <Spin />
          </div>
        ) : detail ? (
          <div style={{ maxHeight: '70vh', overflowY: 'auto', paddingInlineEnd: 4 }}>
            <MarkdownContent md={detail.content} />
          </div>
        ) : null}
      </Modal>
    </>
  );
};

export default ReportsTab;
