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
<<<<<<< HEAD
=======
  const aiAbortControllerRef = useRef(null);
>>>>>>> e6b7d087e5aa6f0db2ab83ba648163b12fdb9357
  
  /**
   * 过滤文章
   * @returns 过滤后的文章
   */
  const filteredArticles = useMemo(() => {
    const q = searchText.trim().toLowerCase();
    if (!q) return articles;
    return articles.filter((article) => `${article.title} ${article.summary}`.toLowerCase().includes(q));
  }, [articles, searchText]);

  /**
   * 加载文章列表
   */
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

  /**
   * 加载文章详情
   * @param articleId 文章 ID
   */
  async function loadArticle(articleId) {
    // 如果文章 ID 不存在，则返回
    if (!articleId) return;
    // 加载文章详情
    const response = await fetchKnowledgeArticle(articleId);
    // 如果加载失败，则设置错误信息
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
      if (aiAbortControllerRef.current) {
        aiAbortControllerRef.current.abort();
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
    /**
     * 处理 beforeunload 事件
     */
    const handleBeforeUnload = () => {
      // 如果存在未保存更改，且文章 ID 存在，且没有保存承诺，则保存草稿
      if (hasUnsavedChanges && articleDetail?.articleId && !savingPromiseRef.current) {
        void saveArticle(draftContent, 'autosave');
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [hasUnsavedChanges, draftContent, articleDetail?.articleId]);

  /**
   * 创建文章
   */
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

  /**
   * 保存文章
   * @param content 内容
   * @param saveSource 保存来源
   */
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

  /**
   * 手动保存
   */
  async function handleManualSave() {
    await saveArticle(draftContent, 'manual');
  }

  /**
   * 删除文章
   * @param articleId 文章 ID
   */
  async function handleDeleteArticle(articleId) {
    // 删除文章
    const response = await deleteKnowledgeArticle(articleId);
    // 如果删除失败，则设置错误信息
    if (!response.ok) {
      setErrorMessage(response.data?.message || '删除失败');
      return;
    }
    setMessage('文章已删除');
    setArticles((current) => current.filter((item) => item.articleId !== articleId));
    setActiveArticleId((current) => (normalizeArticleId(current) === normalizeArticleId(articleId) ? null : current));
  }

  /**
   * 切换文章
   * @param nextArticleId 下一个文章 ID
   */
  async function switchArticle(nextArticleId) {
    if (!nextArticleId || nextArticleId === activeArticleId || isSwitchingArticle) return;
    setIsSwitchingArticle(true);
    // 保存草稿
    if (hasUnsavedChanges) {
      await saveArticle(draftContent, 'manual');
    }
    setActiveArticleId(normalizeArticleId(nextArticleId));
    navigate(`/knowledge-base/${nextArticleId}`);
    setIsSwitchingArticle(false);
  }

  /**
   * 回滚文章
   * @param versionNo 版本号
   */
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
    // 设置自动保存定时器
    autosaveTimerRef.current = window.setInterval(() => {
      void saveArticle(draftContent, 'autosave');
    }, 60_000);
    return () => {
      if (autosaveTimerRef.current) {
        clearInterval(autosaveTimerRef.current);
      }
    };
  }, [hasUnsavedChanges, draftContent, articleDetail?.articleId]);

  /**
   * 应用 AI 结果到编辑器
   * @param resultAction 结果动作
   * @param aiText AI 文本
   * @returns 是否应用成功
   */
  const applyAiResultToEditor = useCallback((resultAction, aiText) => {
    const editor = editorRef.current;
    // 如果编辑器引用不存在，或 AI 文本为空，则返回 false
    if (!editor || !aiText?.trim()) return false;

    // 获取 AI 文本
    const text = aiText.trim();
    // 渲染 HTML
    const renderedHtml = md.render(text);

    // 如果结果动作是替换，则替换选中的文本
    if (resultAction === 'replace') {
      // 获取选中的文本
      const { from, to } = editor.state.selection;
      // 如果选中的文本不为空，则替换选中的文本
      if (from !== to) {
        editor.chain().focus().insertContentAt({ from, to }, renderedHtml).run();
      } else {
        editor.chain().focus().insertContent(renderedHtml).run();
      }
      return true;
    }

    // 如果结果动作是插入到后面，则插入到后面
    if (resultAction === 'insertAfter') {
      // 获取选中的文本的末尾位置
      const pos = editor.state.selection.to;
      // 插入到后面
      editor.chain().focus().insertContentAt(pos, renderedHtml).run();
      return true;
    }

    // 如果结果动作是追加块，则追加块
    if (resultAction === 'appendBlock') {
      // 追加块
      editor.chain().focus('end').insertContent(renderedHtml).run();
      return true;
    }

    return false;
  }, []);

  useEffect(() => {
    /**
     * 处理点击外部事件
     */
    const onClickOutside = () => {
      setAiMenu((prev) => (prev.visible ? { ...prev, visible: false } : prev));
    };
    document.addEventListener('click', onClickOutside);
    return () => document.removeEventListener('click', onClickOutside);
  }, []);

  useEffect(() => {
    if (!aiMenu.visible) return;

    /**
     * 更新锚点位置
     */
    const updateAnchorPosition = () => {
      // 获取选中的文本范围
      const range = selectionRangeRef.current;
      if (!range) return;
      // 获取选中的文本范围的矩形
      const rect = range.getBoundingClientRect();
      // 如果矩形不存在，或矩形的宽度为 0，且高度为 0，则返回
      if (!rect || (rect.width === 0 && rect.height === 0)) return;
      const nextX = Math.max(12, Math.min(window.innerWidth - 436, rect.left + rect.width / 2 - 180));
      const nextY = Math.max(12, rect.bottom + 8);
      setAiMenu((prev) => ({ ...prev, x: nextX, y: nextY }));
    };

    /**
     * 处理选中文本变化
     */
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

  /**
<<<<<<< HEAD
=======
   * 终止 AI 生成
   */
  function handleAbortAiExecute() {
    if (!aiAbortControllerRef.current) return;
    aiAbortControllerRef.current.abort();
  }

  /**
>>>>>>> e6b7d087e5aa6f0db2ab83ba648163b12fdb9357
   * 执行 AI
   */
  async function handleAiExecute() {
    if (!articleDetail?.articleId || !aiChatInput.trim() || isAiRunning) return;
    const abortController = new AbortController();
    aiAbortControllerRef.current = abortController;
    setIsAiRunning(true);
    setMessage('');
    setErrorMessage('');

    try {
      const requestId = `req-${Date.now()}`;
<<<<<<< HEAD
      const response = await executeEditorAi({
        articleId: Number(articleDetail.articleId),
        requestId,
        entryPoint: 'context-menu',
        selectedText: aiSelectedText,
        surroundingContext: JSON.stringify(draftContent || EMPTY_DOC).slice(0, 500),
        chatInput: aiChatInput.trim(),
      });
=======
      const response = await executeEditorAi(
        {
          articleId: Number(articleDetail.articleId),
          requestId,
          entryPoint: 'context-menu',
          selectedText: aiSelectedText,
          surroundingContext: JSON.stringify(draftContent || EMPTY_DOC).slice(0, 500),
          chatInput: aiChatInput.trim(),
        },
        { signal: abortController.signal },
      );
>>>>>>> e6b7d087e5aa6f0db2ab83ba648163b12fdb9357

      if (!response.ok || !response.data?.success) {
        setErrorMessage(response.data?.message || 'AI 执行失败');
        return;
      }

      const aiData = response.data?.data;
      const aiText = aiData?.outputText || '';
      const resultAction = aiData?.resultAction || 'previewOnly';

      if (!aiText && resultAction !== 'previewOnly') {
        setErrorMessage('AI 未返回结果');
        return;
      }

      if (resultAction === 'previewOnly') {
        setMessage(aiText || 'AI 已完成预览建议');
        setAiChatInput('');
        setAiMenu((prev) => ({ ...prev, visible: false }));
        return;
      }

      const applied = applyAiResultToEditor(resultAction, aiText);
      // 如果应用失败，则设置错误信息
      if (!applied) {
        setErrorMessage('当前 AI 返回动作暂不支持自动写入');
        return;
      }

      // 如果编辑器引用存在，则设置草稿内容
      if (editorRef.current) {
        // 获取最新的内容
        const latest = editorRef.current.getJSON();
        // 设置草稿内容
        setDraftContent(latest);
        // 设置有未保存更改
        setHasUnsavedChanges(true);
      }
      setAiChatInput('');
      setAiMenu((prev) => ({ ...prev, visible: false }));
      setMessage(`AI 文本处理完成（${resultAction}），请确认后保存`);
    } catch (e) {
<<<<<<< HEAD
      setErrorMessage('AI 请求异常，请稍后重试');
    } finally {
=======
      if (e?.name === 'AbortError') {
        setMessage('已中断 AI 生成');
      } else {
        setErrorMessage('AI 请求异常，请稍后重试');
      }
    } finally {
      aiAbortControllerRef.current = null;
>>>>>>> e6b7d087e5aa6f0db2ab83ba648163b12fdb9357
      setIsAiRunning(false);
    }
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
            className={`kb-editor-workbench ${isAiRunning ? 'is-ai-generating' : ''}`}
            onContextMenuCapture={(e) => {
              // 如果 AI 正在运行，则不处理右键菜单
              if (isAiRunning) return;
              // 阻止默认行为
              e.preventDefault();
              e.stopPropagation();
              // 获取选中的文本
              const selection = window.getSelection();
              // 获取选中的文本
              const selected = selection?.toString() || '';
              // 如果选中的文本不为空，则设置选中的文本
              if (selection && selection.rangeCount > 0 && !selection.getRangeAt(0).collapsed) {
                selectionRangeRef.current = selection.getRangeAt(0).cloneRange();
                // 获取选中的文本的矩形
                const rect = selectionRangeRef.current.getBoundingClientRect();
                // 计算下一个 X 坐标
                const nextX = Math.max(12, Math.min(window.innerWidth - 436, rect.left + rect.width / 2 - 180));
                // 计算下一个 Y 坐标
                const nextY = Math.max(12, rect.bottom + 8);
                // 设置 AI 菜单的可见性
                setAiMenu({ visible: true, x: nextX, y: nextY });
              } else {
                selectionRangeRef.current = null;
                setAiMenu({ visible: true, x: e.clientX, y: e.clientY });
              }
              setAiSelectedText(selected.trim());
            }}
          >
            {/* 编辑器 */}
            <SimpleEditor
              // 初始内容
              initialContent={initialContent}  
              // 是否只读
              readOnly={isAiRunning}
              // 编辑器准备就绪
              onEditorReady={(editor) => {
                // 设置编辑器引用
                editorRef.current = editor;
              }} 
              // 内容改变
              onContentChange={(nextContent) => {
                // 如果 AI 正在运行，则不处理内容改变
                if (isAiRunning) return;
                // 设置草稿内容
                setDraftContent(nextContent);
                // 设置有未保存更改
                setHasUnsavedChanges(true);
              }}
            />

            {/* AI 正在生成中 */}
            {isAiRunning ? (
              <div className="kb-ai-generating-overlay" role="status" aria-live="polite">
                <div className="kb-ai-generating-card">
                  <div className="kb-ai-generating-spinner" />
                  <div>
                    <strong>AI 正在生成中...</strong>
                    <p>请稍候，生成完成后会自动写入编辑器。</p>
                  </div>
<<<<<<< HEAD
=======
                  <button type="button" className="secondary-button" onClick={handleAbortAiExecute}>中断生成</button>
>>>>>>> e6b7d087e5aa6f0db2ab83ba648163b12fdb9357
                </div>
              </div>
            ) : null}
          </div>

          {/* AI 菜单 */}
          {aiMenu.visible ? (
            <div
              className="kb-ai-context-dialog"
              style={{ left: aiMenu.x, top: aiMenu.y }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="kb-ai-context-dialog__title">AI 文本操作</div>
              <textarea
                className="kb-ai-context-dialog__input"
                // AI 聊天输入
                value={aiChatInput}
                // AI 聊天输入改变
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
