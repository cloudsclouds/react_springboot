// @ts-nocheck
const OVERVIEW_CARDS = [
  { title: '文档区', value: '12', note: '最近编辑 4 篇，继续从工作台进入编辑流。' },
  { title: 'AI 会话', value: '08', note: '保留单独聊天页，避免和编辑器工具抢层级。' },
  { title: '知识库', value: '06', note: '以列表和状态标签为主，后续再接上传与详情。' },
];

export default function WorkspaceHomePage() {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Dashboard</span>
          <h2>工作台布局</h2>
        </div>
      </header>

      <div className="dashboard-grid">
        <section className="panel surface-grid surface-grid--triple">
          {OVERVIEW_CARDS.map((card) => (
            <article key={card.title} className="metric-card">
              <span className="metric-card__label">{card.title}</span>
              <strong>{card.value}</strong>
              <p>{card.note}</p>
            </article>
          ))}
        </section>
      </div>
    </section>
  );
}