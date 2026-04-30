// @ts-nocheck
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createDocument, deleteDocument, fetchDocumentMetadata, fetchDocuments, updateDocumentTitle } from '@/api/document';
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';
import NoPermissionPage from './NoPermissionPage';
import NotFoundPage from './NotFoundPage';

const ROLE_ORDER = {
  no_access: 0,
  viewer: 1,
  editor: 2,
  owner: 3,
};

const DEFAULT_NEW_TITLE = '未命名文档';

function parseSnapshot(snapshot) {
  if (!snapshot) return null;
  if (typeof snapshot === 'string') {
    try {
      return JSON.parse(snapshot);
    } catch {
      return { text: snapshot };
    }
  }
  return snapshot;
}

function normalizeRole(role) {
  return typeof role === 'string' ? role.toLowerCase() : '';
}

function canRole(role, requiredRole) {
  const current = ROLE_ORDER[normalizeRole(role)] || 0;
  const required = ROLE_ORDER[normalizeRole(requiredRole)] || 0;
  return current >= required;
}

function snapshotToTiptapContent(snapshot) {
  const data = parseSnapshot(snapshot);
  if (!data) {
    return { type: 'doc', content: [{ type: 'paragraph' }] };
  }
  if (data.type === 'doc' && Array.isArray(data.content)) return data;

  const blocks = Array.isArray(data.blocks) ? data.blocks : [];
  const content = [];
  blocks.forEach((block) => {
    if (block.type === 'heading') {
      content.push({ type: 'heading', attrs: { level: block.level || 1 }, content: block.text ? [{ type: 'text', text: block.text }] : [] });
      return;
    }
    if (block.type === 'paragraph') {
      content.push({ type: 'paragraph', content: block.text ? [{ type: 'text', text: block.text }] : [] });
      return;
    }
    if (block.type === 'list' && Array.isArray(block.items)) {
      const listType = block.ordered ? 'orderedList' : 'bulletList';
      content.push({
        type: listType,
        content: block.items.map((item) => ({ type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: item }] }] })),
      });
    }
  });
  return { type: 'doc', content: content.length > 0 ? content : [{ type: 'paragraph' }] };
}

export default function DocumentsPage() {
  const navigate = useNavigate();
  const { id: routeDocumentId } = useParams();
  const editorContentRef = useRef(null);
  const [documents, setDocuments] = useState([]);
  const [documentAccess, setDocumentAccess] = useState({});
  const [activeDocumentId, setActiveDocumentId] = useState(null);
  const [titleDraft, setTitleDraft] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [message, setMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [editorSnapshot, setEditorSnapshot] = useState(null);
  const [accessDeniedDocumentId, setAccessDeniedDocumentId] = useState(null);
  const [notFoundDocumentId, setNotFoundDocumentId] = useState(null);

  const requestedDocumentId = useMemo(() => {
    const nextRequestedId = routeDocumentId ? Number(routeDocumentId) : null;
    return Number.isFinite(nextRequestedId) ? nextRequestedId : null;
  }, [routeDocumentId]);

  const activeDocument = useMemo(() => documents.find((document) => document.id === activeDocumentId) ?? documents[0] ?? null, [activeDocumentId, documents]);
  const activeRole = normalizeRole(documentAccess[activeDocument?.id] || activeDocument?.myRole || 'no_access');
  const isReadOnly = activeRole === 'viewer';
  const isEditable = activeRole === 'editor' || activeRole === 'owner';

  useEffect(() => {
    async function loadDocuments() {
      setIsLoading(true);
      setErrorMessage('');
      setAccessDeniedDocumentId(null);
      setNotFoundDocumentId(null);

      const response = await fetchDocuments();
      if (!response.ok) {
        setErrorMessage(response.data?.message || '获取文档列表失败');
        setIsLoading(false);
        return;
      }

      const nextDocuments = response.data?.data || [];
      setDocuments(nextDocuments);
      setDocumentAccess(Object.fromEntries(nextDocuments.map((document) => [document.id, normalizeRole(document.myRole || 'viewer')])));
      setActiveDocumentId((currentActiveDocumentId) => {
        if (currentActiveDocumentId) {
          return currentActiveDocumentId;
        }
        if (Number.isFinite(requestedDocumentId)) {
          return requestedDocumentId;
        }
        return null;
      });
      setIsLoading(false);
    }
    void loadDocuments();
  }, []);

  useEffect(() => {
    if (!documents.length) {
      return;
    }

    const routeDocumentExists = requestedDocumentId ? documents.some((document) => document.id === requestedDocumentId) : false;

    if (requestedDocumentId && routeDocumentExists) {
      if (activeDocumentId !== requestedDocumentId) {
        setActiveDocumentId(requestedDocumentId);
      }
      if (routeDocumentId !== String(requestedDocumentId)) {
        navigate(`/documents/${requestedDocumentId}`, { replace: true });
      }
      return;
    }

    if (routeDocumentId && requestedDocumentId && !routeDocumentExists) {
      const nextDocumentId = documents[0].id;
      setActiveDocumentId(nextDocumentId);
      navigate('/documents', { replace: true });
      return;
    }

    if (routeDocumentId && activeDocumentId && !routeDocumentExists) {
      setActiveDocumentId(documents[0].id);
      navigate('/documents', { replace: true });
      return;
    }

    if (!routeDocumentId && !activeDocumentId) {
      const nextDocumentId = documents[0].id;
      setActiveDocumentId(nextDocumentId);
    }
  }, [activeDocumentId, documents, navigate, requestedDocumentId, routeDocumentId]);

  useEffect(() => {
    if (!activeDocumentId) return;
    void reloadDocument(activeDocumentId).catch((error) => setErrorMessage(error instanceof Error ? error.message : '获取文档元数据失败'));
  }, [activeDocumentId]);

  useEffect(() => {
    if (!activeDocumentId || routeDocumentId || documents.length === 0) {
      return;
    }
    navigate(`/documents/${activeDocumentId}`, { replace: true });
  }, [activeDocumentId, documents.length, navigate, routeDocumentId]);

  useEffect(() => {
    if (!activeDocument) {
      setTitleDraft('');
      setEditorSnapshot(null);
      return;
    }
    setTitleDraft(activeDocument.title || '');
    setEditorSnapshot(activeDocument.latestSnapshot ? snapshotToTiptapContent(activeDocument.latestSnapshot) : null);
  }, [activeDocument]);

  const reloadDocument = async (documentId) => {
    const response = await fetchDocumentMetadata(documentId);
    if (!response.ok) {
      const errorMessageText = response.data?.message || '获取文档元数据失败';
      if (response.status === 403 || /没有权限|权限/i.test(errorMessageText)) {
        setAccessDeniedDocumentId(documentId);
        return null;
      }
      if (response.status === 404 || /不存在|已被删除/i.test(errorMessageText)) {
        setNotFoundDocumentId(documentId);
        return null;
      }
      throw new Error(errorMessageText);
    }

    const metadata = response.data?.data ?? response.data;
    setDocuments((currentDocuments) => currentDocuments.map((document) => (document.id === documentId ? { ...document, ...metadata } : document)));
    setDocumentAccess((current) => ({ ...current, [documentId]: normalizeRole(metadata.myRole || current[documentId] || 'viewer') }));
    if (documentId === activeDocumentId) {
      setEditorSnapshot(metadata.latestSnapshot ? snapshotToTiptapContent(metadata.latestSnapshot) : null);
    }
    return metadata;
  };

  const handleCreateDocument = async () => {
    setIsCreating(true);
    setErrorMessage('');
    setMessage('');
    try {
      const response = await createDocument({ title: DEFAULT_NEW_TITLE });
      if (!response.ok) throw new Error(response.data?.message || '创建文档失败');
      const documentId = response.data?.data?.documentId ?? response.data?.documentId;
      if (!documentId) throw new Error('创建文档失败：未返回文档 ID');

      const nextDocument = { id: documentId, title: DEFAULT_NEW_TITLE, ownerId: 0, ownerName: '我', myRole: 'owner', updatedAt: new Date().toISOString() };
      setDocuments((currentDocuments) => [nextDocument, ...currentDocuments]);
      setDocumentAccess((current) => ({ ...current, [documentId]: 'owner' }));
      setActiveDocumentId(documentId);
      setAccessDeniedDocumentId(null);
      setNotFoundDocumentId(null);
      navigate(`/documents/${documentId}`);
      setTitleDraft(DEFAULT_NEW_TITLE);
      setMessage('文档创建成功');
      try { await reloadDocument(documentId); } catch {}
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '创建文档失败');
    } finally {
      setIsCreating(false);
    }
  };

  const handleSaveTitle = async () => {
    if (!activeDocument) return;
    const role = documentAccess[activeDocument.id] || normalizeRole(activeDocument.myRole);
    if (role === 'no_access') {
      setAccessDeniedDocumentId(activeDocument.id);
      return;
    }
    if (!canRole(role, 'editor')) {
      setErrorMessage('当前角色没有编辑权限');
      return;
    }

    const nextTitle = titleDraft.trim();
    if (!nextTitle) {
      setErrorMessage('文档标题不能为空');
      return;
    }
    if (nextTitle === activeDocument.title) {
      setMessage('标题未发生变化');
      return;
    }

    setIsSaving(true);
    setErrorMessage('');
    setMessage('');
    try {
      const response = await updateDocumentTitle(activeDocument.id, { title: nextTitle });
      if (!response.ok) throw new Error(response.data?.message || '修改文档标题失败');
      setDocuments((currentDocuments) => currentDocuments.map((document) => (document.id === activeDocument.id ? { ...document, title: nextTitle, updatedAt: new Date().toISOString() } : document)));
      setMessage('文档标题已更新');
      await reloadDocument(activeDocument.id).catch(() => null);
    } catch (error) {
      if (error instanceof Error && /没有权限|权限/i.test(error.message)) setAccessDeniedDocumentId(activeDocument.id);
      setErrorMessage(error instanceof Error ? error.message : '修改文档标题失败');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteDocument = async () => {
    if (!activeDocument) return;
    const role = documentAccess[activeDocument.id] || normalizeRole(activeDocument.myRole);
    if (role === 'no_access') {
      setAccessDeniedDocumentId(activeDocument.id);
      return;
    }
    if (!canRole(role, 'owner')) {
      setAccessDeniedDocumentId(activeDocument.id);
      return;
    }

    const confirmed = window.confirm(`确定删除文档「${activeDocument.title}」吗？`);
    if (!confirmed) return;

    setIsDeleting(true);
    setErrorMessage('');
    setMessage('');
    try {
      const response = await deleteDocument(activeDocument.id);
      if (!response.ok) {
        const errorMessageText = response.data?.message || '删除文档失败';
        if (response.status === 403 || /没有权限|权限/i.test(errorMessageText)) setAccessDeniedDocumentId(activeDocument.id);
        throw new Error(errorMessageText);
      }
      setDocuments((currentDocuments) => {
        const remaining = currentDocuments.filter((document) => document.id !== activeDocument.id);
        setActiveDocumentId(remaining[0]?.id ?? null);
        return remaining;
      });
      setDocumentAccess((current) => { const nextAccess = { ...current }; delete nextAccess[activeDocument.id]; return nextAccess; });
      setMessage('文档已删除');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '删除文档失败');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleRefresh = async () => {
    if (!activeDocument) return;
    setErrorMessage('');
    try {
      await reloadDocument(activeDocument.id);
      setMessage('文档信息已刷新');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '刷新失败');
    }
  };

  const handleEditorChange = (nextContent) => {
    if (!activeDocument) return;
    const role = documentAccess[activeDocument.id] || normalizeRole(activeDocument.myRole);
    if (role === 'no_access') {
      setAccessDeniedDocumentId(activeDocument.id);
      return;
    }
    if (!canRole(role, 'editor')) {
      setErrorMessage('当前角色没有编辑权限');
      return;
    }

    setEditorSnapshot(nextContent);
    setDocuments((currentDocuments) =>
      currentDocuments.map((document) =>
        document.id === activeDocument.id
          ? { ...document, latestSnapshot: JSON.stringify(nextContent), updatedAt: new Date().toISOString() }
          : document
      )
    );
  };

  if (isLoading) {
    return <section className="page-shell"><header className="page-hero"><div><span className="panel-kicker">Documents</span><h2>文档管理</h2></div></header><div className="panel"><p>正在加载文档列表...</p></div></section>;
  }

  if (accessDeniedDocumentId) {
    return (
      <NoPermissionPage
        documentId={accessDeniedDocumentId}
        onBack={() => {
          setAccessDeniedDocumentId(null);
          setNotFoundDocumentId(null);
          navigate('/documents', { replace: true });
        }}
      />
    );
  }

  if (notFoundDocumentId) {
    return (
      <NotFoundPage
        documentId={notFoundDocumentId}
        onBack={() => {
          setNotFoundDocumentId(null);
          setAccessDeniedDocumentId(null);
          navigate('/documents', { replace: true });
        }}
      />
    );
  }

  return (
    <section className="page-shell">
      <header className="page-hero"><div><span className="panel-kicker">Documents</span><h2>文档管理</h2></div></header>
      <div className="documents-layout">
        <aside className="panel side-panel documents-layout__list">
          <div className="panel-header">
            <div><span className="panel-kicker">List</span><h3>文档列表</h3></div>
            <button type="button" className="secondary-button" onClick={handleCreateDocument} disabled={isCreating}>{isCreating ? '创建中...' : '新建文档'}</button>
          </div>
          <div className="stack-list">
            {documents.length === 0 ? (
              <div className="empty-state"><strong>暂无文档</strong><span>点击“新建文档”开始创建第一篇文档。</span></div>
            ) : (
              documents.map((document) => (
                <button
                  key={document.id}
                  type="button"
                  className={`stack-list__item ${document.id === activeDocument?.id ? 'is-active' : ''}`}
                  onClick={() => {
                    setActiveDocumentId(document.id);
                    navigate(`/documents/${document.id}`);
                  }}
                >
                  <strong>{document.title}</strong>
                  <span>#{document.id} · {document.updatedAt ? new Date(document.updatedAt).toLocaleString() : '未知时间'}</span>
                  <div className="document-badges">
                    <span className={`role-badge ${normalizeRole(document.myRole) === 'editor' || normalizeRole(document.myRole) === 'owner' ? 'role-badge--write' : 'role-badge--read'}`}>
                      {normalizeRole(document.myRole) === 'editor' || normalizeRole(document.myRole) === 'owner' ? '可编辑' : '只读'}
                    </span>
                    <span className="role-badge role-badge--muted">
                      角色：{normalizeRole(document.myRole) || '无权限'}
                    </span>
                    {normalizeRole(document.myRole) === 'viewer' ? (
                      <span className="document-badges__hint">当前文档仅可查看，不可编辑</span>
                    ) : null}
                  </div>
                </button>
              ))
            )}
          </div>
        </aside>

        <section className="panel editor-stage documents-layout__content">
          <div className="panel-header">
            <div><span className="panel-kicker">Metadata</span><h3>{activeDocument ? '文档详情' : '请选择文档'}</h3></div>
            <div className="editor-action-group">
              <article><strong>作者：</strong><span>{activeDocument?.ownerName || '-'}</span></article>
              <article><strong>我的角色：</strong><span>{activeRole === 'no_access' ? '无权限' : activeRole}</span></article>
              <article><strong>更新时间：</strong><span>{activeDocument?.updatedAt ? new Date(activeDocument.updatedAt).toLocaleString() : '-'}</span></article>
              <button type="button" className="secondary-button" onClick={handleRefresh} disabled={!activeDocument}>刷新</button>
              <button type="button" className="secondary-button" onClick={handleDeleteDocument} disabled={!activeDocument || isDeleting || !isEditable || activeRole !== 'owner'}>{isDeleting ? '删除中...' : '删除文档'}</button>
            </div>
          </div>

          {activeDocument ? (
            <div>
              <div className="document-detail__header document-detail__header--inline">
                <label className="auth-field document-detail__title-field">
                  <input value={titleDraft} onChange={(event) => setTitleDraft(event.target.value)} placeholder="请输入标题" disabled={!isEditable} />
                </label>
                <button type="button" className="primary-button" onClick={handleSaveTitle} disabled={isSaving || !isEditable}>{isSaving ? '保存中...' : '保存标题'}</button>
              </div>

              <div className="document-detail__body">
                <div className="document-content-placeholder">
                  <span className="panel-kicker">Content</span>
                  <h4>文档内容</h4>
                  <SimpleEditor
                    key={`${activeDocument.id}-${isEditable ? 'edit' : 'read'}`}
                    initialContent={editorSnapshot || snapshotToTiptapContent(activeDocument.latestSnapshot)}
                    onContentChange={isEditable ? handleEditorChange : undefined}
                    readOnly={!isEditable}
                  />
                  {!isEditable ? (
                    <div className="empty-state" style={{ marginTop: '12px' }}>
                      <strong>只读模式</strong>
                      <span>当前角色只能查看内容，不能编辑。</span>
                    </div>
                  ) : null}
                </div>
              </div>
            </div>
          ) : (
            <div className="editor-stage__paper empty-state empty-state--center"><strong>没有可查看的文档</strong></div>
          )}
        </section>
      </div>
    </section>
  );
}
