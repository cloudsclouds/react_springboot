import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MarkdownIt from 'markdown-it';
import markdownItKatex from 'markdown-it-katex';
import 'katex/dist/katex.min.css';
import { useNavigate, useParams } from 'react-router-dom';
import ChatHistoryMenu from './ChatHistoryMenu';
import ChatMessageList from './ChatMessageList';
import RagPreviewPanel from './RagPreviewPanel';
import { getJson, postJson } from '../../api/http';
import { fetchKnowledgeArticle } from '../../api/kb';
import type {
  ArticlePreview,
  ChatCitation,
  ChatEvent,
  ConversationDetail,
  ConversationItem,
  MessageItem,
} from '../../types/AIChat';
import '../../styles/AIChat.css';

const parseCitations = (value?: ChatCitation[] | string | null): ChatCitation[] => {
  if (!value) return [];
  if (Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

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

const renderMarkdownWithCitations = (markdown: string) => {
  const html = renderMarkdown(markdown);
  return html.replace(/\[(c\d+)\]/g, '<button type="button" class="inline-citation" data-citation-id="$1">[$1]</button>');
};

const citationLabel = (citationId: string) => `citation-${citationId}`;

const buildArticleDoc = (preview: ArticlePreview | null) => {
  if (!preview?.content) return null;
  if (typeof preview.content === 'object') return preview.content;
  try {
    return JSON.parse(preview.content);
  } catch {
    return null;
  }
};

const extractChunkPreview = (articleText: string, citation: ChatCitation) => {
  const baseText = articleText || citation.chunkText || '';
  const startToken = citation.chunkText?.slice(0, 24) || citation.articleTitle;
  const start = baseText.indexOf(startToken);
  if (start >= 0) {
    return baseText.slice(start, Math.min(baseText.length, start + Math.max(420, citation.chunkText.length + 180)));
  }
  return baseText.slice(0, Math.max(citation.chunkText.length + 200, 360));
};

const extractTextFromDoc = (node: any): string => {
  if (!node) return '';
  if (typeof node === 'string') return node;
  if (Array.isArray(node)) return node.map(extractTextFromDoc).join('\n');
  if (typeof node !== 'object') return '';
  if (node.text) return node.text;
  if (Array.isArray(node.content)) return node.content.map(extractTextFromDoc).join('');
  if (Array.isArray(node.items)) return node.items.map(extractTextFromDoc).join('');
  return '';
};

const normalizeConversation = (item: ConversationItem) => ({
  id: String(item.conversationId),
  conversationId: item.conversationId,
  name: item.title,
  updatedAt: formatTime(item.lastMessageAt),
  preview: item.summary?.trim() || (item.messageCount ? `共 ${item.messageCount} 条消息` : '暂无消息'),
});

export default function AIChat() {
  const historyWrapRef = useRef<HTMLDivElement | null>(null);
  const messagesWrapRef = useRef<HTMLDivElement | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const activeConversationIdRef = useRef('');
  const activeRequestIdRef = useRef('');
  const navigate = useNavigate();
  const params = useParams();

  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);
  const [conversations, setConversations] = useState<Array<ReturnType<typeof normalizeConversation>>>([]);
  const [activeConversationId, setActiveConversationId] = useState('');
  const [messagesByConversation, setMessagesByConversation] = useState<Record<string, MessageItem[]>>({});
  const [messageCitationsByConversation, setMessageCitationsByConversation] = useState<Record<string, ChatCitation[]>>({});
  const [pendingCitationsByConversation, setPendingCitationsByConversation] = useState<Record<string, ChatCitation[]>>({});
  const [previewByConversation, setPreviewByConversation] = useState<Record<string, ArticlePreview | null>>({});
  const [previewOpenByConversation, setPreviewOpenByConversation] = useState<Record<string, boolean>>({});
  const [previewLoadingByConversation, setPreviewLoadingByConversation] = useState<Record<string, boolean>>({});
  const [inputValue, setInputValue] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [activeCitationId, setActiveCitationId] = useState('');

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

  const loadConversations = useCallback(async () => {
    const response = await getJson<ConversationItem[]>('/ai/conversations');
    if (!response.ok) throw new Error('查询会话列表失败');
    const items = Array.isArray(response.data) ? response.data.map(normalizeConversation) : [];
    setConversations(items);
    return items;
  }, []);

  const loadConversationDetail = useCallback(async (conversationId: string) => {
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
    setConversations((current) => [normalizedConversation, ...current.filter((item) => item.id !== String(detail.conversationId))]);
    setMessagesByConversation((current) => ({
      ...current,
      [String(detail.conversationId)]: normalizedMessages,
    }));
    const assistantCitations = normalizedMessages.filter((message) => message.role === 'assistant').flatMap((message) => parseCitations(message.citations));
    setMessageCitationsByConversation((current) => ({
      ...current,
      [String(detail.conversationId)]: assistantCitations,
    }));
    if (normalizedMessages.length > 0) scrollMessagesToBottom();

    return normalizedConversation;
  }, []);

  const createConversation = useCallback(async () => {
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
  }, [navigate]);

  const resolveConversationFromRoute = useCallback(async (conversationId: string) => {
    try {
      const loadedConversation = await loadConversationDetail(conversationId);
      return loadedConversation;
    } catch {
      return createConversation();
    }
  }, [createConversation, loadConversationDetail]);

  const resolveConversationFromRouteRef = useRef(resolveConversationFromRoute);
  const createConversationRef = useRef(createConversation);

  resolveConversationFromRouteRef.current = resolveConversationFromRoute;
  createConversationRef.current = createConversation;

  const ensureConversation = useCallback(async () => {
    if (activeConversation) return activeConversation;
    return createConversationRef.current();
  }, [activeConversation]);

  useEffect(() => {
    let mounted = true;

    (async () => {
      try {
        const targetConversationId = routeConversationId;
        if (targetConversationId) {
          const resolvedConversation = await resolveConversationFromRouteRef.current(targetConversationId);
          if (!mounted) return;
          setActiveConversationId(String(resolvedConversation.conversationId));
          return;
        }

        const created = await createConversationRef.current();
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

  const openPreviewForCitation = async (citation: ChatCitation) => {
    const conversationId = activeConversationId;
    if (!conversationId) return;
    const isSameCitation = activeCitationId === citation.citationId;
    setPreviewOpenByConversation((current) => ({ ...current, [conversationId]: true }));
    setActiveCitationId((current) => (isSameCitation ? current : citation.citationId));
    if (isSameCitation && previewByConversation[conversationId]?.articleId === citation.articleId) return;
    setPreviewLoadingByConversation((current) => ({ ...current, [conversationId]: true }));
    try {
      const response = await fetchKnowledgeArticle(citation.articleId);
      if (!response.ok) throw new Error('加载文章失败');
      const detail = response.data?.data;
      setPreviewByConversation((current) => ({
        ...current,
        [conversationId]: detail
          ? {
              articleId: detail.articleId,
              title: detail.title,
              summary: detail.summary,
              content: detail.content,
              updatedAt: detail.updatedAt,
            }
          : null,
      }));
    } catch {
      setPreviewByConversation((current) => ({
        ...current,
        [conversationId]: {
          articleId: citation.articleId,
          title: citation.articleTitle,
          summary: '加载失败，请重试',
          content: null,
        },
      }));
    } finally {
      setPreviewLoadingByConversation((current) => ({ ...current, [conversationId]: false }));
    }
  };

  const togglePreviewOpen = () => {
    const conversationId = activeConversationId;
    if (!conversationId) return;
    setPreviewOpenByConversation((current) => ({ ...current, [conversationId]: !current[conversationId] }));
  };

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
    const requestId = activeRequestIdRef.current;
    if (!conversationId || !requestId) return;
    abortRef.current?.abort();
    setIsSending(false);
    try {
      await postJson('/ai/chat/stop', { conversationId, requestId });
    } catch {
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
          const activeRequestId = activeRequestIdRef.current;
          if (!conversationId) return;
          if (event.requestId && activeRequestId && event.requestId !== activeRequestId) return;

          if (event.type === 'rag-start') {
            setPendingCitationsByConversation((current) => ({ ...current, [conversationId]: [] }));
            return;
          }

          if (event.type === 'rag-result') {
            setPendingCitationsByConversation((current) => ({ ...current, [conversationId]: event.citations ?? [] }));
            return;
          }

          if (event.type === 'message-start') {
            setPendingCitationsByConversation((current) => ({
              ...current,
              [conversationId]: event.citations ?? current[conversationId] ?? [],
            }));
            mergeMessages(conversationId, (current) => {
              const next = [...current];
              const assistantIndex = next.findIndex((item) => item.role === 'assistant' && item.status === 'GENERATING');
              if (assistantIndex === -1) {
                next.push({ role: 'assistant', content: '', status: 'GENERATING', requestId: event.requestId, citations: [] });
                return next;
              }
              next[assistantIndex] = {
                ...next[assistantIndex],
                status: 'GENERATING',
                requestId: event.requestId ?? next[assistantIndex].requestId,
                citations: [],
              };
              return next;
            });
            scrollMessagesToBottom();
            return;
          }

          if (event.type === 'message-delta') {
            mergeMessages(conversationId, (current) => {
              const next = [...current];
              const lastIndex = next.length - 1;
              if (lastIndex < 0) return next;
              const lastItem = next[lastIndex];
              if (lastItem.role !== 'assistant' || lastItem.status !== 'GENERATING') return next;
              next[lastIndex] = { ...lastItem, content: `${lastItem.content || ''}${event.content ?? ''}` };
              return next;
            });
            scrollMessagesToBottom();
            return;
          }

          if (event.type === 'message-end' || event.type === 'message-stop') {
            const finalCitations = event.citations ?? pendingCitationsByConversation[conversationId] ?? [];
            setMessageCitationsByConversation((current) => ({ ...current, [conversationId]: finalCitations }));
            setPendingCitationsByConversation((current) => ({ ...current, [conversationId]: finalCitations }));
            mergeMessages(conversationId, (current) => {
              const next = [...current];
              const assistantIndex = next.findIndex((item) => item.role === 'assistant' && item.status === 'GENERATING');
              if (assistantIndex === -1) return next;
              next[assistantIndex] = {
                ...next[assistantIndex],
                status: event.status ?? (event.type === 'message-stop' ? 'STOPPED' : 'COMPLETED'),
                requestId: event.requestId ?? next[assistantIndex].requestId,
                citations: finalCitations,
              };
              return next;
            });
            scrollMessagesToBottom();
            setIsSending(false);
            activeRequestIdRef.current = '';
            return;
          }

          if (event.type === 'message-error') {
            setIsSending(false);
            activeRequestIdRef.current = '';
            setPendingCitationsByConversation((current) => ({ ...current, [conversationId]: [] }));
          }
        } catch {
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
    const requestId = globalThis.crypto?.randomUUID?.() ?? `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    activeRequestIdRef.current = requestId;

    setPendingCitationsByConversation((current) => ({ ...current, [conversationKey]: [] }));
    setMessageCitationsByConversation((current) => ({ ...current, [conversationKey]: [] }));
    mergeMessages(conversationKey, (current) => [
      ...current,
      { role: 'user', content, status: 'COMPLETED', requestId },
      { role: 'assistant', content: '', status: 'GENERATING', requestId, citations: [] },
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
        body: JSON.stringify({ conversationId, message: content, requestId, useRag: true, topK: 5 }),
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
    <section className="ai-chat-page">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">AI Chat</span>
          <h2>AI 聊天页</h2>
        </div>
      </header>

      <div className="ai-chat-page__layout">
        <section className="chat-stage">
          <div className="chat-stage__header">
            <div className="chat-stage__title-block">
              <p className="chat-stage__label">当前会话</p>
              <h3 className="chat-stage__title">{activeConversation?.name ?? '新建聊天'}</h3>
            </div>

            <div className="chat-stage__actions" ref={historyWrapRef}>
              <ChatHistoryMenu
                isOpen={isHistoryOpen}
                isLoading={isHistoryLoading}
                items={historyItems}
                activeConversationId={activeConversationId}
                onSelectConversation={handleSelectConversation}
                onCreateChat={handleCreateChat}
                onToggleOpen={handleOpenHistory}
              />
            </div>
          </div>

          <div className="chat-stage__messages" ref={messagesWrapRef}>
            <ChatMessageList messages={activeMessages} onOpenCitation={openPreviewForCitation} renderMarkdownWithCitations={renderMarkdownWithCitations} parseCitations={parseCitations} formatTime={formatTime} />
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
            <div className="chat-stage__composer-actions">
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
          </div>
        </section>

        <RagPreviewPanel
          activeConversationId={activeConversationId}
          isOpen={!!previewOpenByConversation[activeConversationId]}
          citations={messageCitationsByConversation[activeConversationId] ?? []}
          activeCitationId={activeCitationId}
          previewLoading={previewLoadingByConversation[activeConversationId]}
          preview={previewByConversation[activeConversationId]}
          onToggleOpen={togglePreviewOpen}
          onOpenCitation={openPreviewForCitation}
          buildArticleDoc={buildArticleDoc}
          extractTextFromDoc={extractTextFromDoc}
          extractChunkPreview={extractChunkPreview}
        />
      </div>
    </section>
  );
}
