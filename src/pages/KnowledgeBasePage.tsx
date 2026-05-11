// @ts-nocheck
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';
import {
  createKnowledgeArticle,
  deleteKnowledgeArticle,
  fetchKnowledgeArticle,
  fetchKnowledgeArticleVersions,
  fetchKnowledgeArticles,
  rollbackKnowledgeArticle,
  updateKnowledgeArticle,
} from '@/api/kb';
import '@/styles/knowledgeBase.css';

const EMPTY_DOC = { type: 'doc', content: [{ type: 'paragraph' }] };

function safeParseContent(content) {
  if (!content) return EMPTY_DOC;
  if (typeof content === 'object') return content;
  try {
    return JSON.parse(content);
  } catch {
    return EMPTY_DOC;
  }
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
    setActiveArticleId((current) => articleId || current || list[0]?.articleId || null);
    setIsLoading(false);
  }

  async function loadArticle(articleId) {
    if (!articleId) return;
    const response = await fetchKnowledgeArticle(articleId);
    if (!response.ok) {
      setErrorMessage(response.data?.message || '加载文章详情失败');
      return;
    }
    setArticleDetail(response.data?.data || null);
    const versionResponse = await fetchKnowledgeArticleVersions(articleId);
    if (versionResponse.ok) {
      setVersions(versionResponse.data?.data || []);
    }
  }

  useEffect(() => {
    void loadArticles();
  }, [articleId]);

  useEffect(() => {
    if (!activeArticleId) return;
    void loadArticle(activeArticleId);
    if (activeArticleId !== articleId) {
      navigate(`/knowledge-base/${activeArticleId}`, { replace: true });
    }
  }, [activeArticleId, articleId, navigate]);

  useEffect(() => {
    if (!articleId) {
      setActiveArticleId((current) => current || null);
      return;
    }
    setActiveArticleId((current) => (current === articleId ? current : articleId));
  }, [articleId]);

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
    await loadArticles();
    const createdArticleId = response.data.data.articleId;
    setActiveArticleId(createdArticleId);
    navigate(`/knowledge-base/${createdArticleId}`, { replace: true });
  }

  async function handleSaveArticle(content) {
    if (!articleDetail?.articleId) return;
    setIsSaving(true);
    const response = await updateKnowledgeArticle(articleDetail.articleId, {
      title: articleDetail.title,
      summary: articleDetail.summary,
      content,
      saveSource: 'manual',
    });
    if (!response.ok) {
      setErrorMessage(response.data?.message || '保存失败');
      setIsSaving(false);
      return;
    }
    setMessage('已保存');
    setIsSaving(false);
    await loadArticles();
    await loadArticle(articleDetail.articleId);
  }

  async function handleDeleteArticle(articleId) {
    const response = await deleteKnowledgeArticle(articleId);
    if (!response.ok) {
      setErrorMessage(response.data?.message || '删除失败');
      return;
    }
    setMessage('文章已删除');
    await loadArticles();
    setActiveArticleId((current) => (current === articleId ? articles[0]?.articleId || null : current));
  }

  async function handleRollback(versionNo) {
    if (!articleDetail?.articleId) return;
    const response = await rollbackKnowledgeArticle(articleDetail.articleId, versionNo);
    if (!response.ok) {
      setErrorMessage(response.data?.message || '回滚失败');
      return;
    }
    setMessage(`已回滚到版本 ${versionNo}`);
    await loadArticles();
    await loadArticle(articleDetail.articleId);
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
                className={`kb-article-card ${activeArticleId === article.articleId ? 'is-active' : ''}`}
                onClick={() => {
                  setActiveArticleId(article.articleId);
                  navigate(`/knowledge-base/${article.articleId}`);
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
              <button type="button" className="secondary-button" onClick={() => handleSaveArticle(initialContent)} disabled={isSaving || !articleDetail}>保存</button>
              <button type="button" className="secondary-button" onClick={() => articleDetail && handleDeleteArticle(articleDetail.articleId)} disabled={!articleDetail}>删除</button>
            </div>
          </div>

          <div className="kb-editor-workbench">
            <SimpleEditor initialContent={initialContent} onContentChange={handleSaveArticle} />
          </div>

          <section className="kb-section">
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
