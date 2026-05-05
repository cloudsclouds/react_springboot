import { useEffect, useRef, useState } from 'react';
import { useEditor } from '@tiptap/react';
import * as Y from 'yjs';
import { Collaboration } from '@tiptap/extension-collaboration';
import { CollaborationCursor } from '@tiptap/extension-collaboration-cursor';

const WS_URL = process.env.REACT_APP_WS_URL || 'ws://localhost:8080/ws/collaboration';

/**
 * 自定义 Hook: 支持 Yjs 实时协作的编辑器
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

    const wsUrl = `${WS_URL}?docId=${encodeURIComponent(String(documentId))}&token=${encodeURIComponent(
      localStorage.getItem('token') || ''
    )}`;
    const socket = new WebSocket(wsUrl);

    socket.onopen = () => {
      setIsConnected(true);
    };

    socket.onclose = () => {
      setIsConnected(false);
    };

    socket.onerror = () => {
      setIsConnected(false);
    };

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data?.type === 'sync') {
          setCollaborators((current) => ({ ...current }));
          if (data.payload?.latestSnapshot && ydoc && !ydocRef.current?.isDestroyed) {
            // 保留文档已打开时的初始内容展示，避免空白页
          }
        }
      } catch (error) {
        console.warn('WebSocket message parse failed', error);
      }
    };

    yProviderRef.current = socket;

    const handleAwarenessChange = () => {
      const clients = {};
      setCollaborators(clients);
      setConnectedClients(1);
    };

    handleAwarenessChange();

    return () => {
      socket.close();
      ydoc.destroy();
    };
  }, [documentId, userName]);

  // 创建编辑器实例，集成 Yjs 协作扩展
  const editor = useEditor(
    {
      immediatelyRender: false,
      editorProps: {
        ...editorProps,
        attributes: {
          'data-testid': 'editor-collaborative',
          ...(editorProps?.attributes ?? {}),
        },
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