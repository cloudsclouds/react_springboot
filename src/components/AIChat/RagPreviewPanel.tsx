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
  activeConversationId,
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
  return (
    <aside className={`panel rag-panel ai-chat-page__preview ${isOpen ? 'is-open' : 'is-collapsed'}`} aria-label="RAG 引用预览面板">
      <button type="button" className="rag-panel__collapse" onClick={onToggleOpen} aria-label={isOpen ? '收起预览面板' : '展开预览面板'}>
        {isOpen ? '›' : '‹'}
      </button>

      {isOpen ? (
        <div className="rag-panel__content">
          <div className="rag-panel__header">
            <div>
              <p className="chat-stage__label">只读预览</p>
              <h3 className="chat-stage__title">文章全文</h3>
            </div>
            <span className="rag-panel__badge">{citations.length} 条引用</span>
          </div>

          <div className="rag-panel__body">
            {citations.length > 0 ? (
              citations.map((citation) => {
                const isSelected = activeCitationId === citation.citationId;
                const articleDoc = buildArticleDoc(preview);
                const articleText = articleDoc ? extractTextFromDoc(articleDoc) : citation.chunkText;
                const chunkPreview = extractChunkPreview(articleText, citation);
                const fullText = articleText;
                return (
                  <article key={citation.citationId} className={`rag-citation-card ${isSelected ? 'is-highlighted' : ''}`} id={citationLabel(citation.citationId)} data-citation-id={citation.citationId}>
                    <button type="button" className="rag-citation-card__toggle" onClick={() => onOpenCitation(citation)}>
                      <div className="rag-citation-card__meta">
                        <strong className="rag-citation-card__title">{citation.articleTitle}</strong>
                        <span>Chunk #{citation.chunkIndex}</span>
                        {typeof citation.score === 'number' ? <span>Score {citation.score.toFixed(4)}</span> : null}
                      </div>
                      <div className="rag-citation-card__summary">{previewLoading ? '正在加载预览…' : chunkPreview}</div>
                      <span className="rag-citation-card__hint">点击可展开 / 收起预览</span>
                    </button>
                    {isSelected ? (
                      <div className="rag-citation-card__reader">
                        <div className="rag-citation-card__reader-meta">
                          <span>只读全文预览</span>
                          <span>•</span>
                          <span>article #{citation.articleId}</span>
                          <span>•</span>
                          <span>chunk #{citation.chunkIndex}</span>
                        </div>
                        <SimpleEditor initialContent={articleDoc || { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: fullText || '暂无内容' }] }] }} onContentChange={() => {}} readOnly />
                      </div>
                    ) : null}
                  </article>
                );
              })
            ) : (
              <div className="empty-state empty-state--center">暂无引用，发送消息后会在这里显示 RAG 命中内容。</div>
            )}
          </div>
        </div>
      ) : null}
    </aside>
  );
}
