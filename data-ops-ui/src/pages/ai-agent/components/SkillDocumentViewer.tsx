import { Segmented, Typography } from 'antd';
import React from 'react';
import SkillMarkdownContent from './SkillMarkdownContent';

/** Static reading and exact source share a single authoritative string. */
const SkillDocumentViewer: React.FC<{ source: string }> = ({ source }) => {
  const [mode, setMode] = React.useState('read');
  return (
    <div className="skill-md-document">
      <div className="skill-md-toolbar">
        <Segmented
          aria-label="正文查看方式"
          value={mode}
          onChange={(value) => setMode(String(value))}
          options={[
            { label: '阅读', value: 'read' },
            { label: '源码', value: 'source' },
          ]}
        />
        <Typography.Text type="secondary">Markdown · {source.length} 字符</Typography.Text>
      </div>
      <div className="skill-md-document-body">
        {mode === 'read' ? (
          <SkillMarkdownContent source={source} />
        ) : (
          <pre className="skill-md-source" aria-label="Markdown 原文">
            {source}
          </pre>
        )}
      </div>
    </div>
  );
};

export default SkillDocumentViewer;
