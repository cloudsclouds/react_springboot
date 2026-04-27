// @ts-nocheck
import { useEffect, useRef, useState } from 'react';
import { useEditor } from '@tiptap/react';
import * as Y from 'yjs';
import { WebsocketProvider } from 'y-websocket';
import { Collaboration } from '@tiptap/extension-collaboration';
import { CollaborationCursor } from '@tiptap/extension-collaboration-cursor';

const WS_URL = process.env.REACT_APP_WS_URL || 'ws://localhost:3002';

/**
 * 自定义 Hook: 支持 Yjs 实时协作的编辑器
 * @param {object} config - 编辑器配置
 * @param {array} config.extensions - TipTap 编辑器扩展
 * @param {object} config.editorProps - 编辑器属性
 * @param {function} config.onContentChange - 内容变化回调
 * @param {string} config.documentId - 文档ID
 * @param {string} config.userName - 用户名
 * @returns {object} 编辑器实例和协作信息
 */
export function useYjsEditor({
  extensions = [],
  editorProps = {},
  onContentChange,
  documentId,
  userName = `Anonymous-${Math.random().toString(36).slice(2, 9)}`,
  initialContent = { type: 'doc', content: [] },
}) {
  const ydocRef = useRef(null);
  const yProviderRef = useRef(null);
  const userColorRef = useRef(generateRandomColor());
  const [connectedClients, setConnectedClients] = useState(0);
  const [isConnected, setIsConnected] = useState(false);
  const [collaborators, setCollaborators] = useState({});

  // 初始化 Yjs 文档和 WebSocket 提供者
  useEffect(() => {
    if (!documentId) return;

    const ydoc = new Y.Doc();
    ydocRef.current = ydoc;

    // 创建 WebSocket 提供者连接到 Hocuspocus 服务器
    const provider = new WebsocketProvider(
      WS_URL,
      documentId, // 文档名称作为 room 名称
      ydoc,
      {
        awareness: true,
        connect: true,
      }
    );

    yProviderRef.current = provider;

    // 处理连接状态变化
    provider.on('status', (event) => {
      setIsConnected(event.status === 'connected');
      console.log(`WebSocket connection status: ${event.status}`);
    });

    // 监听远程客户端的连接/断开连接
    const handleAwarenessChange = (changes) => {
      const clients = {};
      const states = provider.awareness.getStates();
      
      states.forEach((state, clientID) => {
        if (clientID !== provider.awareness.clientID && state.user) {
          clients[clientID] = {
            user: state.user.name || `User-${clientID}`,
            color: state.user.color || '#cccccc',
          };
        }
      });
      setCollaborators(clients);
      setConnectedClients(states.size); // Yjs 的 states 包含所有连接的客户端
    };

    provider.awareness.on('change', handleAwarenessChange);

    // 设置当前用户的awareness信息
    provider.awareness.setLocalState({
      user: {
        name: userName,
        color: userColorRef.current,
      },
    });

    // 初始监听一次
    handleAwarenessChange([]);

    return () => {
      provider.awareness.off('change', handleAwarenessChange);
      provider.destroy();
      ydoc.destroy();
    };
  }, [documentId, userName]);

  // 创建编辑器实例，集成 Yjs 协作扩展
  const editor = useEditor(
    {
      immediatelyRender: false,
      editorProps: {
        attributes: {
          'data-testid': 'editor-collaborative',
          ...editorProps.attributes,
        },
        ...editorProps,
      },
      extensions: [
        Collaboration.configure({
          document: ydocRef.current || new Y.Doc(),
        }),
        CollaborationCursor.configure({
          provider: yProviderRef.current,
          user: {
            name: userName,
            color: userColorRef.current,
          },
        }),
        ...extensions,
      ],
      content: initialContent,
      onUpdate: ({ editor: nextEditor }) => {
        if (typeof onContentChange === 'function') {
          onContentChange(nextEditor.getJSON());
        }
      },
    },
    [ydocRef.current, yProviderRef.current, extensions, initialContent]
  );

  return {
    editor,
    ydoc: ydocRef.current,
    provider: yProviderRef.current,
    isConnected,
    connectedClients,
    collaborators,
  };
}

/**
 * 生成随机颜色
 */
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