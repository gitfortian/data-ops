import { markdownInsertion, renderSkillMarkdown } from './skill-markdown';

describe('Skill 静态 Markdown 阅读', () => {
  it('文档头单独投影，GFM 标题/表格/列表/代码完整排版；输入原文不变', () => {
    const source =
      '---\r\nname: demo\r\ndescription: 长文\r\n---\r\n\r\n# 说明\r\n\r\n- **依据**\r\n\r\n|字段|说明|\r\n|---|---|\r\n|id|标识|\r\n\r\n## 步骤\r\n\r\n```echarts\r\n{"series":[]}\r\n```\r\n';
    const rendered = renderSkillMarkdown(source);
    expect(rendered.frontMatter).toBe('name: demo\r\ndescription: 长文');
    expect(rendered.headings).toEqual([
      { index: 0, level: 1, text: '说明' },
      { index: 1, level: 2, text: '步骤' },
    ]);
    expect(rendered.html).toContain('<table>');
    expect(rendered.html).toContain('<strong>依据</strong>');
    expect(rendered.html).toContain('language-echarts');
    expect(rendered.html).toContain('{"series":[]}');
    expect(source).toContain('---\r\nname: demo');
  });

  it('不完整文档头不隐藏正文；正常横线仍作为 Markdown 阅读', () => {
    expect(renderSkillMarkdown('---\nname: unfinished').frontMatter).toBeUndefined();
    expect(renderSkillMarkdown('说明\n\n---\n\n正文').html).toContain('<hr>');
    expect(renderSkillMarkdown('---\nname: unfinished').html).toContain('unfinished');
  });

  it('移除脚本、事件、表单、远端图片/媒体、样式请求及危险链接，保留代码原文', () => {
    const rendered = renderSkillMarkdown(
      '<script>alert(1)</script>\n<img src="https://example.com/private" onerror="evil()">\n<iframe src="https://example.com"></iframe>\n<video poster="https://example.com"></video>\n<a href="javascript:evil()" onclick="evil()" style="background:url(https://example.com)">bad</a>\n\n```html\n<script>code only</script>\n```\n\n[原页面](https://example.com/source)',
    );
    const holder = document.createElement('div');
    holder.innerHTML = rendered.html;
    expect(holder.querySelector('script, img, iframe, video, style, form')).toBeNull();
    expect(holder.querySelector('[onclick], [onerror], [style], [src]')).toBeNull();
    expect(holder.querySelector('a')?.hasAttribute('href')).toBe(false);
    expect(holder.querySelector('code')?.textContent).toContain('<script>code only</script>');
    expect(holder.querySelector('a[href="https://example.com/source"]')?.getAttribute('rel')).toBe(
      'noopener noreferrer',
    );
  });

  it('每份文档标题定位各自编号，不把重复标题折叠为一个', () => {
    const rendered = renderSkillMarkdown('# 检查\n\n## 检查\n');
    expect(rendered.headings.map((value) => value.index)).toEqual([0, 1]);
    expect(rendered.html).toContain('data-skill-heading="1"');
  });
});

it('显式格式操作保留选中文字，代码保持代码围栏', () => {
  expect(markdownInsertion('bold', '原文')).toBe('**原文**');
  expect(markdownInsertion('list', '甲\n乙')).toBe('- 甲\n- 乙');
  expect(markdownInsertion('quote', '待核对')).toBe('> 待核对');
  expect(markdownInsertion('code', '<b>raw</b>')).toBe('\n```\n<b>raw</b>\n```\n');
});
