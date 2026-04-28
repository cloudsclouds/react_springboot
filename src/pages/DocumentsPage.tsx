// @ts-nocheck
import { useEffect, useMemo, useRef, useState } from 'react';
import { createDocument, deleteDocument, fetchDocumentMetadata, fetchDocuments, updateDocumentTitle } from '@/api/document';
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';

const DEFAULT_NEW_TITLE = '未命名文档';

function parseSnapshot(snapshot) {
  if (!snapshot) {
    return null;
  }

  if (typeof snapshot === 'string') {
    try {
      return JSON.parse(snapshot);
    } catch {
      return { text: snapshot };
    }
  }

  return snapshot;
}

function snapshotToTiptapContent(snapshot) {
  const data = parseSnapshot(snapshot);
  if (!data) {
    return {
      type: 'doc',
      content: [{ type: 'paragraph' }],
    };
  }

  if (data.type === 'doc' && Array.isArray(data.content)) {
    return data;
  }

  const blocks = Array.isArray(data.blocks) ? data.blocks : [];
  const content = [];

  blocks.forEach((block) => {
    if (block.type === 'heading') {
      content.push({
        type: 'heading',
        attrs: { level: block.level || 1 },
        content: block.text ? [{ type: 'text', text: block.text }] : [],
      });
      return;
    }

    if (block.type === 'paragraph') {
      content.push({
        type: 'paragraph',
        content: block.text ? [{ type: 'text', text: block.text }] : [],
      });
      return;
    }

    if (block.type === 'list' && Array.isArray(block.items)) {
      const listType = block.ordered ? 'orderedList' : 'bulletList';
      content.push({
        type: listType,
        content: block.items.map((item) => ({
          type: 'listItem',
          content: [{ type: 'paragraph', content: [{ type: 'text', text: item }] }],
        })),
      });
    }
  });

  return {
    type: 'doc',
    content: content.length > 0 ? content : [{ type: 'paragraph' }],
  };
}

export default function DocumentsPage() {
  const editorContentRef = useRef(null);
  const [documents, setDocuments] = useState([]);
  const [activeDocumentId, setActiveDocumentId] = useState(null);
  const [titleDraft, setTitleDraft] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [message, setMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [editorSnapshot, setEditorSnapshot] = useState(null);

  const activeDocument = useMemo(
    () => documents.find((document) => document.id === activeDocumentId) ?? documents[0] ?? null,
    [activeDocumentId, documents]
  );

  useEffect(() => {
    async function loadDocuments() {
      setIsLoading(true);
      setErrorMessage('');

      const response = await fetchDocuments();
      if (!response.ok) {
        setErrorMessage(response.data?.message || '获取文档列表失败');
        setIsLoading(false);
        return;
      }

      const nextDocuments = response.data?.data || [];
      setDocuments(nextDocuments);
      setActiveDocumentId(nextDocuments[0]?.id ?? null);
      setIsLoading(false);
    }

    void loadDocuments();
  }, []);

  useEffect(() => {
    if (!activeDocumentId) {
      return;
    }

    void reloadDocument(activeDocumentId).catch((error) => {
      setErrorMessage(error instanceof Error ? error.message : '获取文档元数据失败');
    });
  }, [activeDocumentId]);

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
      throw new Error(response.data?.message || '获取文档元数据失败');
    }

    const metadata = response.data?.data ?? response.data;
    setDocuments((currentDocuments) =>
      currentDocuments.map((document) => (document.id === documentId ? { ...document, ...metadata } : document))
    );
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
      if (!response.ok) {
        throw new Error(response.data?.message || '创建文档失败');
      }

      const documentId = response.data?.data?.documentId ?? response.data?.documentId;
      if (!documentId) {
        throw new Error('创建文档失败：未返回文档 ID');
      }

      const nextDocument = {
        id: documentId,
        title: DEFAULT_NEW_TITLE,
        ownerId: 0,
        ownerName: '我',
        myRole: 'owner',
        updatedAt: new Date().toISOString(),
      };

      setDocuments((currentDocuments) => [nextDocument, ...currentDocuments]);
      setActiveDocumentId(documentId);
      setTitleDraft(DEFAULT_NEW_TITLE);
      setMessage('文档创建成功');

      try {
        await reloadDocument(documentId);
      } catch {
        // Ignore metadata refresh failure; optimistic creation is still usable.
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '创建文档失败');
    } finally {
      setIsCreating(false);
    }
  };

  const handleSaveTitle = async () => {
    if (!activeDocument) {
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
      if (!response.ok) {
        throw new Error(response.data?.message || '修改文档标题失败');
      }

      setDocuments((currentDocuments) =>
        currentDocuments.map((document) =>
          document.id === activeDocument.id ? { ...document, title: nextTitle, updatedAt: new Date().toISOString() } : document
        )
      );
      setMessage('文档标题已更新');
      await reloadDocument(activeDocument.id).catch(() => null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '修改文档标题失败');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteDocument = async () => {
    if (!activeDocument) {
      return;
    }

    const confirmed = window.confirm(`确定删除文档「${activeDocument.title}」吗？`);
    if (!confirmed) {
      return;
    }

    setIsDeleting(true);
    setErrorMessage('');
    setMessage('');

    try {
      const response = await deleteDocument(activeDocument.id);
      if (!response.ok) {
        throw new Error(response.data?.message || '删除文档失败');
      }

      setDocuments((currentDocuments) => {
        const remaining = currentDocuments.filter((document) => document.id !== activeDocument.id);
        setActiveDocumentId(remaining[0]?.id ?? null);
        return remaining;
      });
      setMessage('文档已删除');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '删除文档失败');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleRefresh = async () => {
    if (!activeDocument) {
      return;
    }

    setErrorMessage('');
    try {
      await reloadDocument(activeDocument.id);
      setMessage('文档信息已刷新');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '刷新失败');
    }
  };

  const handleEditorChange = async (nextContent) => {
    if (!activeDocument) {
      return;
    }

    setEditorSnapshot(nextContent);

    try {
      const response = await updateDocumentSnapshot(activeDocument.id, {
        latestSnapshot: JSON.stringify(nextContent),
      });
      if (!response.ok) {
        throw new Error(response.data?.message || '保存文档内容失败');
      }

      setDocuments((currentDocuments) =>
        currentDocuments.map((document) =>
          document.id === activeDocument.id
            ? { ...document, latestSnapshot: JSON.stringify(nextContent), updatedAt: new Date().toISOString() }
            : document
        )
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '保存文档内容失败');
    }
  };

  if (isLoading) {
    return (
      <section className="page-shell">
        <header className="page-hero">
          <div>
            <span className="panel-kicker">Documents</span>
            <h2>文档管理</h2>
          </div>
        </header>
        <div className="panel">
          <p>正在加载文档列表...</p>
        </div>
      </section>
    );
  }

  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Documents</span>
          <h2>文档管理</h2>
        </div>
        
      </header>

      <div className="documents-layout">
        <aside className="panel side-panel documents-layout__list">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">List</span>
              <h3>文档列表</h3>
            </div>
            <button type="button" className="secondary-button" onClick={handleCreateDocument} disabled={isCreating}>
              {isCreating ? '创建中...' : '新建文档'}
            </button>
          </div>

          <div className="stack-list">
            {documents.length === 0 ? (
              <div className="empty-state">
                <strong>暂无文档</strong>
                <span>点击“新建文档”开始创建第一篇文档。</span>
              </div>
            ) : (
              documents.map((document) => (
                <button
                  key={document.id}
                  type="button"
                  className={`stack-list__item ${document.id === activeDocument?.id ? 'is-active' : ''}`}
                  onClick={() => setActiveDocumentId(document.id)}
                >
                  <strong>{document.title}</strong>
                  <span>
                    #{document.id} · {document.myRole || 'viewer'} · {document.updatedAt ? new Date(document.updatedAt).toLocaleString() : '未知时间'}
                  </span>
                </button>
              ))
            )}
          </div>
        </aside>

        <section className="panel editor-stage documents-layout__content">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Metadata</span>
              <h3>{activeDocument ? '文档详情' : '请选择文档'}</h3>
            </div>
            <div className="editor-action-group">
              <article>
                <strong>作者：</strong>
                <span>{activeDocument.ownerName || '-'}</span>
              </article>
              <article>
                <strong>我的角色：</strong>
                <span>{activeDocument.myRole || '-'}</span>
              </article>
              <article>
                <strong>更新时间：</strong>
                <span>{activeDocument.updatedAt ? new Date(activeDocument.updatedAt).toLocaleString() : '-'}</span>
              </article>
              <button type="button" className="secondary-button" onClick={handleRefresh} disabled={!activeDocument}>
                刷新
              </button>
              <button type="button" className="secondary-button" onClick={handleDeleteDocument} disabled={!activeDocument || isDeleting}>
                {isDeleting ? '删除中...' : '删除文档'}
              </button>      
            </div>
          </div>

          {activeDocument ? (
            <div>
              <div className="document-detail__header document-detail__header--inline">
                <label className="auth-field document-detail__title-field">
                  <input
                    value={titleDraft}
                    onChange={(event) => setTitleDraft(event.target.value)}
                    placeholder="请输入标题"
                  />
                </label>
                <button type="button" className="primary-button" onClick={handleSaveTitle} disabled={isSaving}>
                  {isSaving ? '保存中...' : '保存标题'}
                </button>
              </div>

              <div className="document-detail__body">
                <div className="document-content-placeholder">
                  <span className="panel-kicker">Content</span>
                  <h4>文档内容</h4>
                  <SimpleEditor
                    key={activeDocument.id}
                    initialContent={editorSnapshot || snapshotToTiptapContent(activeDocument.latestSnapshot)}
                    onContentChange={handleEditorChange}
                  />
                </div>
              </div>

              <div className="document-detail__footer">
                
              </div>
            </div>
          ) : (
            <div className="editor-stage__paper empty-state empty-state--center">
              <strong>没有可查看的文档</strong>
            </div>
          )}
        </section>
      </div>
    </section>
  );
}
