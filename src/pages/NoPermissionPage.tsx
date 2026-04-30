export default function NoPermissionPage({
  title = '没有权限',
  description = '你没有访问该文档的权限，请联系文档 owner 或管理员开通访问。',
  documentId,
  onBack,
}) {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Access denied</span>
          <h2>{title}</h2>
        </div>
      </header>

      <div className="panel editor-stage">
        <div className="empty-state empty-state--center">
          <strong>没有权限访问该文档</strong>
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
