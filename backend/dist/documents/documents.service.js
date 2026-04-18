"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DocumentsService = void 0;
const common_1 = require("@nestjs/common");
const node_crypto_1 = require("node:crypto");
const node_fs_1 = require("node:fs");
const node_path_1 = __importDefault(require("node:path"));
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
let DocumentsService = class DocumentsService {
    constructor() {
        this.filePath = node_path_1.default.resolve(process.cwd(), 'data', 'documents.json');
        this.documents = [];
    }
    async onModuleInit() {
        await this.loadDocuments();
        if (this.documents.length === 0) {
            this.documents = [
                {
                    id: (0, node_crypto_1.randomUUID)(),
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
    getDocument(id) {
        const document = this.documents.find((item) => item.id === id);
        if (!document) {
            throw new common_1.NotFoundException(`Document ${id} not found`);
        }
        return document;
    }
    async createDocument(dto) {
        const now = new Date().toISOString();
        const document = {
            id: (0, node_crypto_1.randomUUID)(),
            title: dto.title?.trim() || '未命名文档',
            content: FALLBACK_CONTENT,
            createdAt: now,
            updatedAt: now,
        };
        this.documents.unshift(document);
        await this.persistDocuments();
        return document;
    }
    async updateDocument(id, dto) {
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
    async loadDocuments() {
        try {
            const content = await node_fs_1.promises.readFile(this.filePath, 'utf-8');
            const parsed = JSON.parse(content);
            if (Array.isArray(parsed)) {
                this.documents = parsed;
            }
        }
        catch {
            this.documents = [];
        }
    }
    async persistDocuments() {
        await node_fs_1.promises.mkdir(node_path_1.default.dirname(this.filePath), { recursive: true });
        await node_fs_1.promises.writeFile(this.filePath, JSON.stringify(this.documents, null, 2), 'utf-8');
    }
};
exports.DocumentsService = DocumentsService;
exports.DocumentsService = DocumentsService = __decorate([
    (0, common_1.Injectable)()
], DocumentsService);
//# sourceMappingURL=documents.service.js.map