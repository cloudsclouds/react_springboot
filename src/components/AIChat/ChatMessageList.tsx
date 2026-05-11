import type { ChatCitation, MessageItem } from '../../types/AIChat';

type ChatMessageListProps = {
  messages: MessageItem[];
  onOpenCitation: (citation: ChatCitation) => void;
  renderMarkdownWithCitations: (markdown: string) => string;
  parseCitations: (value?: ChatCitation[] | string | null) => ChatCitation[];
  formatTime: (value?: string) => string;
};

export default function ChatMessageList({ messages, onOpenCitation, renderMarkdownWithCitations, parseCitations, formatTime }: ChatMessageListProps) {
  return (
    <>
      {messages.length > 0 ? (
        messages.map((message, index) => {
          const citations = parseCitations(message.citations);
          return (
            <article key={`${message.role}-${message.messageId ?? index}`} className={`chat-bubble chat-bubble--${message.role}`}>
              <span className="chat-bubble__role">{message.role === 'assistant' ? 'AI' : '你'}</span>
              <div className="chat-bubble__content" dangerouslySetInnerHTML={{ __html: renderMarkdownWithCitations(message.content && message.content !== 'null' ? message.content : '') }} />
              {message.role === 'assistant' && message.status !== 'GENERATING' && citations.length > 0 ? (
                <div className="chat-bubble__citations">
                  {citations.map((citation) => (
                    <button key={citation.citationId} type="button" className="chat-citation-pill" title={citation.chunkText} onClick={() => onOpenCitation(citation)}>
                      [{citation.citationId}] {citation.articleTitle}
                    </button>
                  ))}
                </div>
              ) : null}
              {message.createdAt ? <time className="chat-bubble__time">{formatTime(message.createdAt)}</time> : null}
            </article>
          );
        })
      ) : (
        <div className="empty-state empty-state--center">还没有消息，先发送一句试试。</div>
      )}
    </>
  );
}
