import { useState } from 'react'
import './App.css'
import { ProfileList } from './components/ProfileList'
import { ProfileEditor } from './components/ProfileEditor'
import { ProfileHistory } from './components/ProfileHistory'
import { useTimeFormat } from './context/TimeFormatContext'

function App() {
  const [userId, setUserId] = useState<string>('c9f1d6b0-1234-5678-9abc-def012345678')
  const [view, setView] = useState<'list' | 'create' | 'history'>('list')
  const [refreshKey, setRefreshKey] = useState(0)
  const { is24Hour, setIs24Hour } = useTimeFormat()

  const handleSaved = () => {
    setView('list')
    setRefreshKey(prev => prev + 1)
  }

  return (
    <div className="app-container">
      <div className="app-header">
        <h1>T1D Profile Manager</h1>
      </div>
      
      <div className="user-control">
        <label htmlFor="user-id-input">Simulated User ID</label>
        <input 
          id="user-id-input"
          value={userId} 
          onChange={e => setUserId(e.target.value)} 
          placeholder="Enter UUID..."
        />
        <div style={{ marginLeft: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <label htmlFor="time-format-toggle" style={{ fontSize: '0.9rem' }}>24-hour time</label>
          <input 
            type="checkbox" 
            id="time-format-toggle"
            checked={is24Hour}
            onChange={(e) => setIs24Hour(e.target.checked)}
          />
        </div>
        <div className="persona-badges">
          {/* Mock personas just for visual flair if needed */}
        </div>
      </div>

      <nav className="app-nav">
        <button onClick={() => setView('list')} disabled={view === 'list'}>My Profiles</button>
        <button onClick={() => setView('create')} disabled={view === 'create'}>Create New Profile</button>
        <button onClick={() => setView('history')} disabled={view === 'history'}>Profile History</button>
      </nav>

      <main>
        {view === 'list' && <ProfileList key={refreshKey} userId={userId} />}
        {view === 'create' && <ProfileEditor userId={userId} onProfileSaved={handleSaved} />}
        {view === 'history' && <ProfileHistory userId={userId} />}
      </main>
    </div>
  )
}

export default App
