import { getJson, postJson, putJson, patchJson, deleteJson } from './http';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface ApiErrorResponse {
  success?: boolean;
  message?: string;
  data?: unknown;
}

export interface CreateDocumentPayload {
  title: string;
}

export interface UpdateDocumentPayload {
  title?: string;
  latestSnapshot?: string;
}

export interface DocumentData {
  id: number;
  title: string;
  ownerId: number;
  ownerName: string;
  latestSnapshot?: string;
  myRole: string;
  updatedAt: string;
}

export interface CreateDocumentResponse {
  documentId: number;
}

export interface DocumentMemberData {
  userId: number;
  nickname: string;
  role: 'owner' | 'editor' | 'viewer' | 'no_access';
  joinedAt?: string;
  editable?: boolean;
}

export interface UpsertDocumentMemberPayload {
  userId: number;
  role: 'owner' | 'editor' | 'viewer' | 'no_access';
}

export interface CreateShareLinkPayload {
  permission?: 'editor' | 'viewer';
}

export interface ShareLinkData {
  documentId: number;
  shareToken: string;
  shareUrl: string;
  permission: 'editor' | 'viewer';
  expireTime?: string;
}

export async function createDocument(payload: CreateDocumentPayload) {
  return postJson<ApiResponse<CreateDocumentResponse>>('/documents', payload);
}

export async function fetchDocuments() {
  return getJson<ApiResponse<DocumentData[]>>('/documents');
}

export async function fetchDocumentMetadata(id: number) {
  return getJson<ApiResponse<DocumentData>>(`/documents/${id}`);
}

export async function updateDocumentTitle(id: number, payload: UpdateDocumentPayload) {
  return putJson<ApiResponse<null>>(`/documents/${id}`, payload);
}

export async function updateDocumentSnapshot(id: number, payload: UpdateDocumentPayload) {
  return patchJson<ApiResponse<null>>(`/documents/${id}/snapshot`, payload);
}

export async function deleteDocument(id: number) {
  return deleteJson<ApiResponse<null>>(`/documents/${id}`);
}

export async function fetchDocumentMembers(id: number) {
  return getJson<ApiResponse<DocumentMemberData[]>>(`/documents/${id}/members`);
}

export async function upsertDocumentMember(id: number, payload: UpsertDocumentMemberPayload) {
  return postJson<ApiResponse<DocumentMemberData>>(`/documents/${id}/members`, payload);
}

export async function removeDocumentMember(id: number, userId: number) {
  return deleteJson<ApiResponse<null>>(`/documents/${id}/members/${userId}`);
}

export async function createShareLink(id: number, payload: CreateShareLinkPayload = {}) {
  return postJson<ApiResponse<ShareLinkData>>(`/documents/${id}/share-links`, payload);
}

export async function joinByShareLink(id: number, shareToken: string) {
  return postJson<ApiResponse<DocumentMemberData>>(`/documents/${id}/join-by-link`, { shareToken });
}

export const documentApi = {
  createDocument,
  fetchDocuments,
  fetchDocumentMetadata,
  updateDocumentTitle,
  updateDocumentSnapshot,
  deleteDocument,
  fetchDocumentMembers,
  upsertDocumentMember,
  removeDocumentMember,
  createShareLink,
  joinByShareLink,
};
