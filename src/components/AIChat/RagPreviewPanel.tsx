import { useEffect, useMemo, useState } from 'react';
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';
import type { ArticlePreview, ChatCitation } from '../../types/AIChat';

type RagPreviewPanelProps = {
  activeConversationId: string;
  isOpen: boolean;
  citations: ChatCitation[];
  activeCitationId: string;
  previewLoading: boolean;
  preview: ArticlePreview | null;
  onToggleOpen: () => void;
  onOpenCitation: (citation: ChatCitation) => void;
  buildArticleDoc: (preview: ArticlePreview | null) => any;
  extractTextFromDoc: (node: any) => string;
  extractChunkPreview: (articleText: string, citation: ChatCitation) => string;
};

const citationLabel = (citationId: string) => `citation-${citationId}`;

export default function RagPreviewPanel({
  isOpen,
  citations,
  activeCitationId,
  previewLoading,
  preview,
  onToggleOpen,
  onOpenCitation,
  buildArticleDoc,
  extractTextFromDoc,
  extractChunkPreview,
}: RagPreviewPanelProps) {
  const [showFullText, setShowFullText] = useState(false);
  const activeCitation = citations.find((citation) => citation.citationId === activeCitationId) ?? citations[0] ?? null;

  useEffect(() => {
    setShowFullText(false);
  }, [activeCitationId, isOpen]);

  /**
   * 构建文章文档
   */ 
  const articleDoc = buildArticleDoc(preview);
  /**
   * 获取文章文本
   */
  const articleText = articleDoc ? extractTextFromDoc(articleDoc) : activeCitation?.chunkText ?? '';
  /**
   * 获取全文文本
   */
  const fullText = articleText || activeCitation?.chunkText || '';

  /**
   * 获取片段文本
   */
  const snippetText = useMemo(() => {
    if (!activeCitation) return '';
    if (previewLoading) return '正在加载预览…';
    return extractChunkPreview(articleText, activeCitation);
  }, [activeCitation, articleText, extractChunkPreview, previewLoading]);

  /**
   * 渲染 RAG 引用预览面板
   */
  return (
    <aside className={`panel rag-panel ai-chat-page__preview ${isOpen ? 'is-open' : 'is-collapsed'}`} aria-label="RAG 引用预览面板">
      <button type="button" className="rag-panel__collapse" onClick={onToggleOpen} aria-label={isOpen ? '收起预览面板' : '展开预览面板'}>
        {isOpen ? '‹' : '›'}
      </button>

      {isOpen ? (
        <div className="rag-panel__content rag-panel__content--preview-only">
          {activeCitation ? (
            <div className="rag-preview-only" id={citationLabel(activeCitation.citationId)} data-citation-id={activeCitation.citationId}>
              <div className="rag-preview-only__meta">
                <p className="chat-stage__label">引用预览</p>
                <h3 className="chat-stage__title">命中片段</h3>
                <span>article #{activeCitation.articleId} · chunk #{activeCitation.chunkIndex}</span>
              </div>

              <div className="rag-snippet-card">
                <p className="rag-snippet-card__content">{snippetText || '暂无片段内容'}</p>
                {typeof activeCitation.score === 'number' ? <span className="rag-snippet-card__score">相关度 {activeCitation.score.toFixed(4)}</span> : null}
              </div>


              <div className="rag-preview-only__actions">
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => {
                    onOpenCitation(activeCitation);
                    setShowFullText(true);
                  }}
                >
                  查看全文
                </button>
              </div>
            </div>
          ) : (
            <div className="empty-state empty-state--center">暂无引用，发送消息后会在这里显示 RAG 命中内容。</div>
          )}
        </div>
      ) : null}

      {isOpen && showFullText && activeCitation ? (
        <div className="rag-fulltext-overlay" role="dialog" aria-modal="true" aria-label="全文预览">
          <div className="rag-fulltext-sheet">
            <div className="rag-fulltext-sheet__header">
              <button type="button" className="secondary-button" onClick={() => setShowFullText(false)}>
                关闭
              </button>
            </div>
            <div className="rag-fulltext-sheet__body">
              <SimpleEditor
                initialContent={articleDoc || { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: fullText || '暂无内容' }] }] }}
                onContentChange={() => {}}
                readOnly
                highlightText={activeCitation.chunkText || ''}
              />
            </div>
          </div>
        </div>
      ) : null}
    </aside>
  );
}
