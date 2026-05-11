import { useEffect, useMemo, useRef, useState } from 'react';
import MarkdownIt from 'markdown-it';
import markdownItKatex from 'markdown-it-katex';
import 'katex/dist/katex.min.css';
import { useNavigate, useParams } from 'react-router-dom';
import { getJson, postJson } from '../api/http';

// @ts-nocheck

type ConversationItem = {
  conversationId: number;
  title: string;
  summary?: string;
  lastMessageAt?: string;
  messageCount?: number;
};

type MessageItem = {
  messageId?: number;
  role: 'user' | 'assistant' | 'system';
  content: string;
  status?: string;
  requestId?: string;
  createdAt?: string;
  updatedAt?: string;
  citations?: ChatCitation[];
};

type ConversationDetail = {
  conversationId: number;
  title: string;
  summary?: string;
  createdAt?: string;
  updatedAt?: string;
  messages?: MessageItem[];
};

type ChatCitation = {
  citationId: string;
  articleId: number;
  articleTitle: string;
  chunkId: number;
  chunkIndex: number;
  chunkText: string;
  score?: number;
};

type ChatEvent =
  | { type?: 'rag-start'; conversationId?: number; requestId?: string }
  | { type?: 'rag-result'; conversationId?: number; requestId?: string; citations?: ChatCitation[] }
  | { type?: 'message-start'; conversationId?: number; requestId?: string; citations?: ChatCitation[] }
  | { type?: 'message-delta'; content?: string }
  | { type?: 'message-end'; conversationId?: number; messageId?: number; status?: string; citations?: ChatCitation[] }
  | { type?: 'message-stop'; conversationId?: number; messageId?: number; status?: string; citations?: ChatCitation[] }
  | { type?: 'message-error'; message?: string };

const API_BASE = process.env.REACT_APP_JAVA_API_BASE_URL || 'http://localhost:8080/api';

const formatTime = (value?: string) => {
  if (!value) return '刚刚';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
};

const markdownRenderer = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true,
  typographer: true,
}).use(markdownItKatex);

const renderMarkdown = (markdown: string) => markdownRenderer.render(markdown || '');

const normalizeConversation = (item: ConversationItem) => ({
  id: String(item.conversationId),
  conversationId: item.conversationId,
  name: item.title,
  updatedAt: formatTime(item.lastMessageAt),
  preview: item.summary?.trim() || (item.messageCount ? `共 ${item.messageCount} 条消息` : '暂无消息'),
});

export default function AIChatPage() {
  const historyWrapRef = useRef<HTMLDivElement | null>(null);
  const messagesWrapRef = useRef<HTMLDivElement | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const activeConversationIdRef = useRef('');
  const navigate = useNavigate();
  const params = useParams();

  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);
  const [conversations, setConversations] = useState<Array<ReturnType<typeof normalizeConversation>>>([]);
  const [activeConversationId, setActiveConversationId] = useState('');
  const [messagesByConversation, setMessagesByConversation] = useState<Record<string, MessageItem[]>>({});
  const [messageCitationsByConversation, setMessageCitationsByConversation] = useState<Record<string, ChatCitation[]>>({});
  const [inputValue, setInputValue] = useState('');
  const [isSending, setIsSending] = useState(false);

  const routeConversationId = params.conversationId ? String(params.conversationId) : '';

  activeConversationIdRef.current = activeConversationId;

  const activeConversation = conversations.find((conversation) => conversation.id === activeConversationId) ?? null;
  const activeMessages = messagesByConversation[activeConversationId] ?? [];

  useEffect(() => {
    if (!activeConversationId || activeConversationId === routeConversationId) return;
    navigate(`/ai/${activeConversationId}`, { replace: true });
  }, [activeConversationId, navigate, routeConversationId]);

  const scrollMessagesToBottom = () => {
    requestAnimationFrame(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    });
  };

  const historyItems = useMemo(
    () =>
      conversations.map((conversation) => ({
        ...conversation,
        preview: conversation.id === activeConversationId ? '当前会话' : conversation.preview,
      })),
    [activeConversationId, conversations],
  );

  const mergeMessages = (conversationId: string, updater: (current: MessageItem[]) => MessageItem[]) => {
    setMessagesByConversation((current) => {
      const existing = current[conversationId] ?? [];
      return { ...current, [conversationId]: updater(existing) };
    });
  };

  const loadConversations = async () => {
    const response = await getJson<ConversationItem[]>('/ai/conversations');
    if (!response.ok) throw new Error('查询会话列表失败');
    const items = Array.isArray(response.data) ? response.data.map(normalizeConversation) : [];
    setConversations(items);
    return items;
  };

  const loadConversationDetail = async (conversationId: string) => {
    const response = await getJson<ConversationDetail>(`/ai/conversations/${conversationId}/detail`);
    if (!response.ok) throw new Error('查询会话详情失败');

    const detail = response.data;
    const normalizedConversation = normalizeConversation({
      conversationId: detail.conversationId,
      title: detail.title,
      summary: detail.summary,
      lastMessageAt: detail.updatedAt,
      messageCount: detail.messages?.length ?? 0,
    });

    const normalizedMessages = Array.isArray(detail.messages) ? detail.messages : [];
    setConversations((current) => [
      normalizedConversation,
      ...current.filter((item) => item.id !== String(detail.conversationId)),
    ]);
    setMessagesByConversation((current) => ({
      ...current,
      [String(detail.conversationId)]: normalizedMessages,
    }));
    const assistantCitations = normalizedMessages
      .filter((message) => message.role === 'assistant' && Array.isArray(message.citations))
      .flatMap((message) => message.citations ?? []);
    setMessageCitationsByConversation((current) => ({
      ...current,
      [String(detail.conversationId)]: assistantCitations,
    }));
    if (normalizedMessages.length > 0) {
      scrollMessagesToBottom();
    }

    return normalizedConversation;
  };

  const createConversation = async () => {
    const response = await postJson<{ conversationId: number; title: string; summary?: string }>('/ai/conversations', { title: '新对话' });
    if (!response.ok) throw new Error('创建会话失败');
    const created = normalizeConversation({
      conversationId: response.data.conversationId,
      title: response.data.title,
      summary: response.data.summary,
      lastMessageAt: new Date().toISOString(),
      messageCount: 0,
    });
    setConversations((current) => [created, ...current.filter((item) => item.id !== created.id)]);
    setMessagesByConversation((current) => ({ ...current, [created.id]: [] }));
    setActiveConversationId(created.id);
    navigate(`/ai/${created.id}`, { replace: true });
    scrollMessagesToBottom();
    return created;
  };

  const resolveConversationFromRoute = async (conversationId: string) => {
    try {
      const loadedConversation = await loadConversationDetail(conversationId);
      return loadedConversation;
    } catch {
      return createConversation();
    }
  };

  const ensureConversation = async () => {
    if (activeConversation) return activeConversation;
    return createConversation();
  };

  useEffect(() => {
    let mounted = true;

    (async () => {
      try {
        const targetConversationId = routeConversationId;
        if (targetConversationId) {
          const resolvedConversation = await resolveConversationFromRoute(targetConversationId);
          if (!mounted) return;
          setActiveConversationId(String(resolvedConversation.conversationId));
          return;
        }

        const created = await createConversation();
        if (mounted) setActiveConversationId(created.id);
      } catch {
        if (!mounted) return;
      }
    })();

    return () => {
      mounted = false;
      abortRef.current?.abort();
    };
  }, [routeConversationId]);

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent | TouchEvent) => {
      const target = event.target as Node | null;
      if (isHistoryOpen && historyWrapRef.current && target && !historyWrapRef.current.contains(target)) {
        setIsHistoryOpen(false);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('touchstart', handlePointerDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('touchstart', handlePointerDown);
    };
  }, [isHistoryOpen]);

  const handleCreateChat = async () => {
    try {
      const created = await createConversation();
      setActiveConversationId(created.id);
    } catch {
    }
    setIsHistoryOpen(false);
  };

  const handleSelectConversation = async (conversationId: string) => {
    setIsHistoryOpen(false);
    try {
      const selected = await loadConversationDetail(conversationId);
      setActiveConversationId(String(selected.conversationId));
    } catch {
    }
  };

  const handleOpenHistory = async () => {
    const nextOpen = !isHistoryOpen;
    setIsHistoryOpen(nextOpen);
    if (!nextOpen) return;
    setIsHistoryLoading(true);
    try {
      await loadConversations();
    } catch {
      setConversations([]);
    } finally {
      setIsHistoryLoading(false);
    }
  };

  const handleStopSending = async () => {
    const conversationId = activeConversation?.conversationId;
    if (!conversationId) return;
    abortRef.current?.abort();
    setIsSending(false);
    try {
      await postJson('/ai/chat/stop', { conversationId });
    } catch (error) {
    }
  };

  const handleParseSseChunk = (chunk: string) => {
    chunk
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .forEach((line) => {
        if (!line.startsWith('data:')) return;
        const payload = line.replace(/^data:\s*/, '');
        if (!payload || payload === '[DONE]') return;
        try {
          const event = JSON.parse(payload) as ChatEvent;
          const conversationId = activeConversationIdRef.current;
          if (event.type === 'rag-result') {
            if (event.citations?.length) {
              setMessageCitationsByConversation((current) => ({
                ...current,
                [conversationId]: event.citations ?? [],
              }));
            }
            return;
          }
          if (event.type === 'message-start') {
            if (event.citations?.length) {
              setMessageCitationsByConversation((current) => ({
                ...current,
                [conversationId]: event.citations ?? [],
              }));
            }
            mergeMessages(conversationId, (current) => {
              const next = [...current];
              const lastItem = next[next.length - 1];
              if (!lastItem || lastItem.role !== 'assistant') {
                next.push({ role: 'assistant', content: '', status: 'GENERATING', citations: event.citations ?? [] });
                return next;
              }
              if (lastItem.status !== 'GENERATING') {
                next.push({ role: 'assistant', content: '', status: 'GENERATING', citations: event.citations ?? [] });
              }
              return next;
            });
            scrollMessagesToBottom();
            return;
          }
          if (event.type === 'message-delta') {
            mergeMessages(conversationId, (current) => {
              const next = [...current];
              const lastIndex = next.length - 1;
              if (lastIndex >= 0 && next[lastIndex].role === 'assistant' && next[lastIndex].status === 'GENERATING') {
                next[lastIndex] = { ...next[lastIndex], content: `${next[lastIndex].content || ''}${event.content ?? ''}` };
              }
              return next;
            });
            scrollMessagesToBottom();
            return;
          }
          if (event.type === 'message-end' || event.type === 'message-stop') {
            if (event.citations?.length) {
              setMessageCitationsByConversation((current) => ({
                ...current,
                [conversationId]: event.citations ?? [],
              }));
            }
            mergeMessages(conversationId, (current) => {
              const next = [...current];
              const lastIndex = next.length - 1;
              if (lastIndex >= 0 && next[lastIndex].role === 'assistant' && next[lastIndex].status === 'GENERATING') {
                next[lastIndex] = { ...next[lastIndex], status: event.status ?? 'COMPLETED', citations: event.citations ?? next[lastIndex].citations };
              }
              return next;
            });
            scrollMessagesToBottom();
            setIsSending(false);
            return;
          }
          if (event.type === 'message-error') {
            setIsSending(false);
          }
        } catch (error) {
        }
      });
  };

  const handleSendMessage = async () => {
    const content = inputValue.trim();
    if (!content || isSending) return;

    setInputValue('');

    const conversation = await ensureConversation();
    const conversationId = conversation.conversationId;
    const conversationKey = conversation.id;

    mergeMessages(conversationKey, (current) => [
      ...current,
      { role: 'user', content, status: 'COMPLETED' },
    ]);
    scrollMessagesToBottom();

    setIsSending(true);
    try {
      const controller = new AbortController();
      abortRef.current = controller;
      const response = await fetch(`${API_BASE}/ai/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(() => {
            try {
              const userStr = localStorage.getItem('paperdesk-user');
              if (!userStr) return {};
              const user = JSON.parse(userStr);
              const token = user.token || user.accessToken;
              return token ? { Authorization: `Bearer ${token}` } : {};
            } catch {
              return {};
            }
          })(),
        },
        body: JSON.stringify({ conversationId, content, useRag: true, topK: 5 }),
        signal: controller.signal,
      });

      if (!response.ok || !response.body) throw new Error('流式会话请求失败');

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';
        parts.forEach((part) => handleParseSseChunk(part));
      }
      if (buffer) handleParseSseChunk(buffer);
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
      } else {
      }
    } finally {
      setIsSending(false);
      abortRef.current = null;
    }
  };

  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">AI Chat</span>
          <h2>AI 聊天页</h2>
        </div>
      </header>

      <div className="workbench-columns">
        <section className="panel chat-stage">
          <div className="chat-stage__toolbar">
            <div>
              <p className="chat-stage__label">当前会话</p>
              <h3 className="chat-stage__title">{activeConversation?.name ?? '新建聊天'}</h3>
            </div>

            <div className="chat-stage__actions">
              <div className="chat-stage__history-wrap" ref={historyWrapRef}>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={handleOpenHistory}
                  aria-haspopup="menu"
                  aria-expanded={isHistoryOpen}
                  aria-label="打开历史记录"
                >
                  历史记录
                </button>

                {isHistoryOpen ? (
                  <div className="chat-stage__history-menu" role="menu" aria-label="历史记录列表">
                    {isHistoryLoading ? (
                      <div className="empty-state">正在加载历史会话…</div>
                    ) : historyItems.length > 0 ? (
                      historyItems.map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          className={`chat-stage__history-item ${activeConversationId === item.id ? 'is-active' : ''}`}
                          onClick={() => handleSelectConversation(item.id)}
                        >
                          <span className="chat-stage__history-name">{item.name}</span>
                          <span className="chat-stage__history-meta">{item.updatedAt}</span>
                          <span className="chat-stage__history-preview">{item.preview}</span>
                        </button>
                      ))
                    ) : (
                      <div className="empty-state">暂无历史会话</div>
                    )}
                  </div>
                ) : null}
              </div>

              <button type="button" className="primary-button" onClick={handleCreateChat}>
                新建聊天
              </button>
            </div>
          </div>

          <div className="chat-stage__messages" ref={messagesWrapRef}>
            {activeMessages.length > 0 ? (
              activeMessages.map((message, index) => (
                <article key={`${message.role}-${message.messageId ?? index}`} className={`chat-bubble chat-bubble--${message.role}`}>
                  <span className="chat-bubble__role">{message.role === 'assistant' ? 'AI' : '你'}</span>
                  <div
                    className="chat-bubble__content"
                    dangerouslySetInnerHTML={{
                      __html: renderMarkdown(message.content && message.content !== 'null' ? message.content : ''),
                    }}
                  />
                  {message.role === 'assistant' && Array.isArray(message.citations) && message.citations.length > 0 ? (
                    <div className="chat-bubble__citations">
                      {message.citations.map((citation) => (
                        <span key={citation.citationId} className="chat-citation-pill" title={citation.chunkText}>
                          [{citation.citationId}] {citation.articleTitle}
                        </span>
                      ))}
                    </div>
                  ) : null}
                  {message.createdAt ? <time className="chat-bubble__time">{formatTime(message.createdAt)}</time> : null}
                </article>
              ))
            ) : (
              <div className="empty-state empty-state--center">还没有消息，先发送一句试试。</div>
            )}
            <div className="chat-stage__citation-summary">
              {(messageCitationsByConversation[activeConversationId] ?? []).length > 0 ? (
                <>
                  <p>已检索到 {(messageCitationsByConversation[activeConversationId] ?? []).length} 条引用</p>
                  <div className="chat-stage__citation-list">
                    {(messageCitationsByConversation[activeConversationId] ?? []).map((citation) => (
                      <span key={citation.citationId} className="chat-citation-pill" title={citation.chunkText}>
                        [{citation.citationId}] {citation.articleTitle}
                      </span>
                    ))}
                  </div>
                </>
              ) : null}
            </div>
            <div ref={messagesEndRef} />
          </div>

          <div className="chat-stage__composer">
            <input
              type="text"
              value={inputValue}
              onChange={(event) => setInputValue(event.target.value)}
              placeholder="输入后点击发送"
              onKeyDown={(event) => {
                if (event.key === 'Enter') handleSendMessage();
              }}
            />
            {isSending ? (
              <button type="button" className="secondary-button" onClick={handleStopSending}>
                停止发送
              </button>
            ) : (
              <button type="button" className="primary-button" onClick={handleSendMessage}>
                发送
              </button>
            )}
          </div>
        </section>
      </div>
    </section>
  );
}
