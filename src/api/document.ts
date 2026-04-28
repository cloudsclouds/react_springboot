import { getJson, postJson, putJson, deleteJson } from './http';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface CreateDocumentPayload {
  title: string;
}

export interface UpdateDocumentPayload {
  title: string;
}

export interface DocumentData {
  id: number;
  title: string;
  ownerId: number;
  myRole: string;
  updatedAt: string;
}

export interface CreateDocumentResponse {
  documentId: number;
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

export async function deleteDocument(id: number) {
  return deleteJson<ApiResponse<null>>(`/documents/${id}`);
}

export const documentApi = {
  createDocument,
  fetchDocuments,
  fetchDocumentMetadata,
  updateDocumentTitle,
  deleteDocument,
};
