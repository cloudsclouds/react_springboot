// @ts-nocheck
const SHARE_STATES = [
  '公开链接状态',
  '密码保护占位',
  '失效时间设置',
  '访问记录与异常态',
];

export default function SharePage() {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Share</span>
          <h2>分享页雏形</h2>
          <p>分享页第一版先处理结构：左侧展示分享对象，右侧展示访问规则与异常状态占位。</p>
        </div>
      </header>

      <div className="workbench-columns">
        <section className="panel panel--wide share-stage">
          <span className="panel-kicker">Shared document</span>
          <h3>文档分享预览</h3>
          <p>这里后面会接分享标题、摘要、正文预览，以及访问受限时的状态页切换。</p>

          <div className="editor-placeholder-lines" aria-hidden="true">
            <span />
            <span />
            <span className="is-long" />
            <span className="is-short" />
          </div>
        </section>

        <aside className="panel side-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Rules</span>
              <h3>访问设置</h3>
            </div>
          </div>

          <div className="prompt-list">
            {SHARE_STATES.map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        </aside>
      </div>
    </section>
  );
}