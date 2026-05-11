type HistoryItem = {
  id: string;
  name: string;
  updatedAt: string;
  preview: string;
};

type ChatHistoryMenuProps = {
  isOpen: boolean;
  isLoading: boolean;
  items: HistoryItem[];
  activeConversationId: string;
  onSelectConversation: (conversationId: string) => void;
  onCreateChat: () => void;
  onToggleOpen: () => void;
};

export default function ChatHistoryMenu({
  isOpen,
  isLoading,
  items,
  activeConversationId,
  onSelectConversation,
  onCreateChat,
  onToggleOpen,
}: ChatHistoryMenuProps) {
  return (
    <div className="chat-stage__history-wrap">
      <div className="chat-stage__history-actions-row">
        <button type="button" className="chat-stage__history-create" onClick={onCreateChat}>
          新建聊天
        </button>
        <button type="button" className="chat-stage__history-create" onClick={onToggleOpen} aria-haspopup="menu" aria-expanded={isOpen} aria-label="打开历史记录">
          历史记录
        </button>
      </div>

      {isOpen ? (
        <div className="chat-stage__history-menu" role="menu" aria-label="历史记录列表">
          {isLoading ? (
            <div className="empty-state">正在加载历史会话…</div>
          ) : items.length > 0 ? (
            <div className="chat-stage__history-list">
              {items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`chat-stage__history-item ${activeConversationId === item.id ? 'is-active' : ''}`}
                  onClick={() => onSelectConversation(item.id)}
                >
                  <span className="chat-stage__history-name">{item.name}</span>
                  <span className="chat-stage__history-meta">{item.updatedAt}</span>
                  <span className="chat-stage__history-preview">{item.preview}</span>
                </button>
              ))}
            </div>
          ) : (
            <div className="empty-state">暂无历史会话</div>
          )}
        </div>
      ) : null}
    </div>
  );
}
