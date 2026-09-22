import DOMPurify from 'dompurify';
import * as echarts from 'echarts';
import { marked } from 'marked';
import React from 'react';
import { useAgentStyles } from './pageStyles';

marked.setOptions({ gfm: true, breaks: true });

interface ChartMount {
  instance: echarts.ECharts;
  handler: () => void;
}

/** Markdown 渲染：GFM 表格 + ```echarts 围栏转图表。内容经 DOMPurify 净化。 */
export const MarkdownContent: React.FC<{ md: string }> = ({ md }) => {
  const ref = React.useRef<HTMLDivElement>(null);
  const chartsRef = React.useRef<ChartMount[]>([]);
  const { styles } = useAgentStyles();
  const html = React.useMemo(() => DOMPurify.sanitize(marked.parse(md ?? '', { async: false }) as string), [md]);

  React.useEffect(() => {
    const container = ref.current;
    if (!container) {
      return;
    }
    // 将 ```echarts 代码块替换为图表容器并挂载实例
    container.querySelectorAll('pre > code.language-echarts').forEach((codeEl) => {
      const pre = codeEl.parentElement;
      if (!pre) {
        return;
      }
      let option: unknown;
      try {
        option = JSON.parse(codeEl.textContent || '');
      } catch {
        return; // 非法 JSON 保留原代码展示
      }
      const box = document.createElement('div');
      box.style.width = '100%';
      box.style.height = '320px';
      pre.replaceWith(box);
      try {
        const instance = echarts.init(box);
        instance.setOption(option as echarts.EChartsOption);
        const handler = () => instance.resize();
        window.addEventListener('resize', handler);
        chartsRef.current.push({ instance, handler });
      } catch {
        // 初始化失败保留空容器
      }
    });

    return () => {
      chartsRef.current.forEach(({ instance, handler }) => {
        window.removeEventListener('resize', handler);
        instance.dispose();
      });
      chartsRef.current = [];
    };
  }, [html]);

  return <div ref={ref} className={styles.markdown} dangerouslySetInnerHTML={{ __html: html }} />;
};

export default MarkdownContent;
