import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ breaks: true, gfm: true })

const escapeHtml = (value = '') =>
  value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** 极简通用代码高亮，保持深色主题所需的 span 结构 */
const highlight = (code = '') => escapeHtml(code)
  .replace(/(\/\/[^\n]*|#[^\n]*)/g, '<span class="token-comment">$1</span>')
  .replace(/("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')/g, '<span class="token-string">$1</span>')
  .replace(/\b(const|let|var|function|return|if|else|for|while|class|import|from|export|async|await|true|false|null)\b/g, '<span class="token-keyword">$1</span>')

// 代码块统一深色主题
const renderer = new marked.Renderer()
renderer.code = ({ text, lang }) =>
  `<pre class="md-code"><code class="hljs language-${escapeHtml(lang || 'text')}">${highlight(text)}</code></pre>`
marked.use({ renderer })

/**
 * 渲染 Markdown 为安全的 HTML。
 * <p>
 * marked 不会转义模型输出/文档内容中的原始 HTML，因此统一经 DOMPurify 消毒后再交给 v-html 渲染，
 * 防止存储型 XSS（如文档埋入 <img onerror> 被模型复述后在浏览器执行）。
 * @param {string} content Markdown 内容
 * @returns {string} 已消毒的 HTML
 */
export const renderMarkdown = (content = '') =>
  DOMPurify.sanitize(marked.parse(content), {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'form', 'input', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['style'],
  })
