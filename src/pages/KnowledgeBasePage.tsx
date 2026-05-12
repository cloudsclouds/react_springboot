// @ts-nocheck
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MarkdownIt from 'markdown-it';
import { useNavigate, useParams } from 'react-router-dom';
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';
import {
  createKnowledgeArticle,
  deleteKnowledgeArticle,
  executeEditorAi,
  fetchKnowledgeArticle,
  fetchKnowledgeArticleVersions,
  fetchKnowledgeArticles,
  rollbackKnowledgeArticle,
  updateKnowledgeArticle,
} from '@/api/kb';
import '@/styles/knowledgeBase.css';

const EMPTY_DOC = { type: 'doc', content: [{ type: 'paragraph' }] };
const md = new MarkdownIt({ html: false, linkify: true, breaks: true });

function safeParseContent(content) {
  if (!content) return EMPTY_DOC;
  if (typeof content === 'object') return content;
  try {
    return JSON.parse(content);
  } catch {
    return EMPTY_DOC;
  }
}

function normalizeArticleId(value) {
  return value == null ? null : String(value);
}


export default function KnowledgeBasePage() {
  const navigate = useNavigate();
  const { articleId } = useParams();
  const [articles, setArticles] = useState([]);
  const [activeArticleId, setActiveArticleId] = useState(null);
  const [articleDetail, setArticleDetail] = useState(null);
  const [versions, setVersions] = useState([]);
  const [searchText, setSearchText] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [newArticleTitle, setNewArticleTitle] = useState('新知识文章');
  const [draftContent, setDraftContent] = useState(EMPTY_DOC);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [isSwitchingArticle, setIsSwitchingArticle] = useState(false);
  const [aiMenu, setAiMenu] = useState({ visible: false, x: 0, y: 0 });
  const [aiChatInput, setAiChatInput] = useState('');
  const [aiSelectedText, setAiSelectedText] = useState('');
  const [isAiRunning, setIsAiRunning] = useState(false);
  const autosaveTimerRef = useRef(null);
  const savingPromiseRef = useRef(null);
  const editorRef = useRef(null);
  const selectionRangeRef = useRef(null);
  const filteredArticles = useMemo(() => {
    const q = searchText.trim().toLowerCase();
    if (!q) return articles;
    return articles.filter((article) => `${article.title} ${article.summary}`.toLowerCase().includes(q));
  }, [articles, searchText]);

  async function loadArticles() {
    setIsLoading(true);
    const response = await fetchKnowledgeArticles();
    if (!response.ok) {
      setErrorMessage(response.data?.message || '加载文章列表失败');
      setIsLoading(false);
      return;
    }
    const list = response.data?.data || [];
    setArticles(list);
    setActiveArticleId((current) => normalizeArticleId(articleId) || current || normalizeArticleId(list[0]?.articleId) || null);
    setIsLoading(false);
  }

  async function loadArticle(articleId) {
    if (!articleId) return;
    const response = await fetchKnowledgeArticle(articleId);
    if (!response.ok) {
      setErrorMessage(response.data?.message || '加载文章详情失败');
      return;
    }
    const detail = response.data?.data || null;
    setArticleDetail(detail);
    setDraftContent(detail?.content || EMPTY_DOC);
    setHasUnsavedChanges(false);
    const versionResponse = await fetchKnowledgeArticleVersions(articleId);
    if (versionResponse.ok) {
      setVersions(versionResponse.data?.data || []);
    }
  }

  useEffect(() => {
    void loadArticles();
  }, []);

  useEffect(() => {
    if (!activeArticleId) return;
    void loadArticle(activeArticleId);
    if (activeArticleId !== articleId) {
      navigate(`/knowledge-base/${activeArticleId}`, { replace: true });
    }
  }, [activeArticleId, articleId, navigate]);

  useEffect(() => {
    return () => {
      if (autosaveTimerRef.current) {
        clearInterval(autosaveTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!articleId) {
      setActiveArticleId((current) => normalizeArticleId(current) || null);
      return;
    }
    setActiveArticleId((current) => (normalizeArticleId(current) === normalizeArticleId(articleId) ? current : normalizeArticleId(articleId)));
  }, [articleId]);

  useEffect(() => {
    const handleBeforeUnload = () => {
      if (hasUnsavedChanges && articleDetail?.articleId && !savingPromiseRef.current) {
        void saveArticle(draftContent, 'autosave');
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [hasUnsavedChanges, draftContent, articleDetail?.articleId]);

  async function handleCreateArticle() {
    setMessage('');
    setErrorMessage('');
    const response = await createKnowledgeArticle({
      title: newArticleTitle.trim() || '新知识文章',
      summary: '请填写摘要',
      content: EMPTY_DOC,
    });
    if (!response.ok) {
      setErrorMessage(response.data?.message || '创建失败');
      return;
    }
    setMessage('文章已创建');
    const createdArticleId = response.data.data.articleId;
    const createdArticle = {
      articleId: createdArticleId,
      title: response.data.data.title,
      summary: '请填写摘要',
      updatedAt: new Date().toISOString(),
      status: 0,
    };
    setArticles((current) => [createdArticle, ...current]);
    setActiveArticleId(normalizeArticleId(createdArticleId));
    navigate(`/knowledge-base/${createdArticleId}`, { replace: true });
  }

  async function saveArticle(content, saveSource) {
    if (!articleDetail?.articleId || isSaving || savingPromiseRef.current) return;
    setIsSaving(true);
    const currentArticleId = articleDetail.articleId;
    const savePromise = updateKnowledgeArticle(currentArticleId, {
      title: articleDetail.title,
      summary: articleDetail.summary,
      content,
      saveSource,
    });
    savingPromiseRef.current = savePromise;
    const response = await savePromise;
    savingPromiseRef.current = null;
    if (!response.ok) {
      setErrorMessage(response.data?.message || '保存失败');
      setIsSaving(false);
      return;
    }
    const changed = Boolean(response.data?.data?.changed);
    setMessage(changed ? (saveSource === 'autosave' ? '已自动保存' : '已保存') : '内容未变化');
    setHasUnsavedChanges(false);
    setIsSaving(false);
    setArticles((current) =>
      current.map((item) =>
        item.articleId === currentArticleId
          ? { ...item, title: articleDetail.title, summary: articleDetail.summary, updatedAt: new Date().toISOString() }
          : item,
      ),
    );
    if (articleDetail?.articleId === currentArticleId) {
      await loadArticle(currentArticleId);
    }
  }

  async function handleManualSave() {
    await saveArticle(draftContent, 'manual');
  }

  async function handleDeleteArticle(articleId) {
    const response = await deleteKnowledgeArticle(articleId);
    if (!response.ok) {
      setErrorMessage(response.data?.message || '删除失败');
      return;
    }
    setMessage('文章已删除');
    setArticles((current) => current.filter((item) => item.articleId !== articleId));
    setActiveArticleId((current) => (normalizeArticleId(current) === normalizeArticleId(articleId) ? null : current));
  }

  async function switchArticle(nextArticleId) {
    if (!nextArticleId || nextArticleId === activeArticleId || isSwitchingArticle) return;
    setIsSwitchingArticle(true);
    if (hasUnsavedChanges) {
      await saveArticle(draftContent, 'manual');
    }
    setActiveArticleId(normalizeArticleId(nextArticleId));
    navigate(`/knowledge-base/${nextArticleId}`);
    setIsSwitchingArticle(false);
  }

  async function handleRollback(versionNo) {
    if (!articleDetail?.articleId) return;
    const response = await rollbackKnowledgeArticle(articleDetail.articleId, versionNo);
    if (!response.ok) {
      setErrorMessage(response.data?.message || '回滚失败');
      return;
    }
    setMessage(`已回滚到版本 ${versionNo}`);
    setArticles((current) =>
      current.map((item) =>
        item.articleId === articleDetail.articleId
          ? { ...item, title: articleDetail.title, summary: articleDetail.summary, updatedAt: new Date().toISOString() }
          : item,
      ),
    );
    await loadArticle(articleDetail.articleId);
  }

  useEffect(() => {
    if (!hasUnsavedChanges) return;
    if (autosaveTimerRef.current) {
      clearInterval(autosaveTimerRef.current);
    }
    autosaveTimerRef.current = window.setInterval(() => {
      void saveArticle(draftContent, 'autosave');
    }, 60_000);
    return () => {
      if (autosaveTimerRef.current) {
        clearInterval(autosaveTimerRef.current);
      }
    };
  }, [hasUnsavedChanges, draftContent, articleDetail?.articleId]);

  const applyAiResultToEditor = useCallback((resultAction, aiText) => {
    const editor = editorRef.current;
    if (!editor || !aiText?.trim()) return false;

    const text = aiText.trim();
    const renderedHtml = md.render(text);

    if (resultAction === 'replace') {
      const { from, to } = editor.state.selection;
      if (from !== to) {
        editor.chain().focus().insertContentAt({ from, to }, renderedHtml).run();
      } else {
        editor.chain().focus().insertContent(renderedHtml).run();
      }
      return true;
    }

    if (resultAction === 'insertAfter') {
      const pos = editor.state.selection.to;
      editor.chain().focus().insertContentAt(pos, renderedHtml).run();
      return true;
    }

    if (resultAction === 'appendBlock') {
      editor.chain().focus('end').insertContent(renderedHtml).run();
      return true;
    }

    return false;
  }, []);

  useEffect(() => {
    const onClickOutside = () => {
      setAiMenu((prev) => (prev.visible ? { ...prev, visible: false } : prev));
    };
    document.addEventListener('click', onClickOutside);
    return () => document.removeEventListener('click', onClickOutside);
  }, []);

  useEffect(() => {
    if (!aiMenu.visible) return;

    const updateAnchorPosition = () => {
      const range = selectionRangeRef.current;
      if (!range) return;
      const rect = range.getBoundingClientRect();
      if (!rect || (rect.width === 0 && rect.height === 0)) return;
      const nextX = Math.max(12, Math.min(window.innerWidth - 436, rect.left + rect.width / 2 - 180));
      const nextY = Math.max(12, rect.bottom + 8);
      setAiMenu((prev) => ({ ...prev, x: nextX, y: nextY }));
    };

    const onSelectionChange = () => {
      const selection = window.getSelection();
      if (!selection || selection.rangeCount === 0) return;
      const range = selection.getRangeAt(0);
      if (range.collapsed) return;
      selectionRangeRef.current = range.cloneRange();
      setAiSelectedText(selection.toString().trim());
      updateAnchorPosition();
    };

    updateAnchorPosition();
    window.addEventListener('scroll', updateAnchorPosition, true);
    window.addEventListener('resize', updateAnchorPosition);
    document.addEventListener('selectionchange', onSelectionChange);

    return () => {
      window.removeEventListener('scroll', updateAnchorPosition, true);
      window.removeEventListener('resize', updateAnchorPosition);
      document.removeEventListener('selectionchange', onSelectionChange);
    };
  }, [aiMenu.visible]);

  async function handleAiExecute() {
    if (!articleDetail?.articleId || !aiChatInput.trim() || isAiRunning) return;
    setIsAiRunning(true);
    setMessage('');
    setErrorMessage('');
    const requestId = `req-${Date.now()}`;
    const response = await executeEditorAi({
      articleId: Number(articleDetail.articleId),
      requestId,
      entryPoint: 'context-menu',
      selectedText: aiSelectedText,
      surroundingContext: JSON.stringify(draftContent || EMPTY_DOC).slice(0, 500),
      chatInput: aiChatInput.trim(),
    });
    if (!response.ok || !response.data?.success) {
      setErrorMessage(response.data?.message || 'AI 执行失败');
      setIsAiRunning(false);
      return;
    }

    const aiData = response.data?.data;
    const aiText = aiData?.outputText || '';
    const resultAction = aiData?.resultAction || 'previewOnly';

    if (!aiText && resultAction !== 'previewOnly') {
      setErrorMessage('AI 未返回结果');
      setIsAiRunning(false);
      return;
    }

    if (resultAction === 'previewOnly') {
      setMessage(aiText || 'AI 已完成预览建议');
      setIsAiRunning(false);
      setAiChatInput('');
      setAiMenu((prev) => ({ ...prev, visible: false }));
      return;
    }

    const applied = applyAiResultToEditor(resultAction, aiText);
    if (!applied) {
      setErrorMessage('当前 AI 返回动作暂不支持自动写入');
      setIsAiRunning(false);
      return;
    }

    if (editorRef.current) {
      const latest = editorRef.current.getJSON();
      setDraftContent(latest);
      setHasUnsavedChanges(true);
    }
    setAiChatInput('');
    setAiMenu((prev) => ({ ...prev, visible: false }));
    setMessage(`AI 文本处理完成（${resultAction}），请确认后保存`);
    setIsAiRunning(false);
  }

  const initialContent = useMemo(() => safeParseContent(articleDetail?.content), [articleDetail?.content]);

  return (
    <section className="page-shell kb-page">
      <header className="page-hero kb-hero">
        <div>
          <span className="panel-kicker">Knowledge Base</span>
          <h2>知识库 AI 编辑平台</h2>
        </div>
        <div className="kb-hero__actions">
          <input value={newArticleTitle} onChange={(e) => setNewArticleTitle(e.target.value)} className="kb-input" placeholder="新文章标题" />
          <button className="primary-button" type="button" onClick={handleCreateArticle}>新建文章</button>
        </div>
      </header>

      <div className="kb-toolbar">
        <input className="kb-input kb-input--search" placeholder="搜索文章标题或摘要" value={searchText} onChange={(e) => setSearchText(e.target.value)} />
        <div className="kb-feedback">
          {message ? <span className="kb-success">{message}</span> : null}
          {errorMessage ? <span className="kb-error">{errorMessage}</span> : null}
        </div>
      </div>

      <div className="kb-layout">
        <aside className="kb-sidebar panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Articles</span>
              <h3>文章列表</h3>
            </div>
          </div>

          {isLoading ? <p className="kb-empty">正在加载文章...</p> : null}
          <div className="kb-article-list">
            {filteredArticles.map((article) => (
              <button
                key={article.articleId}
                type="button"
                className={`kb-article-card ${normalizeArticleId(activeArticleId) === normalizeArticleId(article.articleId) ? 'is-active' : ''}`}
                onClick={() => {
                  void switchArticle(article.articleId);
                }}
              >
                <div className="kb-article-card__top">
                  <strong>{article.title}</strong>
                  <small>{article.updatedAt}</small>
                </div>
                <span>{article.summary || '暂无摘要'}</span>
              </button>
            ))}
          </div>
        </aside>

        <main className="kb-editor-panel panel">
          <div className="panel-header kb-editor-panel__header">
            <div>
              <span className="panel-kicker">Editor</span>
              <h3>{articleDetail?.title || '请选择一篇文章'}</h3>
            </div>
            <div className="kb-editor-panel__actions">
              <button type="button" className="secondary-button" onClick={handleManualSave} disabled={isSaving || !articleDetail || !hasUnsavedChanges}>保存</button>
              <button type="button" className="secondary-button" onClick={() => articleDetail && handleDeleteArticle(articleDetail.articleId)} disabled={!articleDetail}>删除</button>
            </div>
          </div>

          <div
            className="kb-editor-workbench"
            onContextMenu={(e) => {
              e.preventDefault();
              const selection = window.getSelection();
              const selected = selection?.toString() || '';
              if (selection && selection.rangeCount > 0 && !selection.getRangeAt(0).collapsed) {
                selectionRangeRef.current = selection.getRangeAt(0).cloneRange();
                const rect = selectionRangeRef.current.getBoundingClientRect();
                const nextX = Math.max(12, Math.min(window.innerWidth - 436, rect.left + rect.width / 2 - 180));
                const nextY = Math.max(12, rect.bottom + 8);
                setAiMenu({ visible: true, x: nextX, y: nextY });
              } else {
                selectionRangeRef.current = null;
                setAiMenu({ visible: true, x: e.clientX, y: e.clientY });
              }
              setAiSelectedText(selected.trim());
            }}
          >
            <SimpleEditor
              initialContent={initialContent}
              onEditorReady={(editor) => {
                editorRef.current = editor;
              }}
              onContentChange={(nextContent) => {
                setDraftContent(nextContent);
                setHasUnsavedChanges(true);
              }}
            />
          </div>

          {aiMenu.visible ? (
            <div
              className="kb-ai-context-dialog"
              style={{ left: aiMenu.x, top: aiMenu.y }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="kb-ai-context-dialog__title">AI 文本操作</div>
              <textarea
                className="kb-ai-context-dialog__input"
                value={aiChatInput}
                onChange={(e) => setAiChatInput(e.target.value)}
                placeholder="请输入需求，例如：请润色这段话"
              />
              <div className="kb-ai-context-dialog__actions">
                <button type="button" className="secondary-button" onClick={() => setAiMenu((prev) => ({ ...prev, visible: false }))}>取消</button>
                <button type="button" className="primary-button" onClick={() => { void handleAiExecute(); }} disabled={isAiRunning || !aiChatInput.trim()}>
                  {isAiRunning ? '发送中...' : '发送'}
                </button>
              </div>
            </div>
          ) : null}

          <section className="kb-section kb-version-card">
            <div className="panel-header">
              <div>
                <span className="panel-kicker">Versions</span>
                <h3>版本历史</h3>
              </div>
            </div>
            <div className="kb-version-list">
              {versions.map((version) => (
                <article key={`${version.versionNo}-${version.createdAt}`} className="kb-version-item">
                  <div>
                    <strong>版本 {version.versionNo}</strong>
                    <span>{version.source}</span>
                  </div>
                  <small>{version.createdAt}</small>
                  <button type="button" className="secondary-button" onClick={() => handleRollback(version.versionNo)}>回滚</button>
                </article>
              ))}
            </div>
          </section>
        </main>
      </div>
    </section>
  );
}
