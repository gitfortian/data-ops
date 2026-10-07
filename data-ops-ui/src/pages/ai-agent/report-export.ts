import DOMPurify from 'dompurify';
import { marked } from 'marked';
import type { ReportDetail } from '@/services/agent/types';

export type ReportExportFormat = 'html' | 'markdown';

const htmlEscapes: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;',
  '"': '&quot;', "'": '&#39;' };

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => htmlEscapes[character]);
}

/** An inert snapshot: charts stay as source JSON; no browser scripts or remote resources. */
export function buildReportHtml(report: ReportDetail): string {
  const body = DOMPurify.sanitize(marked.parse(report.content, { async: false, gfm: true, breaks: true }) as string, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['script', 'style', 'link', 'meta', 'base', 'iframe', 'object', 'embed', 'form', 'input',
      'button', 'textarea', 'select', 'img', 'video', 'audio', 'source', 'track'],
    FORBID_ATTR: ['style', 'src', 'srcset', 'poster', 'background', 'action', 'formaction', 'ping', 'target'],
    SANITIZE_NAMED_PROPS: true,
  });
  const title = escapeHtml(report.title);
  return `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>${title}</title>
<style>body{font-family:system-ui,sans-serif;max-width:920px;margin:24px auto;padding:0 16px;line-height:1.8;color:#222}
table{border-collapse:collapse;max-width:100%;display:block;overflow:auto}th,td{border:1px solid #ddd;padding:6px 10px}
pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f5f5f5;padding:12px}code{overflow-wrap:anywhere}</style>
</head><body><h1>${title}</h1>
<p>报告 ID：${report.id} · 来源会话：${escapeHtml(report.sessionId)}</p>
<p>静态导出快照；图表保留 JSON 配置，不执行交互图。来源链接需在原站权限下核对。</p>
<main>${body}</main></body></html>`;
}

export function reportExport(report: ReportDetail, format: ReportExportFormat) {
  const base = Array.from(report.title.replace(/[\u0000-\u001f\u007f/\\:*?"<>|]/g, '_').trim())
    .slice(0, 80).join('').replace(/[. ]+$/g, '') || 'analysis-report';
  return format === 'html'
    ? { filename: `${base}.html`, content: buildReportHtml(report), mimeType: 'text/html;charset=utf-8' }
    : { filename: `${base}.md`, content: report.content, mimeType: 'text/markdown;charset=utf-8' };
}
