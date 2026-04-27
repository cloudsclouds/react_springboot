// @ts-nocheck
const KNOWLEDGE_ROWS = [
  { name: '产品资料库', type: '文档集', updatedAt: '今天 09:10' },
  { name: '访谈原始记录', type: '音频转写', updatedAt: '昨天 20:30' },
  { name: '行业观察', type: '网页收藏', updatedAt: '04-22 16:48' },
  { name: '品牌素材', type: '附件集合', updatedAt: '04-20 11:15' },
];

export default function KnowledgeBasePage() {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Knowledge Base</span>
          <h2>知识库列表页</h2>
          <p>知识库阶段一先做“可浏览、可筛选、可预览”，把上传和详情复杂度留到下一步。</p>
        </div>
      </header>

      <div className="workbench-columns">
        <section className="panel panel--wide">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Library</span>
              <h3>列表视图</h3>
            </div>
            <div className="toolbar-row">
              <span className="toolbar-chip">全部</span>
              <span className="toolbar-chip">最近更新</span>
              <span className="toolbar-chip">我的资料</span>
            </div>
          </div>

          <div className="table-list">
            {KNOWLEDGE_ROWS.map((row, index) => (
              <article key={row.name} className={`table-row ${index === 0 ? 'is-active' : ''}`}>
                <strong>{row.name}</strong>
                <span>{row.type}</span>
                <span>{row.updatedAt}</span>
              </article>
            ))}
          </div>
        </section>

        <aside className="panel side-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Preview</span>
              <h3>详情预览</h3>
            </div>
          </div>

          <div className="detail-list">
            <article>
              <strong>产品资料库</strong>
              <span>预留简介、文件数、最后更新时间。</span>
            </article>
            <article>
              <strong>操作位</strong>
              <span>上传文件、添加链接、删除入口都可以放在这里。</span>
            </article>
          </div>
        </aside>
      </div>
    </section>
  );
}