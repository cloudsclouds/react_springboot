// @ts-nocheck
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { createDocument, createShareLink, deleteDocument, fetchDocumentMembers, fetchDocumentMetadata, fetchDocuments, joinByShareLink, removeDocumentMember, updateDocumentSnapshot, updateDocumentTitle, upsertDocumentMember } from '@/api/document';
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
  const [searchParams, setSearchParams] = useSearchParams();
  const editorContentRef = useRef(null);
  const [documents, setDocuments] = useState([]);
  const [documentAccess, setDocumentAccess] = useState({});
  const [activeDocumentId, setActiveDocumentId] = useState(null);
  const [titleDraft, setTitleDraft] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isSavingSnapshot, setIsSavingSnapshot] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [message, setMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [editorSnapshot, setEditorSnapshot] = useState(null);
  const [accessDeniedDocumentId, setAccessDeniedDocumentId] = useState(null);
  const [notFoundDocumentId, setNotFoundDocumentId] = useState(null);
  const [members, setMembers] = useState([]);
  const [memberUserId, setMemberUserId] = useState('');
  const [memberRole, setMemberRole] = useState('viewer');
  const [sharePermission, setSharePermission] = useState('viewer');
  const [shareLink, setShareLink] = useState('');
  const [showMemberPanel, setShowMemberPanel] = useState(false);
  const [showSharePanel, setShowSharePanel] = useState(false);
  const [selectedMemberId, setSelectedMemberId] = useState(null);
  const [memberQuery, setMemberQuery] = useState('');
  const [activeMemberIndex, setActiveMemberIndex] = useState(0);
  const memberPanelRef = useRef(null);
  const sharePanelRef = useRef(null);

  const requestedDocumentId = useMemo(() => {
    const nextRequestedId = routeDocumentId ? Number(routeDocumentId) : null;
    return Number.isFinite(nextRequestedId) ? nextRequestedId : null;
  }, [routeDocumentId]);

  const activeDocument = useMemo(() => documents.find((document) => document.id === activeDocumentId) ?? documents[0] ?? null, [activeDocumentId, documents]);
  const activeRole = normalizeRole(documentAccess[activeDocument?.id] || activeDocument?.myRole || 'no_access');
  const isReadOnly = activeRole === 'viewer';
  const isEditable = activeRole === 'editor' || activeRole === 'owner';
  const filteredMembers = useMemo(() => {
    const q = memberQuery.trim().toLowerCase();
    if (!q) return members;
    return members.filter((m) => `${m.nickname || ''} ${m.userId} ${m.role}`.toLowerCase().includes(q));
  }, [members, memberQuery]);

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
    const shareToken = searchParams.get('shareToken');
    if (!activeDocumentId || !shareToken) return;

    let cancelled = false;
    async function applyShareToken() {
      const response = await joinByShareLink(activeDocumentId, shareToken);
      if (cancelled) return;

      if (!response.ok) {
        setErrorMessage(response.data?.message || '加入文档失败');
        return;
      }

      setMessage(`已通过分享链接加入文档（${response.data?.data?.role || 'viewer'}）`);
      await reloadDocument(activeDocumentId).catch(() => null);
      const next = new URLSearchParams(searchParams);
      next.delete('shareToken');
      setSearchParams(next, { replace: true });
    }

    void applyShareToken();
    return () => {
      cancelled = true;
    };
  }, [activeDocumentId, searchParams, setSearchParams]);

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

  useEffect(() => {
    if (!showMemberPanel && !showSharePanel) return;
    const onPointerDown = (event) => {
      const target = event.target;
      if (showMemberPanel && memberPanelRef.current && memberPanelRef.current.contains(target)) return;
      if (showSharePanel && sharePanelRef.current && sharePanelRef.current.contains(target)) return;
      setShowMemberPanel(false);
      setShowSharePanel(false);
      setSelectedMemberId(null);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [showMemberPanel, showSharePanel]);

  useEffect(() => {
    if (activeMemberIndex >= filteredMembers.length) {
      setActiveMemberIndex(0);
    }
  }, [activeMemberIndex, filteredMembers.length]);

  useEffect(() => {
    const onKeyDown = (event) => {
      const isSave = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's';
      if (!isSave) return;
      event.preventDefault();
      if (!activeDocument || !isEditable) return;
      void handleSaveSnapshot();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activeDocument, isEditable, editorSnapshot]);

  const loadMembers = async (documentId) => {
    const response = await fetchDocumentMembers(documentId);
    if (!response.ok) {
      setMembers([]);
      return;
    }
    setMembers(response.data?.data || []);
  };

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
    await loadMembers(documentId);
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

  const handleInviteOrUpdateMember = async () => {
    if (!activeDocument) return;
    const userId = Number(memberUserId);
    if (!Number.isFinite(userId) || userId <= 0) {
      setErrorMessage('请输入有效的用户 ID');
      return;
    }
    const response = await upsertDocumentMember(activeDocument.id, { userId, role: memberRole });
    if (!response.ok) {
      setErrorMessage(response.data?.message || '保存成员失败');
      return;
    }
    setMessage('成员已更新');
    setMemberUserId('');
    await loadMembers(activeDocument.id);
  };

  const handleChangeMemberRole = async (userId, nextRole) => {
    if (!activeDocument) return;
    const response = await upsertDocumentMember(activeDocument.id, { userId, role: nextRole });
    if (!response.ok) {
      setErrorMessage(response.data?.message || '切换成员权限失败');
      return;
    }
    setMessage('成员权限已切换');
    setSelectedMemberId(null);
    await loadMembers(activeDocument.id);
  };

  const handleRemoveMember = async (userId) => {
    if (!activeDocument) return;
    const response = await removeDocumentMember(activeDocument.id, userId);
    if (!response.ok) {
      setErrorMessage(response.data?.message || '移除成员失败');
      return;
    }
    setMessage('成员已移除');
    await loadMembers(activeDocument.id);
  };

  const handleCreateShareLink = async () => {
    if (!activeDocument) return;
    const response = await createShareLink(activeDocument.id, { permission: sharePermission });
    if (!response.ok) {
      setErrorMessage(response.data?.message || '生成分享链接失败');
      return;
    }
    const data = response.data?.data;
    if (!data?.shareToken) {
      setErrorMessage('生成分享链接失败');
      return;
    }
    const absolute = `${window.location.origin}/documents/${activeDocument.id}?shareToken=${data.shareToken}`;
    setShareLink(absolute);
    setMessage('分享链接已生成');
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

  const handleSaveSnapshot = async () => {
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

    const currentContent = editorContentRef.current ?? editorSnapshot ?? snapshotToTiptapContent(activeDocument.latestSnapshot);
    if (!currentContent) {
      setErrorMessage('暂无可保存内容');
      return;
    }

    setIsSavingSnapshot(true);
    setErrorMessage('');
    setMessage('');
    try {
      const response = await updateDocumentSnapshot(activeDocument.id, { latestSnapshot: JSON.stringify(currentContent) });
      if (!response.ok) throw new Error(response.data?.message || '保存文档内容失败');
      setMessage('文档内容已保存');
      await reloadDocument(activeDocument.id).catch(() => null);
    } catch (error) {
      if (error instanceof Error && /没有权限|权限/i.test(error.message)) setAccessDeniedDocumentId(activeDocument.id);
      setErrorMessage(error instanceof Error ? error.message : '保存文档内容失败');
    } finally {
      setIsSavingSnapshot(false);
    }
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
            <div className="editor-action-group" style={{ position: 'relative' }}>
              <article><strong>作者：</strong><span>{activeDocument?.ownerName || '-'}</span></article>
              <article><strong>我的角色：</strong><span>{activeRole === 'no_access' ? '无权限' : activeRole}</span></article>
              <article><strong>更新时间：</strong><span>{activeDocument?.updatedAt ? new Date(activeDocument.updatedAt).toLocaleString() : '-'}</span></article>
              <button type="button" className="secondary-button" onClick={handleRefresh} disabled={!activeDocument}>刷新</button>
              <button type="button" className="secondary-button" onClick={handleDeleteDocument} disabled={!activeDocument || isDeleting || !isEditable || activeRole !== 'owner'}>{isDeleting ? '删除中...' : '删除文档'}</button>
              {activeRole === 'owner' ? (
                <>
                  <div style={{ position: 'relative' }} ref={memberPanelRef}>
                    <button type="button" className="secondary-button" onClick={() => { setShowMemberPanel((v) => !v); setShowSharePanel(false); }}>管理成员</button>
                    {showMemberPanel ? (
                      <div className="panel" style={{ position: 'absolute', right: 0, top: '44px', zIndex: 9999, width: 380, maxHeight: 460, overflow: 'auto' }}>
                        <h4 style={{ marginTop: 0, marginBottom: 10 }}>成员管理</h4>
                        <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                          <input value={memberUserId} onChange={(e) => setMemberUserId(e.target.value)} placeholder="用户 ID" />
                          <button type="button" className="secondary-button" onClick={handleInviteOrUpdateMember}>邀请</button>
                        </div>
                        <input
                          value={memberQuery}
                          onChange={(e) => { setMemberQuery(e.target.value); setActiveMemberIndex(0); }}
                          placeholder="搜索成员（用户名/ID/权限）"
                          style={{ marginBottom: 10, width: '100%' }}
                        />
                        <div
                          className="stack-list"
                          style={{ gap: 6 }}
                          tabIndex={0}
                          onKeyDown={(e) => {
                            if (!filteredMembers.length) return;
                            if (e.key === 'ArrowDown') {
                              e.preventDefault();
                              setActiveMemberIndex((i) => Math.min(i + 1, filteredMembers.length - 1));
                              return;
                            }
                            if (e.key === 'ArrowUp') {
                              e.preventDefault();
                              setActiveMemberIndex((i) => Math.max(i - 1, 0));
                              return;
                            }
                            if (e.key === 'Enter') {
                              e.preventDefault();
                              const m = filteredMembers[activeMemberIndex];
                              if (m) setSelectedMemberId((current) => (current === m.userId ? null : m.userId));
                            }
                            if (e.key === 'Escape') {
                              setShowMemberPanel(false);
                              setSelectedMemberId(null);
                            }
                          }}
                        >
                          {filteredMembers.map((m, index) => (
                            <div
                              key={m.userId}
                              className="stack-list__item"
                              style={{ cursor: 'default', border: index === activeMemberIndex ? '1px solid var(--accent-9, #6366f1)' : undefined, background: index === activeMemberIndex ? 'rgba(99,102,241,0.06)' : undefined }}
                            >
                              <button
                                type="button"
                                className="secondary-button"
                                style={{ width: '100%', textAlign: 'left' }}
                                onMouseEnter={() => setActiveMemberIndex(index)}
                                onClick={() => setSelectedMemberId((current) => (current === m.userId ? null : m.userId))}
                              >
                                {(m.nickname || `用户${m.userId}`)} · {m.role}
                              </button>
                              {selectedMemberId === m.userId && m.role !== 'owner' ? (
                                <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                                  <button type="button" className="secondary-button" onClick={() => handleChangeMemberRole(m.userId, 'viewer')}>viewer</button>
                                  <button type="button" className="secondary-button" onClick={() => handleChangeMemberRole(m.userId, 'editor')}>editor</button>
                                  <button type="button" className="secondary-button" onClick={() => handleChangeMemberRole(m.userId, 'no_access')}>no_access</button>
                                  <button type="button" className="secondary-button" onClick={() => handleRemoveMember(m.userId)}>移除</button>
                                </div>
                              ) : null}
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>
                  <div style={{ position: 'relative' }} ref={sharePanelRef}>
                    <button type="button" className="secondary-button" onClick={() => { setShowSharePanel((v) => !v); setShowMemberPanel(false); }}>分享邀请链接</button>
                    {showSharePanel ? (
                      <div className="panel" style={{ position: 'absolute', right: 0, top: '44px', zIndex: 9999, width: 320 }}>
                        <h4 style={{ marginTop: 0, marginBottom: 10 }}>分享邀请链接</h4>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          <button type="button" className="secondary-button" onClick={() => setSharePermission('viewer')}>viewer</button>
                          <button type="button" className="secondary-button" onClick={() => setSharePermission('editor')}>editor</button>
                          <button type="button" className="primary-button" onClick={handleCreateShareLink}>生成链接</button>
                        </div>
                        <p style={{ margin: '8px 0 0 0', fontSize: 12 }}>当前权限：{sharePermission}</p>
                        {shareLink ? <input style={{ marginTop: 8, width: '100%' }} value={shareLink} readOnly /> : null}
                      </div>
                    ) : null}
                  </div>
                </>
              ) : null}
            </div>
          </div>

          {activeDocument ? (
            <div>
              <div className="document-detail__header document-detail__header--inline">
                <label className="auth-field document-detail__title-field">
                  <input value={titleDraft} onChange={(event) => setTitleDraft(event.target.value)} placeholder="请输入标题" disabled={!isEditable} />
                </label>
                <button type="button" className="primary-button" onClick={handleSaveTitle} disabled={isSaving || !isEditable}>{isSaving ? '保存中...' : '保存标题'}</button>
                <button type="button" className="secondary-button" onClick={handleSaveSnapshot} disabled={isSavingSnapshot || !isEditable}>{isSavingSnapshot ? '内容保存中...' : '保存内容（Ctrl+S）'}</button>
              </div>

              <div className="document-detail__body">
                <div className="document-content-placeholder">
                  <span className="panel-kicker">Content</span>
                  <h4>文档内容</h4>
                  <SimpleEditor
                    key={`${activeDocument.id}-${isEditable ? 'edit' : 'read'}`}
                    initialContent={editorSnapshot || snapshotToTiptapContent(activeDocument.latestSnapshot)}
                    onContentChange={isEditable ? (content) => { editorContentRef.current = content; handleEditorChange(content); } : undefined}
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
