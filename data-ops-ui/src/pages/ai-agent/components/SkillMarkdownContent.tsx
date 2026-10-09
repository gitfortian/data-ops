import React from 'react';
import { Empty } from 'antd';
import { renderSkillMarkdown } from '../skill-markdown';
import './skill-markdown.less';

const SkillMarkdownContent: React.FC<{ source: string; outline?: boolean }> = ({ source, outline = true }) => {
  const document = React.useMemo(() => renderSkillMarkdown(source), [source]);
  const contentRef = React.useRef<HTMLDivElement>(null);
  if (!source.trim()) return <Empty description="暂无正文" />;
  const showOutline = outline && document.headings.length > 0;
  return (
    <div className={`skill-md-reading ${showOutline ? 'skill-md-reading-with-outline' : ''}`}>
      {showOutline ? (
        <nav className="skill-md-outline" aria-label="正文目录">
          <strong>目录</strong>
          {document.headings.map((heading) => (
            <button
              type="button"
              key={heading.index}
              style={{ paddingLeft: 8 + (heading.level - 1) * 10 }}
              onClick={() => {
                const scroller = contentRef.current;
                const target = scroller?.querySelector(`[data-skill-heading="${heading.index}"]`);
                if (scroller && target)
                  scroller.scrollTo({
                    top:
                      scroller.scrollTop +
                      target.getBoundingClientRect().top -
                      scroller.getBoundingClientRect().top -
                      12,
                  });
              }}
            >
              {heading.text}
            </button>
          ))}
        </nav>
      ) : null}
      <div className="skill-md-prose-scroll" aria-label="正文阅读区" ref={contentRef}>
        {document.frontMatter !== undefined ? (
          <details className="skill-md-front-matter">
            <summary>文档头信息（YAML）</summary>
            <pre>{document.frontMatter}</pre>
          </details>
        ) : null}
        <article
          className="skill-md-prose"
          aria-label="Markdown 正文"
          dangerouslySetInnerHTML={{ __html: document.html }}
        />
      </div>
    </div>
  );
};

export default SkillMarkdownContent;
