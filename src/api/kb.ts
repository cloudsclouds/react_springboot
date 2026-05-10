import { deleteJson, getJson, postJson, putJson } from './http';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface KnowledgeArticleListItem {
  articleId: number;
  title: string;
  summary: string;
  updatedAt: string;
  status: number;
}

export interface KnowledgeArticleDetail {
  articleId: number;
  title: string;
  summary: string;
  content: any;
  updatedAt: string;
}

export interface KnowledgeArticleVersion {
  versionNo: number;
  source: string;
  createdAt: string;
}

export interface CreateKnowledgeArticlePayload {
  title: string;
  summary?: string;
  content: any;
}

export interface UpdateKnowledgeArticlePayload {
  title: string;
  summary?: string;
  content: any;
  saveSource?: string;
}

export async function fetchKnowledgeArticles() {
  return getJson<ApiResponse<KnowledgeArticleListItem[]>>('/kb/articles');
}

export async function fetchKnowledgeArticle(articleId: number) {
  return getJson<ApiResponse<KnowledgeArticleDetail>>(`/kb/articles/${articleId}`);
}

export async function createKnowledgeArticle(payload: CreateKnowledgeArticlePayload) {
  return postJson<ApiResponse<{ articleId: number; title: string }>>('/kb/articles', payload);
}

export async function updateKnowledgeArticle(articleId: number, payload: UpdateKnowledgeArticlePayload) {
  return putJson<ApiResponse<{ articleId: number; versionNo: number }>>(`/kb/articles/${articleId}`, payload);
}

export async function deleteKnowledgeArticle(articleId: number) {
  return deleteJson<ApiResponse<null>>(`/kb/articles/${articleId}`);
}

export async function fetchKnowledgeArticleVersions(articleId: number) {
  return getJson<ApiResponse<KnowledgeArticleVersion[]>>(`/kb/articles/${articleId}/versions`);
}

export async function rollbackKnowledgeArticle(articleId: number, versionNo: number) {
  return postJson<ApiResponse<{ articleId: number; versionNo: number }>>(`/kb/articles/${articleId}/rollback`, { versionNo });
}
