import { buildReportHtml, reportExport } from './report-export';
import type { ReportDetail } from '@/services/agent/types';

const report: ReportDetail = { id: 7, sessionId: 'session-7', title: '治理分析', content: '# 正文\n\n| 字段 | 值 |\n| --- | --- |\n| 状态 | ERROR |' };
const parse = (html: string) => new DOMParser().parseFromString(html, 'text/html');

it('escapes title and metadata while preserving readable static GFM content', () => {
  const title = '</title><script>alert(1)</script><img src=x onerror=alert(2)>';
  const document = parse(buildReportHtml({ ...report, title, sessionId: '<iframe src=x>' }));
  expect(document.title).toBe(title);
  expect(document.querySelector('h1')?.textContent).toBe(title);
  expect(document.body.textContent).toContain('<iframe src=x>');
  expect(document.querySelector('script, img, iframe')).toBeNull();
  expect(document.querySelector('table')?.textContent).toContain('ERROR');
  expect(document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.getAttribute('content')).toContain("default-src 'none'");
});

it('removes active markup and remote resources instead of bundling a CDN runtime', () => {
  const content = `<script>alert(1)</script><img src="https://private.invalid/pixel" onerror="alert(1)">
<style>body{background:url(https://private.invalid/style)}</style><link rel="stylesheet" href="https://private.invalid/css">
<iframe srcdoc="<script>alert(1)</script>"></iframe><form action="https://private.invalid"><input name="secret"></form>
<svg onload="alert(1)"><a href="javascript:alert(1)">bad</a></svg>
<a href="javascript:alert(1)" onclick="alert(1)">unsafe link</a>
<p style="background:url(https://private.invalid)" onmouseover="alert(1)">可读内容</p>`;
  const document = parse(buildReportHtml({ ...report, content }));
  expect(document.body.textContent).toContain('可读内容');
  expect(document.querySelector('script, img, iframe, form, input, svg, link')).toBeNull();
  expect(document.querySelector('main [style], [src], [onclick], [onmouseover]')).toBeNull();
  expect(document.querySelector('a[href^="javascript:"]')).toBeNull();
  expect(document.querySelectorAll('style')).toHaveLength(1);
});

it('keeps chart JSON as readable code without executable chart dependencies', () => {
  const option = { title: { text: '查询结果' }, series: [{ type: 'bar', data: [1, 2] }] };
  const document = parse(buildReportHtml({ ...report, content: `\`\`\`echarts\n${JSON.stringify(option)}\n\`\`\`` }));
  expect(JSON.parse(document.querySelector('code.language-echarts')!.textContent!)).toEqual(option);
  expect(document.querySelector('script')).toBeNull();
  expect(document.body.textContent).toContain('不执行交互图');
});

it('exports exact source Markdown and safe filenames from the fetched detail', () => {
  const title = '../私有\\报告:<script>\u0000?*|';
  const file = reportExport({ ...report, title }, 'markdown');
  expect(file.content).toBe(report.content);
  expect(file.mimeType).toBe('text/markdown;charset=utf-8');
  expect(file.filename).toMatch(/\.md$/);
  expect(file.filename).not.toMatch(/[\u0000-\u001f/\\:*?"<>|]/);
  expect(reportExport({ ...report, title: '. '.repeat(60) }, 'html').filename).toBe('analysis-report.html');
  expect(Array.from(reportExport({ ...report, title: '😀'.repeat(100) }, 'html').filename.replace(/\.html$/, ''))).toHaveLength(80);
});
