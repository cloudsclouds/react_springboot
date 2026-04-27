import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

interface DocumentSession {
  id: string;
  lastSaved: number;
  connectedClients: Set<string>;
}

@Injectable()
export class CollaborationService implements OnModuleInit, OnModuleDestroy {
  private readonly documentPath = path.resolve(process.cwd(), 'data', 'documents.json');
  private readonly documents = new Map<string, DocumentSession>();
  private saveInterval: NodeJS.Timeout | null = null;
  private readonly SAVE_INTERVAL_MS = 10000; // 每 10 秒检查一次需要保存的文档

  async onModuleInit() {
    // 每隔一段时间检查是否需要保存文档
    this.saveInterval = setInterval(() => {
      this.checkAndPersistDocuments();
    }, this.SAVE_INTERVAL_MS);
  }

  onModuleDestroy() {
    if (this.saveInterval) {
      clearInterval(this.saveInterval);
    }
  }

  /**
   * 注册或获取文档会话
   */
  getOrCreateDocumentSession(documentId: string): DocumentSession {
    let session = this.documents.get(documentId);

    if (!session) {
      session = {
        id: documentId,
        lastSaved: Date.now(),
        connectedClients: new Set(),
      };
      this.documents.set(documentId, session);
    }

    return session;
  }

  /**
   * 注册客户端连接
   */
  registerClient(documentId: string, clientId: string): void {
    const session = this.getOrCreateDocumentSession(documentId);
    session.connectedClients.add(clientId);
  }

  /**
   * 注销客户端连接
   */
  unregisterClient(documentId: string, clientId: string): void {
    const session = this.documents.get(documentId);
    if (session) {
      session.connectedClients.delete(clientId);

      // 如果没有客户端连接了，标记为需要保存
      if (session.connectedClients.size === 0) {
        session.lastSaved = 0; // 强制下次保存
      }
    }
  }

  /**
   * 获取文档的连接状态
   */
  getConnectedClients(documentId: string): number {
    return this.documents.get(documentId)?.connectedClients.size || 0;
  }

  /**
   * 获取所有活跃文档
   */
  getActiveDocuments(): string[] {
    return Array.from(this.documents.keys());
  }

  /**
   * 检查并持久化需要保存的文档
   */
  private async checkAndPersistDocuments(): Promise<void> {
    // 这个方法将由 Hocuspocus 扩展来调用
    // 这里我们记录哪些文档需要被持久化
  }

  /**
   * 标记文档为已修改
   */
  markDocumentModified(documentId: string): void {
    const session = this.getOrCreateDocumentSession(documentId);
    session.lastSaved = 0; // 标记为需要保存
  }
}

