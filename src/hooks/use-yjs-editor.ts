import { useEffect, useMemo, useRef, useState } from 'react';
import { useEditor } from '@tiptap/react';
import * as Y from 'yjs';
import { Collaboration } from '@tiptap/extension-collaboration';
import { CollaborationCursor } from '@tiptap/extension-collaboration-cursor';

// WebSocket 默认地址：本地开发环境会连到 Spring Boot 协作服务。
const WS_URL = process.env.REACT_APP_WS_URL || 'ws://localhost:8080/ws/collaboration';

// Yjs 中用于存放文档内容的 fragment 名称。
// 前后端都需要保持一致，否则协作数据会写入不同的节点。
const DOC_FRAGMENT_NAME = 'default';

/**
 * `useYjsEditor` 是一个把 TipTap + Yjs + WebSocket 协作服务串起来的 React Hook。
 *
 * 它做的事情可以拆成 4 层：
 * 1. 创建并维护一个本地 `Y.Doc`，作为协作文档的真实数据源
 * 2. 通过 WebSocket 与服务端同步文档更新、在线状态和协作光标信息
 * 3. 把 Yjs 文档接入 TipTap 的 `Collaboration` 扩展，让编辑器自动感知远端变更
 * 4. 将连接状态、在线人数、协作者信息暴露给业务层，用于 UI 展示
 *
 * 设计上它是“状态 + 副作用”的组合 Hook：
 * - `useEffect` 负责建立和清理连接
 * - `useEditor` 负责创建 TipTap 编辑器实例
 * - `useRef` 保存跨渲染周期都不应该丢失的对象（Y.Doc / WebSocket）
 */
export function useYjsEditor({
  extensions = [],
  editorProps = {},
  onContentChange,
  documentId,
  userName = `Anonymous-${Math.random().toString(36).slice(2, 9)}`,
  initialContent = { type: 'doc', content: [] },
}: {
  extensions?: any[];
  editorProps?: Record<string, any>;
  onContentChange?: (content: any) => void;
  documentId?: string | number;
  userName?: string;
  initialContent?: any;
} = {}) {
  // `Y.Doc` 需要在多个渲染周期之间保持同一个引用，否则协作状态会被重置。
  const ydocRef = useRef<Y.Doc | null>(null);

  // WebSocket 连接同样必须保留引用，便于在编辑器扩展和清理逻辑中复用。
  const socketRef = useRef<WebSocket | null>(null);

  // 离线期间产生的本地 update 先缓存起来，等网络恢复后统一补发。
  // 这里缓存的是 Yjs 的二进制增量，不是完整文档内容。
  const offlineUpdatesRef = useRef<Uint8Array[]>([]);

  // 每个用户在协作中会分配一个随机颜色，用于光标和在线用户列表展示。
  const userColorRef = useRef(generateRandomColor());

  // 下面三个状态主要服务于 UI：在线人数、连接状态、协作者列表。
  const [connectedClients, setConnectedClients] = useState(0);
  const [isConnected, setIsConnected] = useState(false);
  const [collaborators, setCollaborators] = useState<Record<string, any>>({});

  // `documentId` 可能是数字或字符串，这里统一转成字符串，便于拼接请求参数。
  const roomId = useMemo(() => (documentId ? String(documentId) : ''), [documentId]);

  useEffect(() => {
    // 没有文档 ID 时不启动协作连接，避免空房间造成无意义请求。
    if (!roomId) return;

    // 为当前房间创建一个全新的 Yjs 文档实例。
    // 这里不直接复用旧实例，是为了切换文档时保证状态隔离。
    const ydoc = new Y.Doc();
    ydocRef.current = ydoc;

    // 建立 WebSocket 连接，并把文档 ID / token 作为查询参数传给服务端。
    const socket = new WebSocket(
      `${WS_URL}?docId=${encodeURIComponent(roomId)}&token=${encodeURIComponent(localStorage.getItem('token') || '')}`
    );
    socket.binaryType = 'arraybuffer';
    socketRef.current = socket;

    // 发送 JSON 消息的统一方法，比如 sync、awareness 等协议控制类消息。
    // 这里做了 readyState 检查，避免连接还没建立就误发消息。
    const sendJson = (message: Record<string, any>) => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(message));
      }
    };

    // 发送二进制 Yjs update 的统一方法，用来发 Yjs 协议的核心数据，也就是 update。
    const sendBinary = (buffer: Uint8Array) => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(buffer);
      }
    };

    // snapshot 用于首屏初始化，对应 SQL 数据库中文档表里的 latestSnapshot / snapshot 字段。
    // 前端这里只负责接收、解析并校验 snapshot，为后续文档恢复做初始化准备。
    const applySnapshot = (snapshot: any) => {
      if (!snapshot) return;
      try {
        const content = typeof snapshot === 'string' ? JSON.parse(snapshot) : snapshot;
        if (!content) return;

        // 目前只在文档类型明确时继续处理，避免把无关 payload 误当成文档内容。
        if (content.type === 'doc') {
          ydoc.transact(() => {
            // 触发 fragment 访问，确保协作文档结构被初始化。
            // 实际内容恢复逻辑可能在后端或其他扩展层完成。
            ydoc.getXmlFragment(DOC_FRAGMENT_NAME);
          }, 'snapshot');
        }
      } catch {
        // 容错：快照异常不应阻塞编辑器启动，最多导致首次内容为空。
      }
    };

    // 本地文档一旦产生 update，就把变更同步给服务端。
    // Uint8Array 是二进制字节数组，表示文档的变更增量。
    // `origin === 'remote'` 表示这次变更来自远端同步，避免回环回发。
    // 如果当前没有连上服务端，就先把增量缓存到本地，等重连后再统一恢复。
    const updateHandler = (update: Uint8Array, origin: unknown) => {
      if (origin === 'remote') return;
      if (socket.readyState === WebSocket.OPEN) {
        sendBinary(update);
        return;
      }
      offlineUpdatesRef.current.push(update);
    };

    // 订阅 Yjs 更新事件，确保本地编辑会实时发送到服务端。
    ydoc.on('update', updateHandler);

    // WebSocket 建连后，先同步状态向量，再主动上报一次自己的协作状态。
    // 状态向量可以帮助服务端判断双方需要交换哪些增量更新。
    socket.onopen = () => {
      setIsConnected(true);

      // 重连成功后，先把离线期间缓存的本地增量补回 Y.Doc，再继续走正常同步流程。
      // 这样可以保证用户离线编辑的内容不会丢失。
      if (offlineUpdatesRef.current.length > 0) {
        offlineUpdatesRef.current.forEach((update) => {
          try {
            Y.applyUpdate(ydoc, update, 'local-offline');
          } catch {
            // ignore malformed offline update
          }
        });
        offlineUpdatesRef.current = [];
      }

      sendJson({
        type: 'sync',
        docId: roomId,
        payload: {
          stateVector: uint8ArrayToBase64(Y.encodeStateVector(ydoc)),
        },
      });
      sendJson({
        type: 'awareness',
        docId: roomId,
        payload: {
          userId: ydoc.clientID,
          nickname: userName,
          color: userColorRef.current,
          active: true,
          lastSeenAt: Date.now(),
        },
      });
    };

    // 断开连接时更新 UI 状态。
    socket.onclose = () => {
      setIsConnected(false);
    };

    // 出错时也视为离线，便于业务层给出连接异常提示。
    socket.onerror = () => {
      setIsConnected(false);
    };

    // 统一处理服务端推送消息：
    // - ArrayBuffer：Yjs binary update
    // - JSON 字符串：sync / update / awareness / onlineCount / error
    socket.onmessage = (event) => {
      try {
        // 二进制消息直接交给 Yjs 应用。
        if (event.data instanceof ArrayBuffer) {
          try {
            Y.applyUpdate(ydoc, new Uint8Array(event.data), 'remote');
          } catch {
            // ignore malformed binary update
          }
          return;
        }

        // 文本消息按 JSON 协议解析。
        const data = JSON.parse(event.data);

        // sync 消息通常用于首次拉取快照、增量更新以及在线人数。
        if (data?.type === 'sync') {
          const payload = data.payload || {};
          if (payload.snapshot) {
            applySnapshot(payload.snapshot);
          }
          if (payload.latestSnapshot) {
            applySnapshot(payload.latestSnapshot);
          }
          if (payload.update && typeof payload.update === 'string') {
            try {
              Y.applyUpdate(ydoc, base64ToUint8Array(payload.update), 'remote');
            } catch {
              // ignore malformed update
            }
          }
          if (payload.count != null) {
            setConnectedClients(Number(payload.count) || 0);
          }
          return;
        }

        // update 消息表示服务端广播的某个协作者更新，需要应用到本地文档。
        if (data?.type === 'update' && data.payload?.update) {
          try {
            if (typeof data.payload.update === 'string') {
              Y.applyUpdate(ydoc, base64ToUint8Array(data.payload.update), 'remote');
            }
          } catch {
            // ignore malformed update
          }
          return;
        }

        // awareness 用于同步“谁在线、光标在哪里、颜色是什么”等临时协作状态。
        if (data?.type === 'awareness') {
          const payload = data.payload || {};
          const key = String(payload.userId ?? payload.clientId ?? payload.nickname ?? 'anonymous');
          setCollaborators((current) => ({
            ...current,
            [key]: {
              user: payload.nickname || payload.user || `用户${payload.userId ?? ''}`,
              color: payload.color || '#999999',
              cursor: payload.cursor || null,
              active: payload.active !== false,
              lastSeenAt: payload.lastSeenAt || Date.now(),
            },
          }));
          return;
        }

        // onlineCount 作为轻量统计值，通常用于展示房间人数。
        if (data?.type === 'onlineCount') {
          const count = Number(data.payload?.count);
          if (Number.isFinite(count)) {
            setConnectedClients(count);
          }
          return;
        }

        if (data?.type === 'error') {
          console.warn('Collaboration error:', data.payload?.message || 'unknown error');
        }
      } catch (error) {
        console.warn('WebSocket message parse failed', error);
      }
    };

    // 定时上报在线心跳。
    // 这样即使没有内容编辑，也能让服务端知道当前用户仍然在线。
    const awarenessTimer = window.setInterval(() => {
      sendJson({
        type: 'awareness',
        docId: roomId,
        payload: {
          userId: ydoc.clientID,
          nickname: userName,
          color: userColorRef.current,
          active: true,
          lastSeenAt: Date.now(),
        },
      });
    }, 3000);

    // 清理逻辑非常重要：
    // - 清除心跳定时器
    // - 移除 Yjs 事件订阅
    // - 关闭 WebSocket
    // - 销毁 Y.Doc，释放协作文档占用的内存
    return () => {
      window.clearInterval(awarenessTimer);
      ydoc.off('update', updateHandler);
      socket.close();
      ydoc.destroy();
      socketRef.current = null;
    };
  }, [roomId, userName]);

  // TipTap 编辑器实例。
  // 这里通过 `useEditor` 将协作文档、协作光标、业务扩展和自定义属性统一装配起来。
  const editor = useEditor(
    {
      // SSR / 首屏场景下避免立即渲染，减少环境不一致导致的问题。
      immediatelyRender: false,
      editorProps: {
        ...editorProps,
        attributes: {
          'data-testid': 'editor-collaborative',
          ...(editorProps?.attributes ?? {}),
        },
      },
      extensions: [
        // Collaboration 扩展让 TipTap 直接读写 Y.Doc 的指定 fragment。
        Collaboration.configure({
          document: ydocRef.current || new Y.Doc(),
          field: DOC_FRAGMENT_NAME,
        }),
        // CollaborationCursor 用于显示其他协作者的光标/选区和昵称颜色。
        CollaborationCursor.configure({
          provider: socketRef.current as any,
          user: {
            name: userName,
            color: userColorRef.current,
          },
        }),
        ...extensions,
      ],
      content: initialContent,
      // 编辑器内容变化后，通过回调把 JSON 结构暴露给上层业务。
      onUpdate: ({ editor: nextEditor }) => {
        if (typeof onContentChange === 'function') {
          onContentChange(nextEditor.getJSON());
        }
      },
    },
    // 依赖项变化时，TipTap 会重新评估配置。
    // 注意：这里传入的是 ref.current，而不是 ref 本身。
    [ydocRef.current, socketRef.current, extensions, initialContent]
  );

  return {
    editor,
    ydoc: ydocRef.current,
    provider: socketRef.current,
    isConnected,
    connectedClients,
    collaborators,
  };
}

// 把 Uint8Array 编成 base64，便于通过 JSON 消息传输状态向量或 update。
function uint8ArrayToBase64(buffer: Uint8Array) {
  let binary = '';
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  for (let i = 0; i < bytes.byteLength; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

// 把 base64 字符串还原成 Uint8Array，用于把服务端下发的增量更新交给 Yjs。
function base64ToUint8Array(base64: string) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

// 生成一个协作用户颜色，增强多人编辑时的可视化辨识度。
function generateRandomColor() {
  const colors = [
    '#FF6B6B',
    '#4ECDC4',
    '#45B7D1',
    '#FFA07A',
    '#98D8C8',
    '#F7DC6F',
    '#BB8FCE',
    '#85C1E2',
  ];
  return colors[Math.floor(Math.random() * colors.length)];
}