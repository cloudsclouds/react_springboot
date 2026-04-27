// @ts-nocheck
import { useEffect, useState } from 'react';
import { BrowserRouter, NavLink, Navigate, Route, Routes } from 'react-router-dom';
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

function WorkspaceLayout({ themeMode, onToggleTheme }) {
  return (
    <div className="studio-shell">
      <aside className="studio-sidebar">
        <div className="studio-brand">
          <span className="studio-brand__eyebrow">Phase 1 MVP</span>
          <h1>Paperdesk</h1>
          <p>像一张整理好的工作桌，把写作、知识和协作入口放在同一层。</p>
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

        <div className="studio-sidebar__footer">
          <div className="sidebar-note">
            <span className="sidebar-note__label">当前阶段</span>
            <p>先搭骨架，再逐页接业务逻辑和后端数据。</p>
          </div>

          <button type="button" className="theme-toggle-button" onClick={onToggleTheme}>
            {themeMode === 'dark' ? '切换到浅色' : '切换到深色'}
          </button>
        </div>
      </aside>

      <div className="studio-main">
        <header className="studio-topbar">
          <div>
            <span className="panel-kicker">Workbench Layout</span>
            <p className="studio-topbar__copy">第一阶段先验证结构是否顺手，再逐步补交互与数据。</p>
          </div>

          <div className="studio-topbar__meta">
            <span className="status-pill">{themeMode === 'dark' ? 'Night desk' : 'Paper mode'}</span>
            <span className="status-pill">Static layout</span>
            <NavLink to="/login" className="ghost-link">
              登录页
            </NavLink>
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