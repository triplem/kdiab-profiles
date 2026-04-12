import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Profile } from '../api/generated';
import { startOfDay, endOfDay, subDays, formatISO, parseISO } from 'date-fns';
import { useTimeFormat } from '../context/TimeFormatContext';

interface ProfileHistoryProps {
  userId: string;
  onSelectProfile?: (profile: Profile) => void;
}

const ProfileHistoryItem = ({ profile, formatTime, is24Hour, onSelectProfile }: { profile: Profile, formatTime: (t: string) => string, is24Hour: boolean, onSelectProfile?: (p: Profile) => void }) => {
  const [activeTab, setActiveTab] = useState<'basal' | 'icr' | 'isf'>('basal');

  return (
    <li className="history-item">
      <details>
        <summary>
          <strong>{profile.name}</strong> - <span className={`status-badge status-${(profile.status as string || 'Unknown').toLowerCase()}`}>{profile.status as string || 'Unknown'}</span>
          <span className="date">({profile.createdAt ? new Date(profile.createdAt).toLocaleString(navigator.language, { dateStyle: 'short', timeStyle: 'short', hour12: !is24Hour }) : 'N/A'})</span>
          {onSelectProfile && (
            <button 
              type="button" 
              className="btn outline"
              style={{ padding: '0.1rem 0.4rem', fontSize: '0.8rem', marginLeft: '0.5rem' }}
              onClick={(e) => { e.preventDefault(); onSelectProfile(profile); }}
            >
              Edit
            </button>
          )}
        </summary>
        <div className="history-details">
          <p>Insulin: {profile.insulinType || 'N/A'} • Action: {profile.durationOfAction || 0}m</p>

          <div className="history-tabs" style={{ display: 'flex', gap: '8px', marginTop: '12px', marginBottom: '8px', borderBottom: '1px solid #ccc', paddingBottom: '6px' }}>
            <button 
              type="button" 
              style={{ fontWeight: activeTab === 'basal' ? 'bold' : 'normal', border: 'none', background: 'none', cursor: 'pointer', textDecoration: activeTab === 'basal' ? 'underline' : 'none' }} 
              onClick={() => setActiveTab('basal')}
            >
              Basal ({profile.basal?.length || 0})
            </button>
            <button 
              type="button" 
              style={{ fontWeight: activeTab === 'icr' ? 'bold' : 'normal', border: 'none', background: 'none', cursor: 'pointer', textDecoration: activeTab === 'icr' ? 'underline' : 'none' }} 
              onClick={() => setActiveTab('icr')}
            >
              ICR ({profile.icr?.length || 0})
            </button>
            <button 
              type="button" 
              style={{ fontWeight: activeTab === 'isf' ? 'bold' : 'normal', border: 'none', background: 'none', cursor: 'pointer', textDecoration: activeTab === 'isf' ? 'underline' : 'none' }} 
              onClick={() => setActiveTab('isf')}
            >
              ISF ({profile.isf?.length || 0})
            </button>
          </div>

          <div className="tab-contents" style={{ padding: '4px 0' }}>
            {activeTab === 'basal' && (
              <div>
                {profile.basal && profile.basal.length > 0 ? (
                  <ul>
                    {profile.basal.map((b, i) => (
                      <li key={i}>{formatTime(b?.startTime || '00:00')} - {b?.value} U/hr</li>
                    ))}
                  </ul>
                ) : <p>No Basal segments.</p>}
              </div>
            )}
            {activeTab === 'icr' && (
              <div>
                {profile.icr && profile.icr.length > 0 ? (
                  <ul>
                    {profile.icr.map((icr, i) => (
                      <li key={i}>{formatTime(icr?.startTime || '00:00')} - {icr?.value} g/U</li>
                    ))}
                  </ul>
                ) : <p>No ICR segments.</p>}
              </div>
            )}
            {activeTab === 'isf' && (
              <div>
                {profile.isf && profile.isf.length > 0 ? (
                  <ul>
                    {profile.isf.map((isf, i) => (
                      <li key={i}>{formatTime(isf?.startTime || '00:00')} - {isf?.value} mg/dL</li>
                    ))}
                  </ul>
                ) : <p>No ISF segments.</p>}
              </div>
            )}
          </div>
        </div>
      </details>
    </li>
  );
};

export const ProfileHistory: React.FC<ProfileHistoryProps> = ({ userId, onSelectProfile }) => {
  const [history, setHistory] = useState<Profile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeProfileWarning, setActiveProfileWarning] = useState<string | null>(null);
  const { formatTime, is24Hour, locale } = useTimeFormat();
  
  // Default to last 30 days, properly formatted as 'YYYY-MM-DD'
  const [startDate, setStartDate] = useState(() => {
    return formatISO(subDays(new Date(), 30), { representation: 'date' });
  });
  const [endDate, setEndDate] = useState(() => {
    return formatISO(new Date(), { representation: 'date' });
  });

  useEffect(() => {
    const fetchHistory = async () => {
      setLoading(true);
      setError(null);
      setActiveProfileWarning(null);
      try {
        // Parse the input dates to local midnight, then get explicit start/end of day
        // This is much safer than `fromDate.setHours(...)` which can cause off-by-one
        // days if the local timezone puts the raw ISO parse into the previous day.
        const parsedStart = parseISO(startDate);
        const parsedEnd = parseISO(endDate);

        const fromDate = startOfDay(parsedStart);
        const toDate = endOfDay(parsedEnd);

        const historyRes = await api.getProfileHistory(
          userId,
          fromDate.toISOString(),
          toDate.toISOString()
        );
        
        try {
          const profilesRes = await api.listProfiles(userId);
          // Only prepend the ACTIVE profile — PROPOSED/DRAFT are not part of the historical record
          const activeProfile = profilesRes.data.find(p => (p.status as string) === 'ACTIVE');

          if (activeProfile && !historyRes.data.find(p => p.id === activeProfile.id)) {
            setHistory([activeProfile, ...historyRes.data]);
          } else {
            setHistory(historyRes.data);
          }
        } catch {
          // Fallback if listProfiles fails — show history without active profile prepended
          setHistory(historyRes.data);
          setActiveProfileWarning('Could not load active profile — history may be incomplete.');
        }
      } catch (err) {
        console.error(err);
        setError('Failed to fetch history');
      } finally {
        setLoading(false);
      }
    };

    if (userId && startDate && endDate) {
      if (startDate > endDate) {
        setError('Start date must be before end date');
        setLoading(false);
        return;
      }
      fetchHistory();
    }
  }, [userId, startDate, endDate]);

  return (
    <div className="profile-history">
      <h3>Profile History</h3>
      
      <div className="filters" style={{ marginBottom: '1rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
        <div>
          <label htmlFor="start-date" style={{ marginRight: '0.5rem' }}>From:</label>
          <input
            type="date"
            id="start-date"
            lang={locale}
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="end-date" style={{ marginRight: '0.5rem' }}>To:</label>
          <input
            type="date"
            id="end-date"
            lang={locale}
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
        </div>
      </div>

      {loading && <div>Loading history...</div>}
      {error && <div style={{ color: 'red' }}>{error}</div>}
      {activeProfileWarning && <div style={{ color: 'orange' }}>{activeProfileWarning}</div>}

      {!loading && !error && history.length === 0 ? (
        <p>No history found for this period.</p>
      ) : (
        <ul className="history-list">
          {history.map(profile => (
            <ProfileHistoryItem key={profile.id} profile={profile} formatTime={formatTime} is24Hour={is24Hour} onSelectProfile={onSelectProfile} />
          ))}
        </ul>
      )}
    </div>
  );
};
