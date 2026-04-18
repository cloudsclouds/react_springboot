import { useEffect, useState } from 'react';
import { BrowserRouter, NavLink, Navigate, Route, Routes } from 'react-router-dom';
import './App.css';
import KnowledgeBasePage from './pages/KnowledgeBasePage';
import OnlineEditorPage from './pages/OnlineEditorPage';

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

function App() {
  const [themeMode, setThemeMode] = useState(getInitialTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = themeMode;
    document.documentElement.style.colorScheme = themeMode;
    window.localStorage.setItem('blocknote-theme', themeMode);
  }, [themeMode]);

  return (
    <BrowserRouter>
      <div className="app-router-shell" data-theme={themeMode}>
        <header className="project-header">
          <nav className="project-nav" aria-label="Main navigation">
            <NavLink to="/knowledge-base" className={({ isActive }) => `project-nav__link ${isActive ? 'is-active' : ''}`}>
              知识库
            </NavLink>
            <NavLink to="/online-editor" className={({ isActive }) => `project-nav__link ${isActive ? 'is-active' : ''}`}>
              在线文档编辑
            </NavLink>
          </nav>

          <div className="topbar-actions">
            <span className="mode-tag">{themeMode === 'dark' ? 'Night' : 'Day'}</span>
            <button
              type="button"
              className="theme-toggle"
              onClick={() => setThemeMode(themeMode === 'dark' ? 'light' : 'dark')}
            >
              {themeMode === 'dark' ? '切换到浅色' : '切换到深色'}
            </button>
          </div>
        </header>

        <main className="route-content">
          <Routes>
            <Route path="/" element={<Navigate to="/knowledge-base" replace />} />
            <Route path="/knowledge-base" element={<KnowledgeBasePage />} />
            <Route path="/online-editor" element={<OnlineEditorPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;
