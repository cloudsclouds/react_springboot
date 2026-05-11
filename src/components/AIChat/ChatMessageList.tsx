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
              <span className="chat-bubble__role">{message.role === 'assistant' ? 'AI' : '我'}</span>
              <div className="chat-bubble__content" dangerouslySetInnerHTML={{ __html: renderMarkdownWithCitations(message.content && message.content !== 'null' ? message.content : '') }} />
            </article>
          );
        })
      ) : (
        <div className="empty-state empty-state--center">还没有消息，先发送一句试试。</div>
      )}
    </>
  );
}
