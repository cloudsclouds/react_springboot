// @ts-nocheck
const DOCUMENT_ITEMS = [
  { title: '产品路线图 2026', meta: '今天更新 · 主文档' },
  { title: '周会纪要模板', meta: '固定模板 · 团队通用' },
  { title: '采访提纲草稿', meta: '个人文档 · 待补充' },
  { title: '分享说明页', meta: '外链预览 · 草稿' },
];

const TOOL_GROUPS = ['排版', '块插入', '评论', 'AI 辅助'];

export default function DocumentsPage() {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Documents</span>
          <h2>文档列表与编辑区</h2>
        </div>
      </header>

      <div className="workbench-columns workbench-columns--editor">
        <aside className="panel side-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">List</span>
              <h3>文档列表</h3>
            </div>
            <button type="button" className="secondary-button">
              新建
            </button>
          </div>

          <div className="stack-list">
            {DOCUMENT_ITEMS.map((item, index) => (
              <button
                key={item.title}
                type="button"
                className={`stack-list__item ${index === 0 ? 'is-active' : ''}`}
              >
                <strong>{item.title}</strong>
                <span>{item.meta}</span>
              </button>
            ))}
          </div>
        </aside>

        <section className="panel editor-stage">
          <div className="editor-stage__toolbar">
            {TOOL_GROUPS.map((label) => (
              <span key={label} className="toolbar-chip">
                {label}
              </span>
            ))}
          </div>

          <div className="editor-stage__paper">
            <span className="panel-kicker">Single user editor</span>
            <h3>产品路线图 2026</h3>
            <p>这里预留单人块编辑器画布，后面直接接现有 Tiptap 实现。</p>
            <div className="editor-placeholder-lines" aria-hidden="true">
              <span />
              <span />
              <span />
              <span className="is-short" />
            </div>
          </div>
        </section>

        <aside className="panel side-panel side-panel--narrow">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Inspector</span>
              <h3>辅助区</h3>
            </div>
          </div>

          <div className="detail-list">
            <article>
              <strong>保存状态</strong>
              <span>已预留顶部状态位</span>
            </article>
            <article>
              <strong>分享入口</strong>
              <span>可从这里跳到分享页</span>
            </article>
            <article>
              <strong>AI 联动</strong>
              <span>后续可切成侧边栏或快捷动作</span>
            </article>
          </div>
        </aside>
      </div>
    </section>
  );
}