// @ts-nocheck
import { useEffect, useMemo, useRef, useState } from 'react';
import { CollaborativeEditor } from '@/components/tiptap-templates/collaborative/collaborative-editor';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:3001/api';

function toSnapshot(document) {
  return JSON.stringify({
    title: document.title,
    content: document.content,
  });
}

export default function OnlineEditorPage() {
  const [documents, setDocuments] = useState([]);
  const [activeDocumentId, setActiveDocumentId] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [saveStatus, setSaveStatus] = useState('idle');
  const [errorMessage, setErrorMessage] = useState('');
  const debounceRef = useRef(null);
  const snapshotsRef = useRef(new Map());

  const activeDocument = useMemo(
    () => documents.find((document) => document.id === activeDocumentId) ?? documents[0] ?? null,
    [activeDocumentId, documents]
  );

  useEffect(() => {
    async function loadDocuments() {
      setIsLoading(true);
      setErrorMessage('');

      try {
        const response = await fetch(`${API_BASE_URL}/documents`);
        if (!response.ok) {
          throw new Error('加载文档失败');
        }

        const payload = await response.json();
        setDocuments(payload);
        if (payload.length > 0) {
          setActiveDocumentId(payload[0].id);
          payload.forEach((document) => {
            snapshotsRef.current.set(document.id, toSnapshot(document));
          });
        }
      } catch (error) {
        setErrorMessage(error instanceof Error ? error.message : '加载文档失败');
      } finally {
        setIsLoading(false);
      }
    }

    void loadDocuments();
  }, []);

  useEffect(() => {
    if (!activeDocument) {
      return;
    }

    const snapshot = toSnapshot(activeDocument);
    const lastSnapshot = snapshotsRef.current.get(activeDocument.id);

    if (snapshot === lastSnapshot) {
      return;
    }

    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    setSaveStatus('saving');
    debounceRef.current = setTimeout(async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/documents/${activeDocument.id}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            title: activeDocument.title,
            content: activeDocument.content,
          }),
        });

        if (!response.ok) {
          throw new Error('自动保存失败');
        }

        const savedDocument = await response.json();
        snapshotsRef.current.set(savedDocument.id, toSnapshot(savedDocument));
        setDocuments((currentDocuments) =>
          currentDocuments.map((document) =>
            document.id === savedDocument.id ? savedDocument : document
          )
        );
        setSaveStatus('saved');
      } catch (error) {
        setSaveStatus('error');
        setErrorMessage(error instanceof Error ? error.message : '自动保存失败');
      }
    }, 700);

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [activeDocument]);

  const handleCreateDocument = async () => {
    try {
      setErrorMessage('');
      const response = await fetch(`${API_BASE_URL}/documents`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ title: '未命名文档' }),
      });

      if (!response.ok) {
        throw new Error('创建文档失败');
      }

      const document = await response.json();
      snapshotsRef.current.set(document.id, toSnapshot(document));
      setDocuments((currentDocuments) => [document, ...currentDocuments]);
      setActiveDocumentId(document.id);
      setSaveStatus('saved');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '创建文档失败');
    }
  };

  const handleTitleChange = (event) => {
    const nextTitle = event.target.value;
    setDocuments((currentDocuments) =>
      currentDocuments.map((document) =>
        document.id === activeDocumentId ? { ...document, title: nextTitle } : document
      )
    );
  };

  const handleContentChange = (nextContent) => {
    setDocuments((currentDocuments) =>
      currentDocuments.map((document) =>
        document.id === activeDocumentId ? { ...document, content: nextContent } : document
      )
    );
  };

  if (isLoading) {
    return (
      <section className="online-editor-shell">
        <h1>TipTap Editor</h1>
        <p>正在加载文档...</p>
      </section>
    );
  }

  if (!activeDocument) {
    return (
      <section className="online-editor-shell">
        <h1>TipTap Editor</h1>
        <button type="button" className="theme-toggle" onClick={handleCreateDocument}>
          创建第一篇文档
        </button>
      </section>
    );
  }

  return (
    <section className="online-editor-shell">
      <header className="online-editor-head">
        <span className="brand-kicker">在线文档编辑</span>
        <h1>TipTap Editor</h1>
        <p>支持创建文档与自动保存到 Nest API。</p>
      </header>

      <div className="online-editor-layout">
        <aside className="online-editor-sidebar">
          <div className="online-editor-sidebar__head">
            <div>
              <span className="section-label">文档列表</span>
              <h2>当前工作区</h2>
              <p>{documents.length} documents</p>
            </div>

            <button type="button" className="theme-toggle" onClick={handleCreateDocument}>
              + 新建文档
            </button>
          </div>

          <div className="online-editor-document-list" aria-label="文档列表">
            {documents.map((document) => {
              const isActive = document.id === activeDocument.id;

              return (
                <button
                  key={document.id}
                  type="button"
                  className={`online-editor-document-item ${isActive ? 'is-active' : ''}`}
                  onClick={() => setActiveDocumentId(document.id)}
                >
                  <strong>{document.title}</strong>
                  <span>{isActive ? '当前编辑' : '点击切换'}</span>
                </button>
              );
            })}
          </div>
        </aside>

        <section className="online-editor-main">
          <div className="online-editor-controls">
            <input
              className="online-editor-title-input"
              value={activeDocument.title}
              onChange={handleTitleChange}
              placeholder="输入文档标题"
            />

            <span className={`save-status save-status--${saveStatus}`}>
              {saveStatus === 'saving' ? '自动保存中...' : saveStatus === 'saved' ? '已自动保存' : saveStatus === 'error' ? '保存失败' : '未修改'}
            </span>
          </div>

          {errorMessage ? <p className="online-editor-error">{errorMessage}</p> : null}

          <CollaborativeEditor
            key={activeDocument.id}
            documentId={activeDocument.id}
            initialContent={activeDocument.content}
            onContentChange={handleContentChange}
            userName={`User-${Math.random().toString(36).slice(2, 9)}`}
          />
        </section>
      </div>
    </section>
  );
}