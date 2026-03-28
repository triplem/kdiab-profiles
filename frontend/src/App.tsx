import { useState } from 'react'
import './App.css'
import { ProfileList } from './components/ProfileList'
import { ProfileEditor } from './components/ProfileEditor'
import { ProfileHistory } from './components/ProfileHistory'
import { AdminInsulinManager } from './components/AdminInsulinManager'
import { useQueryClient } from '@tanstack/react-query'
import type { Profile } from './api/generated'
import React from 'react'
import { useAuth } from 'react-oidc-context'
import { useEffect } from 'react'

class ErrorBoundary extends React.Component<{children: React.ReactNode}, {hasError: boolean, error: Error | null}> {
  constructor(props: {children: React.ReactNode}) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '2rem', color: 'red' }}>
          <h2>Something went wrong in rendering.</h2>
          <pre>{this.state.error?.message}</pre>
          <pre>{this.state.error?.stack}</pre>
        </div>
      );
    }
    return this.props.children;
  }
}

function App() {
  console.log("App component mounting started");
  const auth = useAuth();
  console.log("auth loaded: ", auth.isAuthenticated, auth.isLoading, auth.error);
  
  const [view, setView] = useState<'list' | 'create' | 'history' | 'edit' | 'admin'>('list')
  const [selectedProfile, setSelectedProfile] = useState<Profile | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const queryClient = useQueryClient()

  // Sync locale to Date Picker if Keycloak provides it
  const userLocale = auth.user?.profile?.locale || navigator.language;

  useEffect(() => {
    if (userLocale) {
      document.documentElement.lang = userLocale as string;
    }
  }, [userLocale]);

  // Hooks must be called before early returns
  if (auth.isLoading) {
    return <div>Loading authentication...</div>;
  }

  if (auth.error) {
    return <div>Oops... {auth.error.message}</div>;
  }

  if (!auth.isAuthenticated || !auth.user) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <h2>Welcome to T1D Profile Manager</h2>
        <button onClick={() => void auth.signinRedirect()}>Log in</button>
      </div>
    );
  }

  const userId = auth.user?.profile?.sub || 'unknown';
  
  // Extract roles based on common JWT mapping standards (Keycloak realm_access or direct roles)
  // Note: OIDC profile often lacks realm_access. We must decode the access_token.
  const parseJwt = (token: string) => {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=');
      return JSON.parse(atob(padded));
    } catch (e) {
      return {};
    }
  };
  
  const tokenPayload = auth.user?.access_token ? parseJwt(auth.user.access_token) : {};
  
  const realmAccess = tokenPayload.realm_access as { roles?: any } | undefined;
  const resourceAccess = tokenPayload.resource_access as Record<string, { roles?: any }> | undefined;
  
  const safeArray = (val: any): string[] => Array.isArray(val) ? val : (typeof val === 'string' ? [val] : []);
  
  const allRoles = [
    ...safeArray(auth.user?.profile?.roles),
    ...safeArray(tokenPayload.roles),
    ...safeArray(realmAccess?.roles),
    ...(Object.values(resourceAccess || {}).flatMap((client: any) => safeArray(client?.roles)))
  ].map(r => String(r).toUpperCase());

  const isAdmin = allRoles.includes('ADMIN');

  const handleSaved = () => {
    setView('list')
    setSelectedProfile(null)
    queryClient.invalidateQueries({ queryKey: ['profiles', userId] })
  }

  console.log("App component mounting finished, returning JSX");

  return (
    <div className="app-container">
      <div className="app-header">
        <h1>T1D Profile Manager</h1>
      </div>
      
      <div className="user-control" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <span>Logged in as <strong>{auth.user?.profile.preferred_username || auth.user?.profile.name}</strong></span>
          <button style={{ marginLeft: '1rem', fontSize: '0.8rem', padding: '0.2rem 0.5rem' }} onClick={() => void auth.signoutRedirect({ post_logout_redirect_uri: window.location.origin })}>Log out</button>
        </div>

        <div className="persona-badges">
          {/* Mock personas just for visual flair if needed */}
        </div>
      </div>

      <nav className="app-nav">
        <button onClick={() => { setSelectedProfile(null); setView('list'); }} disabled={view === 'list'}>My Profiles</button>
        <button onClick={() => { setSelectedProfile(null); setView('create'); }} disabled={view === 'create'}>Create New Profile</button>
        <button onClick={() => { setSelectedProfile(null); setView('history'); }} disabled={view === 'history'}>Profile History</button>
        {isAdmin && (
          <button onClick={() => { setSelectedProfile(null); setView('admin'); }} disabled={view === 'admin'}>Manage Insulins (Admin)</button>
        )}
      </nav>

      <main>
        {view === 'list' && <ProfileList key={refreshKey} userId={userId} onSelectProfile={(p) => { setSelectedProfile(p); setView('edit'); }} />}
        {view === 'create' && <ProfileEditor key="create" userId={userId} onProfileSaved={handleSaved} />}
        {view === 'edit' && selectedProfile && <ProfileEditor key={`edit-${selectedProfile.id}`} userId={userId} initialProfile={selectedProfile} onProfileSaved={handleSaved} />}
        {view === 'history' && <ProfileHistory userId={userId} onSelectProfile={(p) => { setSelectedProfile(p); setView('edit'); }} />}
        {view === 'admin' && <AdminInsulinManager />}
      </main>
    </div>
  )
}

export default App
