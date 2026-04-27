"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.createHocuspocusServer = createHocuspocusServer;
const node_fs_1 = require("node:fs");
const node_path_1 = __importDefault(require("node:path"));
const DOCUMENTS_PATH = node_path_1.default.resolve(process.cwd(), 'data', 'documents.json');
let HocuspocusServer;
const getHocuspocusServer = async () => {
    if (!HocuspocusServer) {
        const module = await import('@hocuspocus/server');
        HocuspocusServer = module.Server;
    }
    return HocuspocusServer;
};
async function createHocuspocusServer() {
    const Server = await getHocuspocusServer();
    const server = Server.create({
        port: 3002,
        address: 'localhost',
        debounce: 5000,
        onStoreDocument: async (data) => {
            const { documentName } = data;
            try {
                const allDocuments = await loadDocuments();
                const docIndex = allDocuments.findIndex((doc) => doc.id === documentName);
                if (docIndex >= 0) {
                    allDocuments[docIndex].updatedAt = new Date().toISOString();
                    await node_fs_1.promises.writeFile(DOCUMENTS_PATH, JSON.stringify(allDocuments, null, 2), 'utf-8');
                }
            }
            catch (error) {
                console.error(`Failed to store document ${documentName}:`, error);
            }
        },
        onLoadDocument: async (data) => {
            const { documentName } = data;
            try {
                const allDocuments = await loadDocuments();
                const document = allDocuments.find((doc) => doc.id === documentName);
                if (!document) {
                    console.warn(`Document ${documentName} not found`);
                    return;
                }
                if (data.document && data.document.awareness) {
                    data.document.awareness.setLocalState({
                        documentId: documentName,
                        documentTitle: document.title,
                    });
                }
            }
            catch (error) {
                console.error(`Failed to load document ${documentName}:`, error);
            }
        },
        onChangeDoc: (data) => {
            const { documentName } = data;
            console.log(`Document "${documentName}" changed`);
        },
        onConnect: (data) => {
            const { clientID, documentName } = data;
            console.log(`Client ${clientID} connected to document "${documentName}"`);
        },
        onDisconnect: (data) => {
            const { clientID, documentName } = data;
            console.log(`Client ${clientID} disconnected from document "${documentName}"`);
        },
    });
    return server;
}
async function loadDocuments() {
    try {
        const content = await node_fs_1.promises.readFile(DOCUMENTS_PATH, 'utf-8');
        return JSON.parse(content);
    }
    catch {
        return [];
    }
}
//# sourceMappingURL=hocuspocus.server.js.map