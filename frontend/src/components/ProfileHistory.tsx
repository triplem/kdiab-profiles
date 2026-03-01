import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Profile } from '../api/generated';
import { startOfDay, endOfDay, subDays, formatISO, parseISO } from 'date-fns';
import { useTimeFormat } from '../context/TimeFormatContext';

interface ProfileHistoryProps {
  userId: string;
}

export const ProfileHistory: React.FC<ProfileHistoryProps> = ({ userId }) => {
  const [history, setHistory] = useState<Profile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { formatTime } = useTimeFormat();
  
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
      try {
        // Parse the input dates to local midnight, then get explicit start/end of day
        // This is much safer than `fromDate.setHours(...)` which can cause off-by-one
        // days if the local timezone puts the raw ISO parse into the previous day.
        const parsedStart = parseISO(startDate);
        const parsedEnd = parseISO(endDate);

        const fromDate = startOfDay(parsedStart);
        const toDate = endOfDay(parsedEnd);

        const response = await api.getProfileHistory(
          userId,
          fromDate.toISOString(),
          toDate.toISOString()
        );
        setHistory(response.data);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch history');
      } finally {
        setLoading(false);
      }
    };

    if (userId && startDate && endDate) {
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
            value={startDate} 
            onChange={(e) => setStartDate(e.target.value)} 
          />
        </div>
        <div>
          <label htmlFor="end-date" style={{ marginRight: '0.5rem' }}>To:</label>
          <input 
            type="date" 
            id="end-date"
            value={endDate} 
            onChange={(e) => setEndDate(e.target.value)} 
          />
        </div>
      </div>

      {loading && <div>Loading history...</div>}
      {error && <div style={{ color: 'red' }}>{error}</div>}

      {!loading && !error && history.length === 0 ? (
        <p>No history found for this period.</p>
      ) : (
        <ul className="history-list">
          {history.map(profile => (
            <li key={profile.id} className="history-item">
              <details>
                <summary>
                  <strong>{profile.name}</strong> - <span className={`status-badge status-${profile.status.toLowerCase()}`}>{profile.status}</span>
                  <span className="date">({profile.createdAt ? new Date(profile.createdAt).toLocaleString() : 'N/A'})</span>
                </summary>
                <div className="history-details">
                  <p>Insulin: {profile.insulinType} • Action: {profile.durationOfAction}m</p>
                  <p style={{ marginTop: '0.5rem', fontWeight: 500 }}>Basal Segments ({profile.basal?.length || 0}):</p>
                  <ul>
                    {profile.basal?.map((b, i) => (
                      <li key={i}>{formatTime(b.startTime)} - {b.value} U/hr</li>
                    ))}
                  </ul>
                  {profile.icr && profile.icr.length > 0 && (
                     <>
                        <p style={{ marginTop: '0.5rem', fontWeight: 500 }}>ICR Segments ({profile.icr.length}):</p>
                        <ul>{profile.icr.map((icr, i) => <li key={i}>{formatTime(icr.startTime)} - {icr.value} g/U</li>)}</ul>
                     </>
                  )}
                  {profile.isf && profile.isf.length > 0 && (
                     <>
                        <p style={{ marginTop: '0.5rem', fontWeight: 500 }}>ISF Segments ({profile.isf.length}):</p>
                        <ul>{profile.isf.map((isf, i) => <li key={i}>{formatTime(isf.startTime)} - {isf.value} mg/dL</li>)}</ul>
                     </>
                  )}
                </div>
              </details>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
