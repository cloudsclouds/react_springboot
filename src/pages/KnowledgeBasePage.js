import { useEffect, useMemo, useState } from 'react';

const STATIC_DOCUMENTS = [
  {
    id: 'doc-welcome',
    title: '产品概览',
    category: '产品资料',
    updatedAt: '2026-04-18T10:15:00.000Z',
    summary: '项目结构、目标用户和近期里程碑。',
    paragraphs: [
      '该知识库页面作为项目第一板块，当前以静态内容展示团队沉淀信息。',
      '后续可在不改动信息结构的情况下，扩展为多角色阅读入口。',
    ],
  },
  {
    id: 'doc-meeting',
    title: '周会纪要模板',
    category: '会议纪要',
    updatedAt: '2026-04-17T09:20:00.000Z',
    summary: '统一会议纪要结构，保障信息可检索。',
    paragraphs: [
      '建议纪要结构：结论、行动项、负责人、截止时间。',
      '会议结束后 10 分钟内完成更新，避免遗漏决策背景。',
    ],
  },
  {
    id: 'doc-personal',
    title: '个人知识沉淀',
    category: '个人资料',
    updatedAt: '2026-04-16T14:45:00.000Z',
    summary: '沉淀经验、方法和常用参考资料。',
    paragraphs: [
      '建议按主题持续维护，减少散落在聊天工具里的信息碎片。',
      '每周整理一次，把临时记录转为可复用的标准条目。',
    ],
  },
];

function getInitialSidebarExpanded() {
  if (typeof window === 'undefined') {
    return true;
  }

  const savedSidebarState = window.localStorage.getItem('blocknote-sidebar-expanded');

  if (savedSidebarState === 'true') {
    return true;
  }

  if (savedSidebarState === 'false') {
    return false;
  }

  return true;
}

function formatUpdatedAt(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

export default function KnowledgeBasePage() {
  const [sidebarExpanded, setSidebarExpanded] = useState(getInitialSidebarExpanded);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeDocumentId, setActiveDocumentId] = useState(STATIC_DOCUMENTS[0].id);
  const [expandedGroups, setExpandedGroups] = useState(() => ({
    产品资料: true,
    会议纪要: true,
    个人资料: true,
    未分类: true,
  }));

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    window.localStorage.setItem('blocknote-sidebar-expanded', String(sidebarExpanded));
  }, [sidebarExpanded]);

  const documentGroups = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const orderedCategories = ['产品资料', '会议纪要', '个人资料', '未分类'];

    const filteredDocuments = STATIC_DOCUMENTS.filter((document) => {
      if (!query) {
        return true;
      }

      return (
        document.title.toLowerCase().includes(query) ||
        document.category.toLowerCase().includes(query)
      );
    });

    const categorySet = new Set(filteredDocuments.map((document) => document.category || '未分类'));
    const categories = [
      ...orderedCategories.filter((category) => categorySet.has(category)),
      ...Array.from(categorySet).filter((category) => !orderedCategories.includes(category)).sort(),
    ];

    return categories.map((category) => ({
      category,
      documents: filteredDocuments.filter((document) => (document.category || '未分类') === category),
    }));
  }, [searchQuery]);

  const activeDocument =
    STATIC_DOCUMENTS.find((document) => document.id === activeDocumentId) ?? STATIC_DOCUMENTS[0];

  const handleToggleGroup = (groupName) => {
    setExpandedGroups((currentExpandedGroups) => ({
      ...currentExpandedGroups,
      [groupName]: !currentExpandedGroups[groupName],
    }));
  };

  return (
    <section className="workspace-shell">
      <header className="workspace-topbar workspace-topbar--compact">
        <div className="brand-block">
          <span className="brand-kicker">知识库</span>
          <h1>Knowledge Base</h1>
          <p>项目第一板块：静态知识库展示，侧重结构化阅读与检索。</p>
        </div>
      </header>

      <main
        className={`workspace-layout ${sidebarExpanded ? 'workspace-layout--expanded' : 'workspace-layout--collapsed'}`}
      >
        <aside className="knowledge-sidebar">
          <div className="knowledge-sidebar__header">
            <div className="knowledge-sidebar__summary">
              <span className="section-label">内容目录</span>
              <h2>文档树</h2>
              <p>{STATIC_DOCUMENTS.length} documents</p>
            </div>

            <button
              type="button"
              className="sidebar-toggle"
              aria-expanded={sidebarExpanded}
              aria-label={sidebarExpanded ? '收起文档树' : '展开文档树'}
              onClick={() => setSidebarExpanded((expanded) => !expanded)}
            >
              {sidebarExpanded ? '收起' : '展开'}
            </button>
          </div>

          {sidebarExpanded ? (
            <>
              <label className="knowledge-sidebar__search">
                <span>搜索文档</span>
                <input
                  type="search"
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="搜索标题或分类"
                />
              </label>

              <div className="document-list" role="list" aria-label="Knowledge base documents">
                {documentGroups.map((group) => {
                  const isExpanded = searchQuery.trim() ? true : expandedGroups[group.category] !== false;

                  return (
                    <section key={group.category} className="document-group">
                      <button
                        type="button"
                        className="document-group__header"
                        onClick={() => handleToggleGroup(group.category)}
                      >
                        <span>{group.category}</span>
                        <strong>{group.documents.length}</strong>
                        <i aria-hidden="true">{isExpanded ? '▾' : '▸'}</i>
                      </button>

                      {isExpanded ? (
                        <div className="document-group__list">
                          {group.documents.map((document) => {
                            const isActive = document.id === activeDocument.id;

                            return (
                              <article
                                key={document.id}
                                role="listitem"
                                className={`document-item ${isActive ? 'document-item--active' : ''}`}
                              >
                                <button
                                  type="button"
                                  className="document-item__main"
                                  onClick={() => setActiveDocumentId(document.id)}
                                >
                                  <strong>{document.title}</strong>
                                  <span>{new Date(document.updatedAt).toLocaleDateString('zh-CN')}</span>
                                </button>
                              </article>
                            );
                          })}
                        </div>
                      ) : null}
                    </section>
                  );
                })}
              </div>
            </>
          ) : null}
        </aside>

        <section className="workspace-content">
          <article className="editor-panel static-doc-panel" aria-live="polite">
            <div className="editor-panel-head">
              <div>
                <span className="panel-label">静态展示</span>
                <h2 className="static-doc-title">{activeDocument.title}</h2>
                <div className="document-breadcrumb">
                  知识库 / {activeDocument.category} / {activeDocument.title}
                </div>
              </div>

              <div className="editor-meta">
                <span>{formatUpdatedAt(activeDocument.updatedAt)}</span>
                <span>{activeDocument.category}</span>
                <span>只读内容</span>
              </div>
            </div>

            <div className="editor-frame static-document-body">
              <p className="static-doc-summary">{activeDocument.summary}</p>
              {activeDocument.paragraphs.map((paragraph) => (
                <p key={paragraph}>{paragraph}</p>
              ))}
            </div>
          </article>
        </section>
      </main>
    </section>
  );
}
