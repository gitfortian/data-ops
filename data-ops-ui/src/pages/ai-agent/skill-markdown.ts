import DOMPurify from 'dompurify';
import { marked } from 'marked';

/** A display projection only: source text, including front matter and line endings, is never rewritten. */
export function renderSkillMarkdown(source: string) {
  const header = /^(?:\uFEFF)?---\r?\n([\s\S]*?)\r?\n(?:---|\.\.\.)(?:\r?\n|$)/.exec(source);
  const body = header ? source.slice(header[0].length) : source;
  const clean = DOMPurify.sanitize(marked.parse(body, { async: false, gfm: true, breaks: false }), {
    USE_PROFILES: { html: true },
    FORBID_TAGS: [
      'script',
      'style',
      'link',
      'meta',
      'base',
      'iframe',
      'object',
      'embed',
      'form',
      'input',
      'button',
      'textarea',
      'select',
      'img',
      'video',
      'audio',
      'source',
      'track',
    ],
    FORBID_ATTR: ['style', 'src', 'srcset', 'poster', 'background', 'action', 'formaction', 'ping', 'target'],
    SANITIZE_NAMED_PROPS: true,
  });
  const fragment = document.createElement('template');
  fragment.innerHTML = clean;
  const headings = Array.from(fragment.content.querySelectorAll('h1, h2, h3, h4, h5, h6')).map((node, index) => {
    node.setAttribute('data-skill-heading', String(index));
    return { index, level: Number(node.tagName.slice(1)), text: node.textContent ?? '' };
  });
  fragment.content.querySelectorAll('a').forEach((link) => {
    link.setAttribute('rel', 'noopener noreferrer');
    // Local heading links stay in this document; other links open only upon an explicit click.
    if (!link.getAttribute('href')?.startsWith('#')) link.setAttribute('target', '_blank');
  });
  return { html: fragment.innerHTML, frontMatter: header?.[1], headings };
}

export type MarkdownFormat = 'heading' | 'bold' | 'italic' | 'list' | 'quote' | 'code' | 'link';

/** Explicit formatting commands are draft edits, never applied during reading or saving. */
export function markdownInsertion(format: MarkdownFormat, selected: string): string {
  switch (format) {
    case 'heading':
      return (selected || '标题')
        .split('\n')
        .map((line) => `## ${line}`)
        .join('\n');
    case 'bold':
      return `**${selected || '加粗文字'}**`;
    case 'italic':
      return `*${selected || '斜体文字'}*`;
    case 'list':
      return (selected || '列表项')
        .split('\n')
        .map((line) => `- ${line}`)
        .join('\n');
    case 'quote':
      return (selected || '引用内容')
        .split('\n')
        .map((line) => `> ${line}`)
        .join('\n');
    case 'code':
      return `\n\`\`\`\n${selected || '代码'}\n\`\`\`\n`;
    case 'link':
      return `[${selected || '链接文字'}](https://example.com)`;
  }
}
