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
exports.CollaborationService = void 0;
const common_1 = require("@nestjs/common");
const node_path_1 = __importDefault(require("node:path"));
let CollaborationService = class CollaborationService {
    constructor() {
        this.documentPath = node_path_1.default.resolve(process.cwd(), 'data', 'documents.json');
        this.documents = new Map();
        this.saveInterval = null;
        this.SAVE_INTERVAL_MS = 10000;
    }
    async onModuleInit() {
        this.saveInterval = setInterval(() => {
            this.checkAndPersistDocuments();
        }, this.SAVE_INTERVAL_MS);
    }
    onModuleDestroy() {
        if (this.saveInterval) {
            clearInterval(this.saveInterval);
        }
    }
    getOrCreateDocumentSession(documentId) {
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
    registerClient(documentId, clientId) {
        const session = this.getOrCreateDocumentSession(documentId);
        session.connectedClients.add(clientId);
    }
    unregisterClient(documentId, clientId) {
        const session = this.documents.get(documentId);
        if (session) {
            session.connectedClients.delete(clientId);
            if (session.connectedClients.size === 0) {
                session.lastSaved = 0;
            }
        }
    }
    getConnectedClients(documentId) {
        return this.documents.get(documentId)?.connectedClients.size || 0;
    }
    getActiveDocuments() {
        return Array.from(this.documents.keys());
    }
    async checkAndPersistDocuments() {
    }
    markDocumentModified(documentId) {
        const session = this.getOrCreateDocumentSession(documentId);
        session.lastSaved = 0;
    }
};
exports.CollaborationService = CollaborationService;
exports.CollaborationService = CollaborationService = __decorate([
    (0, common_1.Injectable)()
], CollaborationService);
//# sourceMappingURL=collaboration.service.js.map