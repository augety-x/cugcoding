export function escapeHtml(text = '') {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export function markdownInline(text = '') {
  return escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>')
}

const LANG_DISPLAY = {
  java: 'Java', js: 'JavaScript', ts: 'TypeScript', py: 'Python',
  sql: 'SQL', xml: 'XML', json: 'JSON', yaml: 'YAML', bash: 'Bash',
  sh: 'Shell', properties: 'Properties', nginx: 'Nginx',
  dockerfile: 'Docker', css: 'CSS', html: 'HTML', md: 'Markdown',
  powershell: 'PowerShell', ps1: 'PowerShell', text: 'Plain Text',
  ini: 'INI', conf: 'Config', env: 'Env', makefile: 'Makefile',
  groovy: 'Groovy', kotlin: 'Kotlin', scala: 'Scala', go: 'Go',
  rust: 'Rust', c: 'C', cpp: 'C++', cs: 'C#', php: 'PHP', ruby: 'Ruby',
  lua: 'Lua', swift: 'Swift', r: 'R', dart: 'Dart', diff: 'Diff',
  graphql: 'GraphQL', docker: 'Docker', vim: 'Vim', perl: 'Perl',
  markdown: 'Markdown', less: 'Less', scss: 'SCSS', sass: 'Sass',
  vue: 'Vue', shell: 'Shell', tf: 'Terraform',
}

function langDisplay(lang) {
  const key = (lang || 'text').toLowerCase()
  return LANG_DISPLAY[key] || lang.toUpperCase()
}

function buildCodeBlock(lang, escapedCode) {
  const label = langDisplay(lang)
  return (
    '<div class="code-block" data-lang="' + escapeHtml(lang || 'text') + '">' +
      '<div class="code-header">' +
        '<span class="code-dots"><i></i><i></i><i></i></span>' +
        '<span class="code-lang">' + escapeHtml(label) + '</span>' +
        '<button class="code-copy-btn" title="复制代码" onclick="window.__copyCode(this)">' +
          '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
            '<rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>' +
            '<path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>' +
          '</svg>' +
          '<span>复制</span>' +
        '</button>' +
      '</div>' +
      '<pre class="md-pre"><code>' + escapedCode + '</code></pre>' +
    '</div>'
  )
}

export function renderMarkdown(content = '', title = '') {
  const lines = content.split(/\r?\n/)
  const titleLine = title ? '# ' + title : ''
  const source = lines.length && ((titleLine && lines[0].trim() === titleLine) || /^#\s+/.test(lines[0].trim()))
    ? lines.slice(1).join('\n').trimStart()
    : content

  const out = []
  let inList = false
  let listTag = ''
  let inParagraph = false
  let inCode = false
  let codeLines = []
  let codeLang = ''
  let inQuote = false

  const closeParagraph = () => { if (inParagraph) out.push('</p>'); inParagraph = false }
  const closeList = () => { if (inList) out.push('</' + listTag + '>'); inList = false; listTag = '' }
  const closeQuote = () => { if (inQuote) out.push('</blockquote>'); inQuote = false }
  const closeCode = () => {
    if (inCode) {
      out.push(buildCodeBlock(codeLang || 'text', escapeHtml(codeLines.join('\n'))))
    }
    inCode = false; codeLines = []; codeLang = ''
  }

  for (const rawLine of source.split(/\r?\n/)) {
    const line = rawLine.replace(/\s+$/, '')
    const fence = line.match(/^```(\w+)?\s*$/)
    if (fence) {
      if (inCode) {
        closeCode()
      } else {
        closeParagraph(); closeList(); closeQuote(); inCode = true; codeLang = fence[1] || ''
      }
      continue
    }
    if (inCode) { codeLines.push(rawLine); continue }

    const t = line.trim()
    if (!t) { closeParagraph(); closeList(); closeQuote(); continue }
    if (t.startsWith('> ')) { closeParagraph(); closeList(); if (!inQuote) { out.push('<blockquote>'); inQuote = true } out.push('<p>' + markdownInline(t.slice(2)) + '</p>'); continue }
    if (/^#{1,6}\s+/.test(t)) { closeParagraph(); closeList(); closeQuote(); const level = t.match(/^#+/)[0].length; out.push('<h' + level + '>' + markdownInline(t.slice(level + 1)) + '</h' + level + '>'); continue }
    if (/^\d+\.\s+/.test(t)) { closeParagraph(); closeQuote(); if (!inList || listTag !== 'ol') { closeList(); out.push('<ol>'); inList = true; listTag = 'ol' } out.push('<li>' + markdownInline(t.replace(/^\d+\.\s+/, '')) + '</li>'); continue }
    if (t.startsWith('- ')) { closeParagraph(); closeQuote(); if (!inList || listTag !== 'ul') { closeList(); out.push('<ul>'); inList = true; listTag = 'ul' } out.push('<li>' + markdownInline(t.slice(2)) + '</li>'); continue }
    closeList(); closeQuote(); if (!inParagraph) { out.push('<p>'); inParagraph = true } else { out.push('<br/>') }; out.push(markdownInline(t))
  }

  closeCode(); closeParagraph(); closeList(); closeQuote()
  return out.join('') || '<p></p>'
}
