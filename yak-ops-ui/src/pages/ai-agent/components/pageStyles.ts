import { createStyles } from 'antd-style';

/** Agent 页面共享视觉细节：Markdown 排版、图表容器等。 */
export const useAgentStyles = createStyles(() => ({
  markdown: {
    lineHeight: 1.85,
    fontSize: 13.5,
    color: '#262626',
    '& h1, & h2, & h3, & h4': { margin: '16px 0 8px', lineHeight: 1.4 },
    '& p': { margin: '6px 0' },
    '& ul, & ol': { paddingLeft: 22, margin: '6px 0' },
    '& li': { margin: '2px 0' },
    '& table': {
      borderCollapse: 'collapse',
      margin: '10px 0',
      fontSize: 12.5,
      width: '100%',
      display: 'block',
      overflowX: 'auto',
    },
    '& th, & td': {
      border: '1px solid #e8e8e8',
      padding: '6px 10px',
      textAlign: 'left',
      whiteSpace: 'nowrap',
    },
    '& th': { background: '#fafafa', fontWeight: 600 },
    '& tr:nth-child(2n) td': { background: '#fafafa' },
    '& code': {
      background: '#f0f0f0',
      padding: '1px 5px',
      borderRadius: 4,
      fontSize: '92%',
    },
    '& pre': {
      background: '#f6f8fa',
      padding: '10px 12px',
      borderRadius: 6,
      overflowX: 'auto',
      fontSize: 12.5,
    },
    '& pre code': { background: 'transparent', padding: 0 },
    '& blockquote': {
      borderInlineStart: '3px solid #d9d9d9',
      margin: '8px 0',
      paddingInlineStart: 10,
      color: '#595959',
    },
    '& a': { color: '#1677ff' },
    '& hr': { border: 'none', borderTop: '1px solid #f0f0f0', margin: '14px 0' },
    '& .chart-box': {
      width: '100%',
      height: 320,
      border: '1px solid #f0f0f0',
      borderRadius: 8,
      margin: '10px 0',
    },
  },

  filterRow: { display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' as const },

  tracePre: {
    margin: 0,
    background: '#f6f8fa',
    padding: '8px 10px',
    borderRadius: 6,
    overflowX: 'auto',
    overflowY: 'auto',
    fontSize: 12,
    lineHeight: 1.6,
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-all' as const,
  },

  paneScroll: {
    height: 'calc(100vh - 245px)',
    overflowY: 'auto',
    paddingInlineEnd: 4,
  },

  /** 输入为空时发送键置灰且不可点（X 默认不处理空态）。 */
  sendDisabled: {
    '& .ant-sender-actions-btn': {
      filter: 'grayscale(1)',
      opacity: 0.45,
      pointerEvents: 'none',
    },
  },
}));
