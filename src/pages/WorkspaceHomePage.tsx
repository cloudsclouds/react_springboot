// @ts-nocheck
const OVERVIEW_CARDS = [
  { title: '文档区', value: '12', note: '最近编辑 4 篇，继续从工作台进入编辑流。' },
  { title: 'AI 会话', value: '08', note: '保留单独聊天页，避免和编辑器工具抢层级。' },
  { title: '知识库', value: '06', note: '以列表和状态标签为主，后续再接上传与详情。' },
];

const RECENT_ACTIVITY = [
  '产品需求讨论稿在昨天 19:20 更新。',
  '市场调研知识库新增了 3 条待整理资料。',
  '组织页预留了邀请、审批和角色管理入口。',
];

export default function WorkspaceHomePage() {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Dashboard</span>
          <h2>工作台布局</h2>
          <p>概览页负责承接进入后的第一眼信息，让用户知道今天该从哪里开始。</p>
        </div>

        <div className="hero-note-card">
          <strong>结构原则</strong>
          <span>左侧始终是导航，中间处理主要任务，右侧保留辅助信息和待办。</span>
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

        <section className="panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Today</span>
              <h3>近期动态</h3>
            </div>
          </div>

          <div className="timeline-list">
            {RECENT_ACTIVITY.map((item) => (
              <article key={item} className="timeline-item">
                <span className="timeline-item__dot" aria-hidden="true" />
                <p>{item}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="panel note-panel">
          <span className="panel-kicker">Next</span>
          <h3>下一步接入建议</h3>
          <p>优先把文档页和 AI 页的真实交互接起来，因为这两块最能验证信息架构是否成立。</p>
        </section>
      </div>
    </section>
  );
}