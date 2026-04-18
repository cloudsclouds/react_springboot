import { Injectable, NotFoundException, OnModuleInit } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { CreateDocumentDto } from './dto/create-document.dto';
import { UpdateDocumentDto } from './dto/update-document.dto';
import { DocumentModel } from './document.model';

const FALLBACK_CONTENT = {
  type: 'doc',
  content: [
    {
      type: 'heading',
      attrs: { level: 2 },
      content: [{ type: 'text', text: '在线文档编辑' }],
    },
    {
      type: 'paragraph',
      content: [{ type: 'text', text: '开始输入内容，系统会自动保存。' }],
    },
  ],
};

@Injectable()
export class DocumentsService implements OnModuleInit {
  private readonly filePath = path.resolve(process.cwd(), 'data', 'documents.json');
  private documents: DocumentModel[] = [];

  async onModuleInit() {
    await this.loadDocuments();

    if (this.documents.length === 0) {
      this.documents = [
        {
          id: randomUUID(),
          title: '未命名文档',
          content: FALLBACK_CONTENT,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ];
      await this.persistDocuments();
    }
  }

  listDocuments() {
    return this.documents;
  }

  getDocument(id: string) {
    const document = this.documents.find((item) => item.id === id);

    if (!document) {
      throw new NotFoundException(`Document ${id} not found`);
    }

    return document;
  }

  async createDocument(dto: CreateDocumentDto) {
    const now = new Date().toISOString();
    const document: DocumentModel = {
      id: randomUUID(),
      title: dto.title?.trim() || '未命名文档',
      content: FALLBACK_CONTENT,
      createdAt: now,
      updatedAt: now,
    };

    this.documents.unshift(document);
    await this.persistDocuments();

    return document;
  }

  async updateDocument(id: string, dto: UpdateDocumentDto) {
    const document = this.getDocument(id);

    if (typeof dto.title === 'string') {
      document.title = dto.title.trim() || '未命名文档';
    }

    if (dto.content) {
      document.content = dto.content;
    }

    document.updatedAt = new Date().toISOString();
    await this.persistDocuments();

    return document;
  }

  private async loadDocuments() {
    try {
      const content = await fs.readFile(this.filePath, 'utf-8');
      const parsed = JSON.parse(content);
      if (Array.isArray(parsed)) {
        this.documents = parsed;
      }
    } catch {
      this.documents = [];
    }
  }

  private async persistDocuments() {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    await fs.writeFile(this.filePath, JSON.stringify(this.documents, null, 2), 'utf-8');
  }
}
