export interface DocumentModel {
  id: string;
  title: string;
  content: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}
