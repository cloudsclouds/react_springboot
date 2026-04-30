export default function NotFoundPage({
  title = '文档不存在',
  description = '该文档不存在，或已被删除。',
  documentId,
  onBack,
}) {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Not found</span>
          <h2>{title}</h2>
        </div>
      </header>

      <div className="panel editor-stage">
        <div className="empty-state empty-state--center">
          <strong>未找到文档</strong>
          <span>{description}</span>
          {documentId ? <span>文档 ID：{documentId}</span> : null}
          <div className="editor-action-group">
            {typeof onBack === 'function' ? (
              <button type="button" className="primary-button" onClick={onBack}>
                返回文档列表
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}
