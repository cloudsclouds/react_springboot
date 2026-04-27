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

function WorkspaceLayout({ themeMode, onToggleTheme }) {
  const navigate = useNavigate();
  const userName = useMemo(getStoredUserName, []);

  const handleLogout = () => {
    window.localStorage.removeItem('paperdesk-user');
    navigate('/login', { replace: true });
  };

  return (
    <div className="studio-shell">
      <aside className="studio-sidebar">
        <div className="studio-brand">
          <span className="studio-brand__eyebrow">Phase 1 MVP</span>
          <h1>Paperdesk</h1>
        </div>

        <nav className="studio-nav" aria-label="Workspace navigation">
          <NavLink to="/workspace" end className={({ isActive }) => `studio-nav__item ${isActive ? 'is-active' : ''}`}>
            <strong>工作台</strong>
            <span>概览与近期动态</span>
          </NavLink>
          <NavLink to="/documents" className={({ isActive }) => `studio-nav__item ${isActive ? 'is-active' : ''}`}>
            <strong>文档</strong>
            <span>列表与编辑区布局</span>
          </NavLink>
          <NavLink to="/ai" className={({ isActive }) => `studio-nav__item ${isActive ? 'is-active' : ''}`}>
            <strong>AI 聊天</strong>
            <span>会话、消息与提示入口</span>
          </NavLink>
          <NavLink to="/knowledge-base" className={({ isActive }) => `studio-nav__item ${isActive ? 'is-active' : ''}`}>
            <strong>知识库</strong>
            <span>列表、筛选和详情预览</span>
          </NavLink>
          <NavLink to="/organizations" className={({ isActive }) => `studio-nav__item ${isActive ? 'is-active' : ''}`}>
            <strong>组织</strong>
            <span>组织卡片与成员面板</span>
          </NavLink>
          <NavLink to="/share" className={({ isActive }) => `studio-nav__item ${isActive ? 'is-active' : ''}`}>
            <strong>分享</strong>
            <span>链接设置与状态占位</span>
          </NavLink>
        </nav>
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
            <Route path="/ai" element={<AIChatPage />} />
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