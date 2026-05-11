export type ConversationItem = {
  conversationId: number;
  title: string;
  summary?: string;
  lastMessageAt?: string;
  messageCount?: number;
};

export type ChatCitation = {
  citationId: string;
  articleId: number;
  articleTitle: string;
  chunkId: number;
  chunkIndex: number;
  chunkText: string;
  score?: number;
};

export type MessageItem = {
  messageId?: number;
  role: 'user' | 'assistant' | 'system';
  content: string;
  status?: string;
  requestId?: string;
  createdAt?: string;
  updatedAt?: string;
  citations?: ChatCitation[] | string;
};

export type ConversationDetail = {
  conversationId: number;
  title: string;
  summary?: string;
  useRag?: boolean;
  createdAt?: string;
  updatedAt?: string;
  messages?: MessageItem[];
};

export type ArticlePreview = {
  articleId: number;
  title: string;
  summary?: string;
  content?: any;
  updatedAt?: string;
};

export type ChatEvent =
  | { type?: 'rag-start'; conversationId?: number; requestId?: string }
  | { type?: 'rag-result'; conversationId?: number; requestId?: string; citations?: ChatCitation[] }
  | { type?: 'message-start'; conversationId?: number; requestId?: string; citations?: ChatCitation[] }
  | { type?: 'message-delta'; requestId?: string; content?: string }
  | { type?: 'message-end'; conversationId?: number; messageId?: number; status?: string; requestId?: string; citations?: ChatCitation[] }
  | { type?: 'message-stop'; conversationId?: number; messageId?: number; status?: string; requestId?: string; citations?: ChatCitation[] }
  | { type?: 'message-error'; requestId?: string; message?: string };
