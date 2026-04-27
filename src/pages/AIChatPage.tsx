// @ts-nocheck
const CONVERSATIONS = ['写作润色建议', '会议纪要总结', '市场分析问答', '分享页文案草稿'];

const MESSAGES = [
  { role: 'assistant', content: 'AI 聊天页会保留独立空间，避免和文档主任务互相打断。' },
  { role: 'user', content: '这里后面会接会话列表、消息流和流式输出。' },
];

export default function AIChatPage() {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">AI Chat</span>
          <h2>AI 聊天页</h2>
          <p>布局重点是会话切换足够快，消息区足够稳，底部输入始终固定在可感知的位置。</p>
        </div>
      </header>

      <div className="workbench-columns">
        <aside className="panel side-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Sessions</span>
              <h3>会话列表</h3>
            </div>
          </div>

          <div className="stack-list">
            {CONVERSATIONS.map((item, index) => (
              <button key={item} type="button" className={`stack-list__item ${index === 0 ? 'is-active' : ''}`}>
                <strong>{item}</strong>
                <span>{index === 0 ? '当前会话' : '静态占位'}</span>
              </button>
            ))}
          </div>
        </aside>

        <section className="panel chat-stage">
          <div className="chat-stage__messages">
            {MESSAGES.map((message) => (
              <article key={`${message.role}-${message.content}`} className={`chat-bubble chat-bubble--${message.role}`}>
                <span className="chat-bubble__role">{message.role === 'assistant' ? 'AI' : '你'}</span>
                <p>{message.content}</p>
              </article>
            ))}
          </div>

          <div className="chat-stage__composer">
            <input type="text" placeholder="后续在这里接流式聊天输入框" />
            <button type="button" className="primary-button">
              发送
            </button>
          </div>
        </section>

        <aside className="panel side-panel side-panel--narrow">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Prompts</span>
              <h3>快捷动作</h3>
            </div>
          </div>

          <div className="prompt-list">
            <span>续写段落</span>
            <span>整理会议纪要</span>
            <span>头脑风暴</span>
            <span>提炼行动项</span>
          </div>
        </aside>
      </div>
    </section>
  );
}