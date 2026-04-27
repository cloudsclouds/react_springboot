// @ts-nocheck
const ORGS = [
  { name: '产品团队', members: '12 成员', role: 'Owner' },
  { name: '内容策划', members: '5 成员', role: 'Admin' },
  { name: '外部顾问组', members: '3 成员', role: 'Member' },
];

export default function OrganizationsPage() {
  return (
    <section className="page-shell">
      <header className="page-hero">
        <div>
          <span className="panel-kicker">Organizations</span>
          <h2>组织列表页</h2>
          <p>这里先把组织卡片、成员概览和邀请审批位置定住，后续再接权限动作。</p>
        </div>
      </header>

      <div className="dashboard-grid">
        <section className="panel surface-grid">
          {ORGS.map((org) => (
            <article key={org.name} className="org-card">
              <span className="metric-card__label">{org.role}</span>
              <h3>{org.name}</h3>
              <p>{org.members}</p>
              <button type="button" className="secondary-button">
                查看详情
              </button>
            </article>
          ))}
        </section>

        <section className="panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Queue</span>
              <h3>待处理事项</h3>
            </div>
          </div>

          <div className="detail-list">
            <article>
              <strong>邀请成员</strong>
              <span>预留邮箱邀请与角色选择区。</span>
            </article>
            <article>
              <strong>加入申请</strong>
              <span>预留审批卡片和状态标签。</span>
            </article>
            <article>
              <strong>成员管理</strong>
              <span>预留角色更新、移除和退出组织动作。</span>
            </article>
          </div>
        </section>
      </div>
    </section>
  );
}