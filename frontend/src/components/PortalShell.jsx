import Brand from './Brand.jsx';
import AppLink from './AppLink.jsx';
import WorkspaceNavigation from './WorkspaceNavigation.jsx';
import AccountMenu from './AccountMenu.jsx';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import { useSyncExternalStore } from 'react';
import { subscribeSidebar, sidebarSnapshot, setSidebarVisible } from './sidebarPreference.js';
import './portal.css';

export default function PortalShell({ title, children, className = '', contentClassName = '', activePath, titleAs: Title = 'h1' }) {
  const { user } = useAuth();
  const sidebarVisible = useSyncExternalStore(subscribeSidebar, sidebarSnapshot);
  return <div className={`portal-shell ${sidebarVisible ? '' : 'sidebar-collapsed'} ${className}`}>
    <a className="portal-skip" href="#portal-content">Skip to content</a>
    <aside className="portal-sidebar" id="portal-navigation">
      <AppLink className="portal-brand" href="/workspaces" aria-label="SwiftDrop home"><Brand compact={!sidebarVisible} /></AppLink>
      <WorkspaceNavigation user={user} activePath={activePath} collapsed={!sidebarVisible} />
      <p className="portal-sidebar-note">Every delivery, connected.</p>
    </aside>
    <main className="portal-main" id="portal-content" tabIndex={-1}>
      <header className="portal-header">
        <div className="portal-heading">
          <button type="button" className="portal-menu-toggle" onClick={() => setSidebarVisible(!sidebarVisible)}
            aria-expanded={sidebarVisible} aria-controls="portal-navigation"
            aria-label={sidebarVisible ? 'Collapse sidebar' : 'Expand sidebar'} title={sidebarVisible ? 'Collapse sidebar' : 'Expand sidebar'}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="M9 3v18" />
              <path d={sidebarVisible ? 'm16 8-4 4 4 4' : 'm13 8 4 4-4 4'} />
            </svg>
          </button>
          <div><p className="portal-eyebrow">SwiftDrop</p><Title className="portal-title">{title}</Title></div>
        </div>
        <AccountMenu />
      </header>
      <div className={`portal-content ${contentClassName}`}>{children}</div>
    </main>
  </div>;
}
