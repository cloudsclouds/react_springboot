// @ts-nocheck
import { useEffect, useMemo, useState } from 'react';
import { BrowserRouter, NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import './App.css';
import AIChatPage from './pages/AIChatPage';
import AuthPage from './pages/AuthPage';
import DocumentsPage from './pages/DocumentsPage';
import KnowledgeBasePage from './pages/KnowledgeBasePage';
import OrganizationsPage from './pages/OrganizationsPage';
import SharePage from './pages/SharePage';
import WorkspaceHomePage from './pages/WorkspaceHomePage';

const WORKSPACE_NAV_ITEMS = [
  {
    to: '/workspace',
    label: '工作台',
    shortLabel: '工',
    description: '概览与近期动态',
  },
  {
    to: '/documents',
    label: '文档',
    shortLabel: '文',
    description: '列表与编辑区布局',
  },
  {
    to: '/ai',
    label: 'AI 聊天',
    shortLabel: 'AI',
    description: '会话、消息与提示入口',
  },
  {
    to: '/knowledge-base',
    label: '知识库',
    shortLabel: '知',
    description: '列表、筛选和详情预览',
  },
  {
    to: '/organizations',
    label: '组织',
    shortLabel: '组',
    description: '组织卡片与成员面板',
  },
  {
    to: '/share',
    label: '分享',
    shortLabel: '享',
    description: '链接设置与状态占位',
  },
];

function getInitialTheme() {
  if (typeof window === 'undefined') {
    return 'light';
  }

  const savedTheme = window.localStorage.getItem('blocknote-theme');

  if (savedTheme === 'light' || savedTheme === 'dark') {
    return savedTheme;
  }

  if (typeof window.matchMedia === 'function' && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }

  return 'light';
}

function getStoredUserName() {
  if (typeof window === 'undefined') {
    return '未登录';
  }

  try {
    const storedUser = window.localStorage.getItem('paperdesk-user');
    if (!storedUser) {
      return '未登录';
    }

    const parsedUser = JSON.parse(storedUser);
    return parsedUser.nickname || parsedUser.email || '未登录';
  } catch {
    return '未登录';
  }
}

function getInitialSidebarCollapsed() {
  if (typeof window === 'undefined') {
    return false;
  }

  return window.localStorage.getItem('paperdesk-sidebar-collapsed') === 'true';
}

function WorkspaceLayout({ themeMode, onToggleTheme }) {
  const navigate = useNavigate();
  const userName = useMemo(getStoredUserName, []);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(getInitialSidebarCollapsed);

  useEffect(() => {
    window.localStorage.setItem('paperdesk-sidebar-collapsed', String(isSidebarCollapsed));
  }, [isSidebarCollapsed]);

  const handleLogout = () => {
    window.localStorage.removeItem('paperdesk-user');
    navigate('/login', { replace: true });
  };

  return (
    <div className={`studio-shell ${isSidebarCollapsed ? 'is-sidebar-collapsed' : ''}`}>
      <aside className={`studio-sidebar ${isSidebarCollapsed ? 'is-collapsed' : ''}`}>
        <div className="studio-brand">
          <span className="studio-brand__eyebrow">Phase 1 MVP</span>
          <h1>Paperdesk</h1>
        </div>

        <nav className="studio-nav" aria-label="Workspace navigation">
          {WORKSPACE_NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/workspace'}
              aria-label={item.label}
              className={({ isActive }) => `studio-nav__item ${isActive ? 'is-active' : ''}`}
            >
              <span className="studio-nav__short" aria-hidden="true">
                {item.shortLabel}
              </span>
              <div className="studio-nav__copy">
                <strong>{item.label}</strong>
                <span>{item.description}</span>
              </div>
            </NavLink>
          ))}
        </nav>

        <div className="studio-sidebar__footer">
          <button
            type="button"
            className="secondary-button studio-sidebar__toggle"
            onClick={() => setIsSidebarCollapsed((currentValue) => !currentValue)}
            aria-expanded={!isSidebarCollapsed}
            aria-label={isSidebarCollapsed ? '展开导航' : '折叠导航'}
          >
            <span aria-hidden="true">{isSidebarCollapsed ? '»' : '«'}</span>
            <span>{isSidebarCollapsed ? '展开导航' : '折叠导航'}</span>
          </button>
        </div>
      </aside>

      <div className="studio-main">
        <header className="studio-topbar">
          <div className="studio-topbar__meta">
            <button type="button" className="studio-topbar__user">
              {userName}
            </button>
            <button type="button" className="theme-toggle-button" onClick={onToggleTheme}>
              {themeMode === 'dark' ? '切换到浅色' : '切换到深色'}
            </button>
            <button type="button" className="ghost-link" onClick={handleLogout}>
              退出登录
            </button>
          </div>
        </header>

        <main className="studio-content">
          <Routes>
            <Route path="/workspace" element={<WorkspaceHomePage />} />
            <Route path="/documents" element={<DocumentsPage />} />
            <Route path="/documents/:id" element={<DocumentsPage />} />
            <Route path="/ai" element={<AIChatPage />} />
            <Route path="/ai/:conversationId" element={<AIChatPage />} />
            <Route path="/knowledge-base" element={<KnowledgeBasePage />} />
            <Route path="/organizations" element={<OrganizationsPage />} />
            <Route path="/share" element={<SharePage />} />
            <Route path="*" element={<Navigate to="/workspace" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}

function App() {
  const [themeMode, setThemeMode] = useState(getInitialTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = themeMode;
    document.documentElement.style.colorScheme = themeMode;
    window.localStorage.setItem('blocknote-theme', themeMode);
  }, [themeMode]);

  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/login" element={<AuthPage />} />
        <Route
          path="/*"
          element={
            <WorkspaceLayout
              themeMode={themeMode}
              onToggleTheme={() => setThemeMode((currentMode) => (currentMode === 'dark' ? 'light' : 'dark'))}
            />
          }
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
